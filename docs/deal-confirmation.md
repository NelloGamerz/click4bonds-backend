 # Deal Confirmation Module

How a customer buys a bond: what is implemented today, how the request travels
through the module, and what is deliberately left for later.

---

## 1. What this module does

`POST /api/deal-confirmations` records the deal information for a number of bond
lots sold to the authenticated customer. It does two things, in this order:

1. **Writes the deal** as a `deal_confirmations` row holding a snapshot of what
   was agreed — quantities, the price as it stood, the reference, and every
   figure the confirmation letter prints.
2. **Asks for the confirmation document** — publishes a request to Kafka and
   returns. The PDF step runs on the other side of that topic: a listener draws
   the letter from the values and uploads it to R2. See
   [§7](#7-the-document-step).

**The customer does not wait for the letter.** Drawing the letter and uploading
it is seconds of work, and the request used to block on all of it for an answer
that only needed the deal row to exist. Now the response carries the same figures
the letter will print, so the frontend can show the customer what they bought
immediately, and the document catches up.

**This module does not touch bond inventory.** It does not reserve, deduct,
release or lock units, and it makes no decision based on how many a bond has
left. A deal confirmation states what was agreed; it holds no stock. Inventory
is claimed by the reservation step that precedes a draft deal and consumed once
payment settles — see [§4](#4-inventory-is-not-this-modules-concern).

There is **no approval workflow**. That is why `DealConfirmationStatus` has only
two values and a persisted deal is always `CREATED` first.

### Files

| File | Package | Responsibility |
|---|---|---|
| `DealConfirmationController` | `...DealConfirmation.Controller` | The HTTP endpoint. Reads the buyer from the JWT. |
| `DealConfirmationService` | `...DealConfirmation.Service` | Idempotent retry handling, letter values on the response, then publishes a document request. Not transactional. |
| `DealConfirmationWriter` | `...DealConfirmation.Service` | The transactional half: validate, allocate the reference, persist. Touches no inventory. |
| `DealConfirmationMapper` | `...DealConfirmation.Service` | Entity → response DTO, entity → document snapshot. |
| `DealReferenceGenerator` / `DealReferenceGeneratorImpl` | `...DealConfirmation.Service` | Issues `2026/S/SEP/121`. |
| `DealConfirmationDocumentProducer` | `...DealConfirmation.Producer` | Publishes the document request to Kafka. Async; never fails a purchase. |
| `DealConfirmationDocumentConsumer` | `...DealConfirmation.Consumer` | The listener: renders the letter and records where it landed. |
| `DealConfirmationDocumentKafkaConfig` | `...DealConfirmation.Config` | The topic, the consumer group, and the container factory pinned to the deal payload type. |
| `DealConfirmationDocumentEvent` | `...DealConfirmation.Dto` | The message: the deal id plus the complete snapshot. |
| `DealConfirmationDocumentService` | `...DealConfirmation.Service` | Interface for the PDF step. |
| `AtSplDealConfirmationDocumentService` | `...DealConfirmation.Service` | The real implementation: picks a layout, draws the PDF, stores it. |
| `NoOpDealConfirmationDocumentService` | `...DealConfirmation.Service` | Wired when `document.enabled=false`; produces nothing and says so. |
| `DealConfirmationDocumentConfig` | `...DealConfirmation.Config` | Chooses between the two implementations above. |
| `DealAccrualCalculator` | `...DealConfirmation.Service` | Accrued interest and last coupon date, computed on the managed bond. |
| `DealConfirmationSheetValuesFactory` | `...DealConfirmation.Service` | The letter's arithmetic, shared by every layout. |
| `DealConfirmationPdfService` / `DealConfirmationPdfServiceImpl` | `...DealConfirmation.Service` | Draws the letter, corporate or G-Sec, from the values with OpenPDF. |
| `DealConfirmationSheetStrategy` | `...DealConfirmation.Service` | Interface for one letter layout: which figures it prints and which it requires. |
| `DealConfirmationSheetStrategyFactory` | `...DealConfirmation.Service` | Picks the layout from the bond's rating. The Sovereign rule lives here. |
| `PsuPrivateSaleSheetStrategy` | `...DealConfirmation.Service` | The corporate letter's layout. Every deal unless the bond is a Sovereign. |
| `GsecSellSheetStrategy` | `...DealConfirmation.Service` | The G-Sec letter's layout. Deducts TDS where the corporate one adds stamp duty — see [§7](#which-layout-a-deal-gets). |
| `GsecAccrualCalculator` | `...DealConfirmation.Service` | `COUPDAYBS(valueDate, maturity, 2, 4)`: the G-Sec letter's accrued days, semi-annual on a European 30/360 count. |
| `DealConfirmationDocumentRecorder` | `...DealConfirmation.Service` | Records where the document landed; flips the status. |
| `DealConfirmationDocumentReader` | `...DealConfirmation.Service` | Reads a stored letter back for download. |
| `Modules/Document/*` | `...Modules.Document` | The reusable engine: keys and `DocumentStorage` — the deal-shaped view of storage. |
| `R2DocumentStorage` | `...Document.Service` | The only `DocumentStorage`: keeps letters in the shared object store. |
| `Modules/Storage/*` | `...Modules.Storage` | The shared object store: `ObjectStore` and its Cloudflare R2 implementation. Used by anything that keeps a file, not just deals. |
| `DealConfirmation` | `...DealConfirmation.Model` | The `deal_confirmations` table. |
| `DealReferenceSequence` | `...DealConfirmation.Model` | The `deal_reference_sequences` monthly counter. |
| `DealConfirmationRepository` | `...DealConfirmation.Repository` | Idempotency lookup, `dealReference` lookup. |
| `DealReferenceSequenceRepository` | `...DealConfirmation.Repository` | The atomic counter increment. |
| `CreateDealConfirmationRequest` | `...DealConfirmation.Dto` | Request body. |
| `DealConfirmationResponse` | `...DealConfirmation.Dto` | What the customer gets back. |
| `DealConfirmationDocumentData` | `...DealConfirmation.Dto` | Flat snapshot for the document step. |
| `DealConfirmationDocument` | `...DealConfirmation.Dto` | Format-agnostic generated document. |
| `DealConfirmationStatus` | `...DealConfirmation.Enums` | `CREATED`, `CONFIRMATION_GENERATED`. |

---

## 2. Request flow

```
POST /api/deal-confirmations
  │  body: { isin, quantityPerLot, numberOfLots }
  │  header: Idempotency-Key (optional)
  │  principal: JWT subject → User.id (UUID)
  v
DealConfirmationController.createDealConfirmation          (not transactional)
  │
  v
DealConfirmationService.createDeal                          (not transactional)
  │
  ├─ 1. normalize the Idempotency-Key (blank → null)
  │
  ├─ 2. key != null?  →  SELECT by (customer, key)
  │        found?  ──────────────────────────────────►  return (deal, replayed=true)
  │
  ├─ 3. ────────────────────────────────────────────────┐
  │                                                      │
  │   DealConfirmationWriter.create          @Transactional │
  │     a. normalize ISIN (trim, upper-case)             │
  │     b. totalQuantity = quantityPerLot × numberOfLots │
  │        (Math.multiplyExact — overflow → 400)         │
  │     c. load User, require status == ACTIVE           │
  │     d. load Bond by ISIN            ◄── read only    │
  │     e. validatePurchasable(bond): status == ACTIVE   │
  │        (no inventory is read or written here)        │
  │     f. buildDeal(...) incl. generator.next(today)    │
  │     g. dealConfirmationRepository.save(deal)         │
  │     h. map → response + document snapshot            │
  │                                                      │
  └─ 4. COMMIT ◄─────────────────────────────────────────┘
  │
  ├─ 5. writer threw DataIntegrityViolationException?
  │       (two in-flight requests, same key)
  │       re-read by key → answer with the winner, replayed=true
  │       nothing found → rethrow
  │
  ├─ 6. build the response's letter values   ← after commit, no transaction
  │       DealConfirmationSheetValuesFactory.build(snapshot)
  │       refuses a deal with no price/accrual → letterValues = null,
  │       logged, never propagated
  │
  ├─ 7. publish DealConfirmationDocumentEvent(dealId, snapshot)
  │       to click4bonds.deal-confirmation.document   ← async, non-blocking
  │       switched off with document.enabled=false
  │       failure is logged, never propagated
  │
  v
201 Created  (new deal)   |   200 OK  (replayed request)
```

...and then, on the consumer side, entirely off the request thread:

```
click4bonds.deal-confirmation.document
  │
  v
DealConfirmationDocumentConsumer          @KafkaListener, group
  │                                       click4bonds-deal-documents
  ├─ DealConfirmationDocumentService.generate(snapshot)
  │     render pdf → upload pdf
  │
  ├─ none()?  ── documents switched off, record nothing
  │
  └─ DealConfirmationDocumentRecorder.record(dealId, key)
        status → CONFIRMATION_GENERATED, address + timestamp
        (a short transaction of its own)

  a failure anywhere is logged and dropped, never rethrown — see §7
```

The three rules that shape this split:

- **`DealConfirmationService` is not transactional, `DealConfirmationWriter` is.**
  The document step is an external operation (a PDF render and an upload) and
  holding a database transaction open across it would pin a
  connection for its duration. Splitting them into two beans is also what makes
  `@Transactional` actually apply — a self-invocation inside one class would
  silently bypass Spring's proxy. The publish itself is also deliberately outside
  the transaction: a request published for a deal that then rolls back would
  letter a deal that does not exist.
- **The response is answered before the letter exists.** The figures travel on
  the response rather than being read back later, because the frontend needs
  something to show and there is no read endpoint (§8). The deal's `status` is
  still `CREATED` on the wire — it only becomes `CONFIRMATION_GENERATED` once the
  consumer has stored the letter.
- **The reference and the insert share one transaction.** The monthly counter is
  incremented inside the writer's transaction, so a failed insert returns the
  number and leaves no deal behind ([§6](#6-deal-references)). Nothing on the
  bond needs rolling back, because nothing on the bond changed.

---

## 3. API

### `POST /api/deal-confirmations`

Declared in `DealConfirmationController.java:55-76`.

**Headers**

| Header | Required | Meaning |
|---|---|---|
| `Authorization` | yes | Bearer JWT. `sub` is the customer. |
| `Idempotency-Key` | no | One value per *intended* purchase; the same value on every retry of it. |

**Body** — `CreateDealConfirmationRequest.java:19-40`

```json
{
  "isin": "INE123A07012",
  "quantityPerLot": 10,
  "numberOfLots": 5
}
```

| Field | Validation |
|---|---|
| `isin` | `@NotBlank`, `@Size(max = 12)`. Upper-cased before lookup. |
| `quantityPerLot` | `@NotNull`, `@Positive` |
| `numberOfLots` | `@NotNull`, `@Positive` |

There is no customer field and no total. The buyer is always the JWT subject, and
the server computes `quantityPerLot × numberOfLots` — a client-supplied total
could disagree with the parts it is made of.

**Response** — `DealConfirmationResponse.java:21-48`

```json
{
  "dealConfirmationId": "3f2b…",
  "dealReference": "2026/S/SEP/121",
  "bondId": "…",
  "bondName": "…",
  "isin": "INE123A07012",
  "quantityPerLot": 10,
  "numberOfLots": 5,
  "totalQuantity": 50,
  "pricePerUnit": 1000.0000,
  "totalAmount": 50000.0000,
  "status": "CREATED",
  "createdAt": "2026-09-22T06:08:27Z",
  "letterValues": {
    "letterDate": "2026-09-22",
    "valueDate": "2026-09-22",
    "quantum": 50000.00,
    "principalAmount": 50000.00,
    "accruedInterest": 1189.04,
    "stampDuty": 0.00,
    "totalConsideration": 51189.04,
    "accruedDays": 64,
    "gsecAccruedDays": null,
    "gsecTds": null
  }
}
```

`pricePerUnit` and `totalAmount` are `null` when the bond has no price recorded.
A null price stays null rather than becoming zero, because a zero total would
read as a free purchase (`DealConfirmationWriter.buildDeal`).

**`letterValues` is the same record the spreadsheet is filled from**
(`DealConfirmationSheetValues`), computed by the same factory, so the figure on
screen and the figure on the letter cannot drift. It carries both sheets'
charges — stamp duty for a corporate bond, TDS for a G-Sec — and the frontend
prints whichever belongs to the bond. The abbreviated example above omits the
fields that are null for a corporate deal.

It is **`null`** in two cases, neither of which fails the request:

- the deal cannot support it — no price, or no accrued interest — which is the
  same condition that stops a letter being produced;
- the request was **replayed**. The figures are computed inside the creating
  transaction, from the interest schedule as it stood then; rebuilding them
  outside it against a bond that may since have been edited would risk showing a
  different number from the one the customer saw the first time.

**Status codes**

| Code | When |
|---|---|
| `201 Created` | A new deal was created. |
| `200 OK` | The request was a retry and the original deal is returned; **no second deal was created**. |
| `400` | Validation failed, bond not `ACTIVE` (includes `SOLD_OUT`), or quantity overflow. |
| `403` | The customer's account is not `ACTIVE`. |
| `404` | No bond with that ISIN, or no such customer. |

There is **no `409`**. The module no longer refuses a request for want of
inventory — that decision belongs to the reservation step, and duplicating it
here as a non-atomic check would be worse than not having it.

A replay is `200` and not `201` on purpose: a client that treats `201` as "a new
purchase happened" would be misled (`DealConfirmationController.java:66-70`).

There is **no `GET` endpoint** — a deal can only be created, not read back. See
[§8](#8-known-gaps).

---

## 4. Inventory is not this module's concern

Inventory lives on the bond as `Bond.quantum`.

**The Deal Confirmation module claims stock, but does not own the field.** It
reads it to validate availability and calls the repository's atomic reservation
before the deal row is inserted — this section explains how that boundary works.

### How the reservation fits

Deal creation reserves the units itself, in the same transaction as the insert:

```
customer selects bond → deal confirmation (reserves atomically, then inserts)
                      → payment → settlement → final confirmation
```

The reservation is the conditional UPDATE below, so a request that loses the
race is refused before any deal row exists, and an insert that fails rolls the
reservation back with the transaction.

Two things follow, and both are enforced by tests rather than convention:

- **Validation reads, but does not decide.** `validatePurchasable` checks the
  bond's status and its `quantum`, rejecting a `null` (unconfigured) or
  non-positive quantum and a request larger than what remains
  (`DealConfirmationWriter.validatePurchasable`). This is only a friendlier
  pre-check; the real guard is the atomic reservation below.
- **The status flip follows the reservation.** `markSoldOutIfExhausted` flips
  the bond to `SOLD_OUT` after the conditional UPDATE reports success and the
  bond is reloaded at zero. A bond at zero units with status `ACTIVE` is still
  refused by the conditional UPDATE matching zero rows.

### The oversell guard is preserved

`BondRepository.reserveQuantity` is the actual oversell protection, and
`DealConfirmationWriter.create` calls it before inserting the deal:

```sql
UPDATE bonds
   SET quantum = quantum - :quantity
 WHERE id = :id
   AND quantum >= :quantity
```

The `quantum >= :quantity` predicate is evaluated by PostgreSQL while
it holds the row lock, so two concurrent reservations are serialized by the
database and the loser matches zero rows instead of taking inventory negative. A
read-then-write in Java would let both read the same figure and both proceed —
that is the difference between this and a plain `findById` + `save`, and it is
why the guard must never be "simplified" into that shape. `clearAutomatically =
true` is required because the `UPDATE` bypasses the persistence context, leaving
any in-memory `Bond` stale; a caller that needs the new figure must reload it.

Because the statement is easy to "simplify" into a race,
`BondRepositoryReserveQuantityContractTest` pins its shape: that it
is a single `UPDATE`, that it carries the guard predicate, and that it is the
only `@Modifying` method on the repository.

### Admin surface

`quantum` is settable through the bond API:

| DTO | Field | Semantics |
|---|---|---|
| `CreateBondRequest` | `quantum` (optional) | Initial inventory. Omit → unconfigured. |
| `UpdateBondRequest` | `quantum` (optional) | **Absolute, not a delta.** Restocking sends the new total. |
| `BondDetailResponse` | `quantum` | Read-only view; `null` = unconfigured. |

Status is deliberately left alone by an update: a restocked bond has to be
activated through the activate endpoint, so inventory and status never disagree
silently (`BondService.java:405-420`).

---

## 5. Idempotency

Nothing stops a client from sending the same purchase twice — a double click, a
flaky mobile connection retrying a request it never saw the answer to. The module
handles a retry as two layers.

**Layer 1 — the check** (`DealConfirmationService.java:61-76`). Before writing
anything, an `Idempotency-Key` that already exists for this customer returns the
original deal with `replayed = true`.

The lookup is scoped to the customer
(`DealConfirmationRepository.java:16-29`): two customers may legitimately pick the
same key, and one customer must never be handed another's deal. It fetches `bond`
and `customer` eagerly via `@EntityGraph`, because the replay mapping happens
outside a transaction and must not trigger a lazy load.

**Layer 2 — the constraint** (`DealConfirmation.java:56-67`). A partial-free
unique constraint on `(customer_id, idempotency_key)` is the backstop for two
requests in flight at the same instant, both of which passed Layer 1. One insert
wins; the other gets a `DataIntegrityViolationException`, which
`DealConfirmationService.java:84-106` catches and collapses onto the winner.

The order matters: the writer's transaction has already rolled back, so no second
deal was written and no second reference was issued. Rethrowing instead would
fail the customer's retry for a deal that in fact succeeded.

PostgreSQL does not compare `NULL`s in a unique constraint, so requests that send
no key are unaffected by Layer 2, and `normalizeKey` treats a blank header as "no
key" so a client that always sends the header but sometimes empty does not make
every request look like a retry of the first
(`DealConfirmationService.java:170-179`).

The key is stored on the deal (`DealConfirmation.idempotencyKey`) and is never
returned in `DealConfirmationResponse`.

---

## 6. Deal references

Format: `2026/S/OCT/144` — year, side of the trade (`S` for our sale), month,
and the deal's number within that month.

The sequence comes from the `deal_reference_sequences` table, not a timestamp and
not an in-memory counter: a timestamp can repeat within the same millisecond, and
an in-memory counter restarts at 1 on every deploy. Both would hand two customers
the same reference (`DealReferenceGeneratorImpl.java:13-25`).

The counter is keyed by the first day of the month, because the reference carries
a month and no day. A daily counter would issue `2026/S/SEP/1` on two different
days of September.

The increment is one statement, because a read-then-write in Java lets two
concurrent deals read the same counter (`DealReferenceSequenceRepository.java:15-41`):

```sql
INSERT INTO deal_reference_sequences (sequence_date, last_value)
VALUES (:sequenceDate, 1)
ON CONFLICT (sequence_date)
DO UPDATE SET last_value = deal_reference_sequences.last_value + 1
```

`ON CONFLICT DO UPDATE` takes the row lock (creating the row on the month's first
deal), so a second transaction blocks until the first commits and then increments
on top of it. The value is then read back with `findLastValue` inside the same
transaction — the lock guarantees it is the value this call produced.

**The increment is deliberately inside the caller's transaction.** If deal
creation rolls back, the increment rolls back with it and the number is issued
again to the next deal — no gap, and still unique, because the deal that would
have held it was never written (`DealReferenceGeneratorImpl.java:22-25`).

`DealConfirmation.dealReference` also carries a unique index. That index is what
actually guarantees uniqueness; the generator is what makes it look tidy.

---

## 7. The document step

Implemented. A created deal produces an ATSPL confirmation letter as a PDF
drawn directly from the computed values — no spreadsheet template and no
external converter.

### Pipeline

```
POST /api/deal-confirmations
       |
       v
DealConfirmationDocumentProducer     (KafkaTemplate.send, async)
       |
       v
click4bonds.deal-confirmation.document      <-- the request ends here
       |
       v
DealConfirmationDocumentConsumer     (@KafkaListener, its own group)
       |
       v
DealConfirmationSheetValuesFactory   (snapshot -> the letter's values)
       |
       v
DealConfirmationSheetStrategyFactory (rating -> which layout)
       |
       v
DealConfirmationSheetStrategy        (layout + the figures it requires)
       |
       v
DealConfirmationPdfService           (values -> PDF, drawn in code)
       |
       v
DocumentStorage                      (upload the .pdf)
       |
       v
ObjectStore -> R2ObjectStore          (Cloudflare R2, S3 API)
       |
       v
GET /api/deal-confirmations/{reference}/document
```

### The Kafka hop

`DealConfirmationDocumentEvent` carries **the deal id and the whole snapshot**,
not an id to look up. The document step has always been forbidden from touching
the database — it runs after the deal's transaction has committed — so the
snapshot was already a complete, flat record. That is exactly what a Kafka
payload wants: the consumer needs no repository and no session, and a message
that arrives late still renders the letter the deal was actually struck on. The
id travels beside it because recording the result is a bulk UPDATE keyed on the
primary key, which the customer-facing reference does not give you.

The topic is `click4bonds.deal-confirmation.document`, keyed by the deal id, so
every message about one deal lands on the same partition and in order. It has
**its own consumer group** (`click4bonds-deal-documents`), deliberately not the
analytics group: the two pipelines are scaled and tuned independently.

**The listener needs a container factory of its own, and this is load-bearing.**
Kafka's JSON deserialiser resolves a payload to one fixed class, and the shared
`spring.kafka.consumer` configuration names `AnalyticsEvent`. Without the
override in `DealConfirmationDocumentKafkaConfig`, a document request would be
deserialised as the wrong type and discarded — and the application would still
start and deals would still be created, so the only symptom would be letters that
never appear. That factory is built **inline rather than declared as a bean**:
Boot's own `ConsumerFactory` backs off when another one exists, which would leave
the default `kafkaListenerContainerFactory` — and therefore the analytics
listener — without the factory it was configured against.

### Drawing the PDF

`DealConfirmationPdfService` lays the letter out in code with OpenPDF, from the
same values the old spreadsheet was filled from. There is no workbook to keep and
no LibreOffice process to start, so generation cannot fail for want of a
converter, and the only artefact stored is the PDF the customer receives.

Rendering happens on the Kafka listener thread, so `document.kafka
.consumer-concurrency` is the number of letters drawn at once. The engine half
(`Modules/Document`) still owns storage and keys; the deal-specific half lives in
`Modules/DealConfirmation`. The store itself (`Modules/Storage`) knows nothing
about documents either — it takes keys and bytes, which is what lets a second
feature keep files without building its own arrangement.

### Which layout a deal gets

A G-Sec is not a corporate bond and must not print on the corporate letter — the
G-Sec letter charges **TDS (0.10%)** where the corporate one charges **stamp
duty**, and prints a different field set. So the layout is chosen per deal:

```
bond rating == "Sovereign"  →  GsecSellSheetStrategy      (G-Sec layout)
otherwise                   →  PsuPrivateSaleSheetStrategy (corporate layout)
```

The rule lives in `DealConfirmationSheetStrategyFactory`, in one place. A
strategy knows how to lay a letter out; which letter a deal deserves is a
decision about deals, not about layouts, so it is not a branch inside either
strategy. The match is `rating != null && rating.trim().equalsIgnoreCase(...)`,
because `Bond.rating` is free text (`String(100)`, no enum, no constraint):
`"Sovereign"` and `" sovereign "` match, `"Sovereign GOLD"` and `null` do not.
A null rating deliberately keeps the corporate letter, which is what every deal
got before this rule existed.

The strategies share `DealConfirmationSheetValuesFactory` — two letters must not
become two definitions of accrued interest. A strategy decides *which* figures the
letter prints, never *what* they are.

Both charges are computed in `DealConfirmationSheetValuesFactory`, so neither
letter's arithmetic lives in a layout class. The record carries both: stamp duty
and the corporate total for one letter, TDS and the G-Sec total for the other.
Each letter prints the pair that belongs to it and ignores the other.

**The G-Sec letter deducts where the corporate one adds.** Its money runs
consideration (principal + accrued, no duty) → TDS → total, and the total is
`consideration − TDS`. The corporate letter's is `consideration + stamp duty`.
That is two different instruments' charges, not two ways of writing one sum.

**The G-Sec letter has its own accrued interest, and it is not the corporate
figure.** That letter's rule is `quantum × coupon × days / 360`, with the days from
`COUPDAYBS(valueDate, maturity, 2, 4)` — **semi-annual coupons on a European 30/360
count**. The corporate letter's accrued interest comes from the bond's own schedule
on an actual/actual count. They are different numbers for the same deal and neither
is a rounding of the other, so they travel as separate fields rather than being
derived from one another. `GsecAccrualCalculator` implements the count, pinned
against the old template's own cached result (159 days) rather than a restatement
of it.

**TDS is charged on the accrued interest.** The old template computed `=C28*0.1%`
— 0.10% of the *consideration* — under a label reading "Tds ( 0.10%)". The agreed
rule is **10% of the accrued-interest component**, a different base and a
different rate.

> **Open, and it is on the letter.** The G-Sec letter's TDS label reads "0.10%"
> while the figure printed beside it is 10% of the interest. One of the two is
> wrong and the letter currently shows both. This is a finance question — the
> rate, or the wording, has to change before a G-Sec letter goes to a customer.
> Recorded in `DealConfirmationSheetValues` and pinned by
> `DealConfirmationSheetValuesFactoryTest.chargesGsecTdsOnTheInterestRatherThanTheConsideration`,
> which asserts the figure and names the discrepancy, so it cannot be resolved by
> accident.

**The coupon is printed as a fraction on the G-Sec letter, not a percentage.**
The corporate letter prints the rate as stored (`7.34`), the G-Sec letter prints
`0.0734`. Swapping the conventions is a hundred-fold error.

**The rating travels on the snapshot.** `DealConfirmationDocumentData.rating` is
captured inside the deal's transaction, because the document step runs after it
commits and may not load a `Bond` — the same reason the interest figures travel
(see [§7](#why-the-interest-figures-travel-on-the-snapshot)). It is a snapshot like
everything else there: editing a bond's rating does not re-letter an existing deal.

### The spreadsheet template is gone

The letter used to be produced by filling `ATSPL Deal Format.xlsx` and
converting it with LibreOffice. Both letters are now drawn directly, so the
workbook, `XlsxTemplateWriter`, `PdfConverter`/`LibreOfficePdfConverter`, the
`document.template.*` and `document.pdf.*` settings, and the Apache POI
dependency have all been removed. There is nothing left to fill, convert or
recalibrate.

### Why the interest figures travel on the snapshot

`AccruedInterestService` and `CouponScheduleService` both take a `Bond` **entity**,
and the document step runs after the deal's transaction has committed, where it
may not load one. So `DealAccrualCalculator` runs them inside the transaction, on
the managed bond, and the results are carried on `DealConfirmationDocumentData`.

Recomputing the arithmetic in the document module was rejected: it would create a
second definition of accrued interest that could drift from the one used for
pricing, and the letter and the ledger would disagree.

### Stamp duty (corporate letter only)

The G-Sec letter charges TDS instead of stamp duty, in the opposite direction —
see [§7](#which-layout-a-deal-gets). Everything below is the corporate letter.

Stamp duty is computed, not configured. The rate is `0.0001%` of the
consideration, rounded to the whole rupee — `ROUND(x, 0)` — and the base is the
principal plus accrued interest, *before* duty:

```
subtotal  = principal + accrued
stampDuty = round(subtotal * 0.0001%)   // whole rupees
total     = subtotal + stampDuty
```

The base excludes the duty itself on purpose: charging the duty on the letter's
own total would make it a function of itself.

Because the result is rounded to the rupee, an ordinary retail deal pays nothing
— `1,126.41` at `0.0001%` is `0.0011`, which rounds to `0`. Only crore-scale
deals show a stamp duty at all (`1,02,40,100` → `10.00`). The rate is not
derivable from any template, so it is held as a constant in
`DealConfirmationSheetValuesFactory` rather than in `application.yaml`.

### Selecting the implementation

`DealConfirmationDocumentConfig` declares both candidates as `@Bean` methods.
`document.enabled=false` wires the no-op: without that fallback, disabling
documents would leave no bean at all and the context would fail to start.

`@ConditionalOnMissingBean` is order-dependent against a component-scanned
candidate, which is why neither class carries `@Service`.

The switch is honoured on **both** sides of the topic, which is deliberate. The
producer checks it before publishing, so a machine with documents off does not
fill the topic with requests whose only possible outcome is a refusal; and the
consumer asks the bean either way, so a request that arrives after the setting
was flipped is declined by the no-op rather than failing on a missing
implementation.

### Failure

Generation throws; `DealConfirmationDocumentConsumer` catches and logs, and the
deal stays `CREATED` with no document recorded. A failure must never fail the
purchase — by the time the document step runs the deal is committed, its
inventory is reserved, and the customer has already been answered.

**The listener swallows the exception rather than letting it escape.** A
`@KafkaListener` that throws has its record redelivered, which would retry a deal
that cannot be lettered — a G-Sec layout with no maturity date, say — forever,
blocking its partition. The behaviour is what it was before the move: the failure
is logged, the deal is left unlettered and retryable, and one bad deal does not
hold up every other deal behind it. A bounded retry for transient failures (an R2
outage) is a reasonable future change; it is not what this does today.

**A publish failure is swallowed too, and it is the one gap the move
introduced.** The deal is committed and the customer is answered, so the purchase
must not fail — but the request dies with nothing but a log line, and nothing
will ever re-publish it. Before the move, a failed generation at least left a
`CREATED` deal that the next retry could pick up. See §8.

### Storage and the download endpoint

Documents are uploaded to a private **Cloudflare R2** bucket, reached through its
S3-compatible API by `R2ObjectStore` (`Modules/Storage`). Keys are unchanged:
`yyyy/MM/<dealReference>.<ext>`, relative and deterministic, so regenerating a
deal overwrites rather than accumulating copies.

`R2DocumentStorage` is the only `DocumentStorage` implementation, and it is a
thin adapter: it passes the key straight through and translates the store's
failures into the document module's. Moving documents from the filesystem to R2
changed no row, no key and no caller — because a deal records the key and the
store that resolves it is configuration rather than schema. Recording an address
*instead of* a key did change the schema; that is the next section.

### Keys and addresses

Two different strings name the same object, and the distinction matters:

```
key      2026/09/2026_S_SEP_121.pdf
address  https://<account-id>.r2.cloudflarestorage.com/<bucket>/2026/09/2026_S_SEP_121.pdf
```

The reference's slashes are replaced with underscores in the key by
`DocumentKeys.safeSegment`, so a reference can never introduce a path segment of
its own.

`deal_confirmations.document_r2_path` holds the **address**. It is readable and
pasteable: an operator looking at a row can find the letter without being told how
the application is configured. Three alternatives were rejected — a bare key,
because it means nothing without the configuration; a presigned URL, because it
expires; and a public custom-domain URL, because the bucket is private and every
stored link would be dead.

The cost is real and was accepted deliberately: an address **pins the endpoint
that built it**. A row written against one account names that account for as long
as it exists, so moving accounts, or fronting the bucket with a custom domain,
leaves recorded addresses pointing at a host you no longer use.
`R2ObjectStore.keyFor` refuses to resolve an address whose host or bucket is not
the configured one, so such a move surfaces as a clear failure rather than as a
silent read from the wrong bucket.

The conversion happens at exactly two places, both at the persistence edge:
`DealConfirmationDocumentRecorder` turns the key it is handed into an address
before writing the row, and `DealConfirmationDocumentReader` turns the address
back into a key before asking for bytes. Neither the generator nor the store has
to know the other's vocabulary.

`keyFor` also accepts a **bare relative key**, for rows written before addresses
were recorded — see the migration note below.

### R2 client settings that decide whether anything works

Three settings are load-bearing, and each fails in a way that looks like a
credential problem rather than a configuration one:

- **Path-style addressing** — the client's default puts the bucket in the
  hostname, which R2 does not serve.
- **Chunked encoding off** — the default `aws-chunked` upload produces a 403 that
  reads as a bad access key.
- **Checksums pinned to `WHEN_REQUIRED`** — the AWS SDK adds a CRC32 checksum by
  default from 2.30.0, and R2 rejects the request outright.

`R2ObjectStoreTest` asserts the first two on the configuration object, because a
mocked client cannot show them, and the two are shared between the client and the
presigner so a signed URL cannot be signed for a different addressing style than
the upload used.

Missing configuration is **not** a startup failure. The client is built without
touching the network, so a laptop, CI, or a deployment with `document.enabled=false`
starts with no R2 settings at all; the first write names the property it needs. A
*malformed* endpoint is different and does fail at construction — that is never a
legitimate state, and catching it there beats an SDK `NullPointerException` about
a null scheme. Production makes all four values required placeholders in
`application-prod.yaml`, so the failure lands at bind time.

`GET /api/deal-confirmations/{reference}/document` still streams the letter back
through the application rather than redirecting to a presigned URL. The bucket
stays private, the API is unchanged, and the customer-scoping is enforced by the
query itself — another customer's deal is indistinguishable from one that does
not exist. `ObjectStore` also exposes `presignedGetUrl` for a future caller that
would rather redirect than stream.

### Schema

`document_r2_path` and `document_generated_at` on `deal_confirmations`. The first
was added by `V4__add_deal_confirmation_document.sql` as `document_path`, holding
a key; `V5__document_r2_path.sql` renames it and widens it to 1024 characters.

It was a **rename, not an add-and-drop**. The obvious shape — add the new column,
copy the old into it, drop the old — would have been one lie, because a key is not
an address and SQL cannot build one without the endpoint and bucket, which live in
configuration and must not be hardcoded into a migration. Copying a key into an
address column would leave every historical row holding a value in the wrong
format. A rename is honest about what happened: the contents did not change, their
meaning did.

**Both migrations must be applied by hand** — `application-prod.yaml` sets
`ddl-auto: validate`, so production refuses to start on a schema it does not
recognise. Flyway is on the classpath and enabled for prod with
`baseline-on-migrate: true` and `baseline-version: 4`, so V5 is the first migration
Flyway applies by itself; **V1–V4 are never applied by Flyway** and must already be
present, or be applied by hand on a fresh database.

**Rows written before V5 hold a bare key, not an address.** The application reads
both, so those deals keep working with no action, and every letter generated from
now on records a full address. Making the old rows consistent is optional; V5
carries a commented `UPDATE` for it, with the endpoint and bucket to substitute.

> **Migration note, and it is an operational one.** Letters generated *before*
> documents moved to R2 at all are files on the host under the old
> `document.storage.directory`. Their rows still hold the right key, but nothing
> will resolve them until those files are uploaded to the bucket preserving the
> `yyyy/MM/<ref>.<ext>` layout. Until then the download endpoint returns "no
> document" for those deals. `ObjectStore.put` can be used to copy them, or
> `rclone`/`aws s3 cp` with the R2 endpoint. New letters are unaffected.

---
---

## 8. Known gaps

Things that are true of the code as it stands, worth knowing before building on
it.

**`@PreAuthorize` on the controller is inert.** `DealConfirmationController.java:38`
declares `@PreAuthorize("hasRole('CUSTOMER')")`, but `SecurityConfig` has no
`@EnableMethodSecurity`, so the annotation never takes effect. The request is
still authenticated by the filter chain (`SecurityConfig.java:57-58`, `anyRequest().authenticated()`)
and the writer still requires an `ACTIVE` user, so the practical exposure is
limited — but the role rule is not being enforced. The controller's own javadoc
notes this (`DealConfirmationController.java:29-33`).

**A non-idempotency integrity violation returns the wrong message.**
`GlobalExceptionHandler.handleDataIntegrityViolation` (`GlobalExceptionHandler.java:110-124`)
hardcodes `"A contact request has already been submitted for this email address."`
`DealConfirmationService` rethrows the original exception when the duplicate
lookup finds nothing, so any other constraint violation on this endpoint surfaces
with a message about contact inquiries.

**No read endpoint.** A deal cannot be fetched after creation — there is no `GET`
by reference or by customer, so a customer who loses the `201` response body has
no way to recover their `dealReference` except by retrying with the same
idempotency key. The generated *document* can be downloaded
(`GET /api/deal-confirmations/{reference}/document`), but the deal itself cannot.

**No way to regenerate a document.** A deal whose generation failed stays
`CREATED` with no document, and nothing moves it forward: `@EnableScheduling` is
commented out in `AppApplication`, there is no admin re-run endpoint, and the
Kafka listener consumes each request exactly once. A dead-letter topic or a
retry is a natural home for this now that there is a topic in the path, but
neither exists. Because the storage key is deterministic
(`yyyy/MM/<reference>.<ext>`), a re-run would overwrite rather than accumulate,
so adding one is safe. Until then, a stuck deal needs a manual intervention and
there is no query surface to find stuck deals other than database access —
`document_generated_at` is the column to age against.

**A publish failure loses the document request entirely.** If
`KafkaTemplate.send` cannot reach the broker, the deal is still created and the
customer is still answered — correctly, since the purchase must not fail — but
the request is gone. Nothing retries it and nothing records that it happened
beyond an error log, so the deal stays `CREATED` with no letter and no way to
tell it apart from a generation that failed later. This is the one gap the move
off the request thread introduced; before it, a failed generation was at least
attached to a deal the client could retry.

**Documents are not transactional with the deal.** Publishing after the commit
is deliberate — a request published for a deal that then rolled back would letter
a deal that does not exist — but the two are not atomic, so a crash between the
commit and the send leaves a deal with no request. The alternative, publishing
inside the transaction, trades a rare silent gap for a guaranteed wrong letter,
which is the worse of the two.

**No analytics event.** Deal creation emits no `AnalyticsService.track(...)` call,
unlike `BondService` and `OrderService` (see `docs/analytics.md`), so purchases do
not appear in the ClickHouse pipeline.

**No cancellation or refund path.** There is no endpoint to void a deal. This is
now a smaller gap than it was: since creating a deal moves no inventory, voiding
one no longer has units to give back, and a deal created in error leaves the
bond's stock exactly as it was. Releasing reserved units is the reservation
step's concern, and that step is not implemented yet.

**Nothing claims inventory for a deal.** With the reservation step not yet
built, `POST /api/deal-confirmations` records the deal without any units being
reserved anywhere. Until that step exists, a deal confirmation is a statement of
intent with no stock behind it — which is the correct state for this stage, but
it does mean the oversell guard is currently dormant rather than unused.

**Schema is applied by hand in production.** `application-dev.yaml:169` sets
`ddl-auto: update`, so a dev database is built by Hibernate.
`application-prod.yaml:29` sets `ddl-auto: validate` — production refuses to
start on a schema it does not recognise.

Flyway is now on the classpath (`flyway-core` and `flyway-database-postgresql` in
`pom.xml`) and `application-prod.yaml` enables it with `baseline-on-migrate: true`
and `baseline-version: 4`. That baseline is the important part: a database with no
Flyway history table is stamped as already at V4, so **V1–V4 are never applied by
Flyway** and must already be present — or be applied by hand on a fresh database.
Migrations numbered above V4 do run automatically. So
`V4__add_deal_confirmation_document.sql` remains a script someone has to run on an
existing schema, but anything added after it does not.

---

## 9. Tests

All under `src/test/java/com/click4bonds/app/Modules/DealConfirmation/`.

| Test class | Covers |
|---|---|
| `CreateDealConfirmationRequestTest` | Every validation rule, including all broken fields reported at once (13 cases). |
| `DealConfirmationWriterTest` | Inventory-neutrality (each success ends with `verifyNoMoreInteractions(bondRepository)`), no status flip at zero units, deals created with `null` or insufficient inventory, ISIN normalisation, overflow, null price, each rejection path, write failure propagating (13 cases). |
| `DealConfirmationServiceTest` | Idempotency: no key, blank key, retry replays, a replay asks for no second document and carries no letter values, key scoped per customer, concurrent duplicate collapsed, constraint violation rethrown when no deal matches. Plus the hand-off: the request publishes a document request carrying the deal id and the full snapshot, nothing is published when documents are switched off, a publish failure does not fail the purchase, the response carries the letter values, and a deal that cannot support them answers without them rather than failing (14 cases). |
| `DealConfirmationDocumentProducerTest` | The topic is the one the consumer listens on, each request is keyed by its own deal id, and the topic name is pinned so a rename cannot silently strand every document request (3 cases). |
| `DealConfirmationDocumentConsumerTest` | The render-and-record path, `none()` recording nothing, a generation failure swallowed rather than redelivered, a recording failure swallowed, a request with no snapshot discarded, the listener naming the right topic and **the right container factory** — the default one deserialises the analytics payload and would drop the message — and that the consumer holds no `KafkaTemplate` (7 cases). |
| `DealConfirmationDocumentKafkaConfigTest` | The topic's name and partition count, its own consumer group, that it is not the analytics topic, that the deserialiser is pinned to the deal payload type with type headers off and the group overridden, that broker settings still come from the shared configuration, and that building the factory touches no broker (6 cases). |
| `DealConfirmationConcurrencyTest` | Eight concurrent requests against a bond with 1000 units, each asking for 500 — all succeed, inventory is bit-for-bit unchanged, and the repository's working reservation stand-in is never reached. |
| `DealReferenceGeneratorImplTest` | Format, zero-padding, reads back the counter it just incremented, fails loudly when unreadable. |
| `BondRepositoryReserveQuantityContractTest` | *(under `Modules/Bond/Repository/`)* That `reserveQuantity` still exists, is `@Modifying`, binds both parameters, remains a single `UPDATE` carrying the `>= :quantity` guard with no `SELECT`, and is the repository's only mutating statement. |

The first three are the ones that would catch a reintroduced inventory mutation:
the writer tests fail on any extra `bondRepository` interaction, and the
concurrency test would see the inventory move because its stand-in reservation
actually works. `BondRepositoryReserveQuantityContractTest` is separate because
it protects a statement this module no longer calls — see
[§4](#the-oversell-guard-is-preserved). Note that it is reflective rather than
executed against a database: the project has no embedded database or
Testcontainers.

The document step adds these, under `Modules/DealConfirmation/` unless noted:

| Test class | Covers |
|---|---|
| `DealConfirmationSheetValuesFactoryTest` | The letter's arithmetic: the coupon printed as stored rather than divided by 100, quantum from face value, accrued interest scaled from one bond to the position, the total, stamp duty, 2dp rounding, refusing a deal with no price or no accrual — and the G-Sec side, which is a **second calculation rather than a reuse of the first**: its own day count, interest from quantum × coupon × days / 360, the consideration before its charge, TDS deducted and **charged on the interest rather than the consideration**, and all five figures null when the deal has no maturity or no coupon (24 cases). |
| `GsecAccrualCalculatorTest` | The day count, pinned against the old template's cached 159: measuring from the previous coupon date rather than maturity, stepping back when settlement falls earlier in the same month as the coupon, the 31st counting as the 30th, thirty-day months rather than calendar ones, and not accruing on or after maturity (9 cases). |
| `DealConfirmationSheetStrategyFactoryTest` | The routing rule: `"Sovereign"` in any case or surrounding whitespace gets the G-Sec layout; `null`, blank, `"AAA"` and `"Sovereign GOLD"` keep the corporate one; the predicate accepts only the whole trimmed word; and no route returns null or an unnamed layout (7 cases). |
| `DealConfirmationPdfServiceImplTest` | The renderer draws a non-empty PDF for both layouts from the values. |
| `AtSplDealConfirmationDocumentServiceTest` | The letter drawn and stored as a PDF under one stem, refusal before anything is written (no reference, values that cannot be built, a Sovereign deal missing its G-Sec figures), nothing stored when the render fails, and that a Sovereign deal is drawn on the **G-Sec** layout while an unrated one keeps the corporate layout (11 cases). |
| `DealConfirmationDocumentRecorderTest` | Status and address recorded together, and the address recorded is the one derived from the key rather than the key itself — a row holding a key would be unreadable to anyone but this application. A deal that is no longer there is warned about, not thrown. And a missing deal id does not demand that storage be configured. |
| `DealConfirmationDocumentReaderTest` | That the row's address is resolved back to a key before the store is asked for bytes, the PDF-versus-legacy-spreadsheet choice made from the address, that another customer's deal and a deal with no document are both reported as missing without consulting storage at all, and that an address which cannot be resolved surfaces as a failure rather than as "no document" (8 cases). |
| `DealConfirmationDocumentConfigTest` | *(under `Modules/DealConfirmation/Config/`)* That `document.enabled` really swaps the implementation, and never wires both. |
| `R2DocumentStorageTest` | *(under `Modules/Document/Service/`)* That the key a deal records is the key the object is stored under — divergence there would write objects nothing could find — that keys and addresses each convert to the other, and that a store failure arrives as a `DocumentStorageException`, the type `DealConfirmationService` knows how to treat as "the letter could not be kept" (11 cases). |
| `R2ObjectStoreTest` | *(under `Modules/Storage/Service/`)* The two settings whose absence silently breaks R2 — path-style addressing and chunked encoding off — asserted on the configuration object because a mocked client cannot show them; that the store constructs with no configuration at all; that a missing bucket or endpoint is reported by property name; key rejection; the bucket/key/content-type mapping; a 404 as absent but a 500 as a failure; address round-tripping, including a bare legacy key and refusal of an address from another endpoint or bucket; and a real round-trip against a live bucket, skipped where none is configured (24 cases, 1 skipped in CI). |
| `StoragePropertiesTest` | *(under `Modules/Storage/Config/`)* That the property names written in the YAML actually bind — Spring ignores keys no field claims, so a mistyped `secret-key` would silently fall back to the SDK's credential chain and surface much later as an authentication error. |

`R2ObjectStoreTest` cannot prove the client works against R2 without a bucket —
a mocked client accepts anything. That is what the `assumeTrue`-gated round-trip
is for: it runs on a machine with R2 configured and is skipped everywhere else.
The checksum and chunked-encoding settings are the reason it exists.
