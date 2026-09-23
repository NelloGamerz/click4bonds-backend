 # Deal Confirmation Module

How a customer buys a bond: what is implemented today, how the request travels
through the module, and what is deliberately left for later.

---

## 1. What this module does

`POST /api/deal-confirmations` records the deal information for a number of bond
lots sold to the authenticated customer. It does two things, in this order:

1. **Writes the deal** as a `deal_confirmations` row holding a snapshot of what
   was agreed — quantities, the price as it stood, the reference.
2. **Generates the confirmation document** — the Excel/PDF step. *Not
   implemented yet*; see [§7](#7-not-implemented-yet).

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
| `DealConfirmationService` | `...DealConfirmation.Service` | Idempotent retry handling, then hands off. Not transactional. |
| `DealConfirmationWriter` | `...DealConfirmation.Service` | The transactional half: validate, allocate the reference, persist. Touches no inventory. |
| `DealConfirmationMapper` | `...DealConfirmation.Service` | Entity → response DTO, entity → document snapshot. |
| `DealReferenceGenerator` / `DealReferenceGeneratorImpl` | `...DealConfirmation.Service` | Issues `DC-YYYYMMDD-000001`. |
| `DealConfirmationDocumentService` | `...DealConfirmation.Service` | Interface for the Excel/PDF step. |
| `NoOpDealConfirmationDocumentService` | `...DealConfirmation.Service` | Placeholder that produces nothing. |
| `DealConfirmation` | `...DealConfirmation.Model` | The `deal_confirmations` table. |
| `DealReferenceSequence` | `...DealConfirmation.Model` | The `deal_reference_sequences` daily counter. |
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
  │  principal: JWT subject → clerkUserId
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
  ├─ 6. generateDocument(snapshot)     ← after commit, no transaction
  │       failure is logged, never propagated
  │
  v
201 Created  (new deal)   |   200 OK  (replayed request)
```

The two rules that shape this split:

- **`DealConfirmationService` is not transactional, `DealConfirmationWriter` is.**
  The document step is an external operation (eventually a spreadsheet fill and a
  PDF render) and holding a database transaction open across it would pin a
  connection for its duration. Splitting them into two beans is also what makes
  `@Transactional` actually apply — a self-invocation inside one class would
  silently bypass Spring's proxy.
- **The reference and the insert share one transaction.** The daily counter is
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
  "dealReference": "DC-20260922-000001",
  "bondId": "…",
  "bondName": "…",
  "isin": "INE123A07012",
  "quantityPerLot": 10,
  "numberOfLots": 5,
  "totalQuantity": 50,
  "pricePerUnit": 1000.0000,
  "totalAmount": 50000.0000,
  "status": "CREATED",
  "createdAt": "2026-09-22T06:08:27Z"
}
```

`pricePerUnit` and `totalAmount` are `null` when the bond has no price recorded.
A null price stays null rather than becoming zero, because a zero total would
read as a free purchase (`DealConfirmationWriter.buildDeal`).

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

Inventory lives on the bond as `Bond.remainingQuantity` (`Bond.java:339`).

**Nothing in the Deal Confirmation module reads or writes it.** If you are
looking for where units are claimed, it is not here — this section exists so the
absence is deliberate rather than a search that ends in confusion.

### Why it was removed

Deal creation used to reserve the units itself, in the same transaction as the
insert. That made a deal confirmation a claim on stock, which is the wrong
ownership for the flow Click4Bonds is moving to:

```
customer selects bond → reservation (reserves atomically) → draft deal confirmation
                      → payment → settlement → final confirmation
```

Under that split the reservation happens *before* a draft deal exists, and the
units are consumed once payment settles. A deal confirmation that also reserved
would reserve twice, and would have to release when a payment failed — a
lifecycle this module has no business running.

Two things follow, and both are enforced by tests rather than convention:

- **No inventory validation.** `validatePurchasable` checks the bond's *status*
  only (`DealConfirmationWriter.validatePurchasable`). It does not check
  `remainingQuantity`, not even to give a friendlier message: a check here would
  be a non-atomic stand-in for the real guard, and the units are not this
  module's to promise or refuse. A `null` remaining quantity is no longer a
  reason to reject a request, because availability is not decided here.
- **No status flip.** Taking the last unit used to flip the bond to `SOLD_OUT`
  as a side effect. Nothing flips it automatically now; the status that gates
  selling is set through the admin API. A bond at zero units with status
  `ACTIVE` is therefore possible, and is refused by whatever claims the last
  units — the conditional UPDATE matches zero rows.

### The oversell guard is preserved

`BondRepository.reserveQuantity` (`BondRepository.java:86-126`) is kept
untouched, with no caller in production code. It is the actual oversell
protection and the reservation step will call it:

```sql
UPDATE bonds
   SET remaining_quantity = remaining_quantity - :quantity
 WHERE id = :id
   AND remaining_quantity >= :quantity
```

The `remaining_quantity >= :quantity` predicate is evaluated by PostgreSQL while
it holds the row lock, so two concurrent reservations are serialized by the
database and the loser matches zero rows instead of taking inventory negative. A
read-then-write in Java would let both read the same figure and both proceed —
that is the difference between this and a plain `findById` + `save`, and it is
why the guard must never be "simplified" into that shape. `clearAutomatically =
true` is required because the `UPDATE` bypasses the persistence context, leaving
any in-memory `Bond` stale; a caller that needs the new figure must reload it.

A method with no callers is exactly the kind that gets cleaned up, so
`BondRepositoryReserveQuantityContractTest` pins the statement's shape: that it
is a single `UPDATE`, that it carries the guard predicate, and that it is the
only `@Modifying` method on the repository.

### Admin surface

`remainingQuantity` is settable through the bond API:

| DTO | Field | Semantics |
|---|---|---|
| `CreateBondRequest` | `remainingQuantity` (`@Min(0)`, optional) | Initial inventory. Omit → unconfigured. |
| `UpdateBondRequest` | `remainingQuantity` (`@Min(0)`, optional) | **Absolute, not a delta.** Restocking sends the new total. |
| `BondResponse` | `remainingQuantity` | Read-only view; `null` = unconfigured. |

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

Format: `DC-YYYYMMDD-000001`. The zero-padded sequence means references sort in
issue order as text.

The sequence comes from the `deal_reference_sequences` table, not a timestamp and
not an in-memory counter: a timestamp can repeat within the same millisecond, and
an in-memory counter restarts at 1 on every deploy. Both would hand two customers
the same reference (`DealReferenceGeneratorImpl.java:13-25`).

The increment is one statement, because a read-then-write in Java lets two
concurrent deals read the same counter (`DealReferenceSequenceRepository.java:15-41`):

```sql
INSERT INTO deal_reference_sequences (sequence_date, last_value)
VALUES (:sequenceDate, 1)
ON CONFLICT (sequence_date)
DO UPDATE SET last_value = deal_reference_sequences.last_value + 1
```

`ON CONFLICT DO UPDATE` takes the row lock (creating the row on the day's first
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

## 7. Not implemented yet

The document step is a wired-up seam with no implementation behind it. This is
intentional, and it is the main outstanding work in the module.

### What exists

| Piece | State |
|---|---|
| The call site (`DealConfirmationService.generateDocument`) | Real, runs after commit, swallows failures |
| `DealConfirmationDocumentService` interface | Real |
| `DealConfirmationDocumentData` snapshot | Real, fully assembled |
| `NoOpDealConfirmationDocumentService` | Placeholder — logs and returns `none()` |
| `src/main/resources/deal_confirmation/Deal Format.xlsx` | In the repo, **not read** |
| Apache POI dependency | **Not present** in `pom.xml` |

### The planned pipeline

`DealConfirmationDocumentService.java:9-32` documents the intended shape:

```
DealConfirmation
      |
      v
ExcelTemplateService   (fills src/main/resources/deal_confirmation/Deal Format.xlsx)
      |
      v
Excel to PDF
      |
      v
DocumentStorage        (keeps the bytes somewhere durable)
      |
      v
download / email
```

### Why it is a seam and not a stub

`DealConfirmationDocumentData` is a flat record holding everything the document
needs — reference, date, customer name and email, bond name, ISIN, security type,
coupon rate, maturity date, the quantities, the money, the status
(`DealConfirmationDocumentData.java:24-58`). It is assembled **inside** the deal's
transaction, because building it reads `customer` and `bond`, which are both
lazily loaded. The document step, which runs after that transaction has
committed, therefore never touches an entity, a repository or a session.

`DealConfirmationDocument` is deliberately format-agnostic — it names no file
format, library or storage location (`DealConfirmationDocument.java:3-9`). So
implementing the document step is a one-line bean swap: replace
`NoOpDealConfirmationDocumentService` with a real implementation. Nothing in deal
creation or validation changes.

### Two rules a real implementation must follow

From the interface contract (`DealConfirmationDocumentService.java:34-42`):

- **Do not read the database or touch lazy associations.** You run after commit,
  with the snapshot you were given.
- **A failure must not fail the deal.** Throw, and the caller logs it and leaves
  the deal in `CREATED` for a later retry. The deal is already committed —
  failing the request now would make the client believe it did not happen.

### The TODO at the call site

`DealConfirmationService.java:139-145` records the three steps that belong to the
document implementation, not to the deal flow:

1. Store the document and keep its location on the deal.
2. Move the deal to `DealConfirmationStatus.CONFIRMATION_GENERATED`.
3. Notify the customer.

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
by reference or by customer. `DealConfirmationRepository.findByDealReference`
exists but is unused. A customer who loses the `201` response body has no way to
recover their `dealReference` except by retrying with the same idempotency key.

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

**Schema is applied by Hibernate, not migrations.** `application-dev.yaml:167-169`
sets `ddl-auto: update`. `bonds.remaining_quantity` and the two new tables
(`deal_confirmations`, `deal_reference_sequences`) exist because Hibernate creates
them; there are no `db/` migration scripts in the project. A schema change that
Hibernate cannot express as an additive update would need manual handling.

---

## 9. Tests

All under `src/test/java/com/click4bonds/app/Modules/DealConfirmation/`.

| Test class | Covers |
|---|---|
| `CreateDealConfirmationRequestTest` | Every validation rule, including all broken fields reported at once (13 cases). |
| `DealConfirmationWriterTest` | Inventory-neutrality (each success ends with `verifyNoMoreInteractions(bondRepository)`), no status flip at zero units, deals created with `null` or insufficient inventory, ISIN normalisation, overflow, null price, each rejection path, write failure propagating (13 cases). |
| `DealConfirmationServiceTest` | Idempotency: no key, blank key, retry replays, replay does not regenerate the document, key scoped per customer, concurrent duplicate collapsed, constraint violation rethrown when no deal matches, document failure does not fail the purchase (9 cases). |
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
