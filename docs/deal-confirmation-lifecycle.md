# Deal Lifecycle — Create, Confirm, Cancel

Back-office deals now move through a tracked lifecycle. An employee with
`DEAL_CREATION` strikes a deal; a (usually different) employee with
`DEAL_CONFIRMATION` signs it off. Confirming freezes the deal and only then asks
for its confirmation letter. Cancelling returns the reserved units to the bond.

Base URL examples use `https://api.example.com`; substitute the environment's API
host. All calls need `Authorization: Bearer <accessToken>`.

---

## 1. Statuses

| Status | Meaning |
|---|---|
| `CREATED` | Deal saved and its units reserved. Editable and cancellable. |
| `CONFIRMED` | Signed off by an employee with `DEAL_CONFIRMATION`. Frozen; letter requested. |
| `CONFIRMATION_GENERATED` | The letter has been rendered and stored against the deal. |
| `CANCELLED` | Never confirmed; the reserved units were returned to the bond. |

```
CREATED --confirm--> CONFIRMED --letter produced--> CONFIRMATION_GENERATED
   |
   +---cancel---> CANCELLED
```

Only `CREATED` can move. Confirm and cancel are mutually exclusive: whoever's
conditional update wins the race decides the outcome, and the loser receives a
`409`.

Customer-placed deals (`POST /api/deal-confirmations`) are unchanged: no
confirmation step, letter generated immediately.

---

## 2. Permissions

| Module grant | What it allows |
|---|---|
| `DEAL_CREATION` | Create deals, reprice a `CREATED` deal, cancel your own deal, list/download your own deals. |
| `DEAL_CONFIRMATION` | Confirm any back-office deal. |

The two are separate grants and are stored in the account's
`accessConfig` (set through the admin user create/update endpoints). An
`ADMIN` passes every module check; an `EMPLOYEE` needs the grant.

A caller without the grant gets `403`.

---

## 3. Create a deal (unchanged)

```http
POST /api/admin/deals
Content-Type: application/json

{
  "clientId": "…",
  "bondId": "…",
  "numberOfLots": 5,
  "dealDate": "2026-10-31",
  "sendEmail": true,
  "bankAccountId": "…",          // optional
  "dematAccountId": "…",         // optional
  "side": "SELL",
  "modeOfDelivery": "ICCL",
  "settlementNumber": "2610022",
  "depository": null
}
```

Response `201`:

```json
{
  "dealConfirmationId": "…",
  "dealReference": "2026/S/OCT/144",
  "status": "CREATED",
  "pricePerUnit": "98.9400",
  "totalAmount": "49470.0000",
  "annualYtm": null,
  "semiYtm": null,
  …
}
```

The unit reservation is atomic with the row write: if the bond has fewer units
left, the request fails with `409` and nothing is written.

**No confirmation letter is produced at this point.** The letter inputs above
(side, delivery mode, settlement number, depository, chosen accounts,
`sendEmail`) are stored on the deal so the letter can be rendered at confirm
time.

### 3.1 Preview the price — before the deal exists

To strike a deal at a negotiated price, calculate the price/yield pair first.
This writes nothing to the bond and nothing to a deal.

```http
POST /api/admin/deals/price-preview
Content-Type: application/json

{
  "bondId": "…",
  "price": 98.94,          // send exactly one of price / annualYtm
  "dealDate": "2026-10-31" // optional; sets the value date, default today
}
```

or `{ "bondId": "…", "annualYtm": 12.5, "dealDate": "2026-10-31" }`.

Response `200`:

```json
{
  "bondId": "…",
  "isin": "INE123A01016",
  "bondName": "Example Bond",
  "valueDate": "2026-10-31",
  "pricePerUnit": "98.9400",
  "annualYtm": "12.5000",
  "semiYtm": "12.1320"
}
```

The result is **cached for 30 minutes against the employee and the bond**. The
next `POST /api/admin/deals` for that bond saves the cached price and yield on
the new deal and clears the entry. Without a preview (or if the entry expired,
or Redis is unavailable) the deal is created at the bond's own price, exactly as
before.

After the deal exists, use `POST /api/admin/deals/{dealId}/calculate` — that
writes to the database, not the cache.

Errors: `400` when neither/both of `price` and `annualYtm` are sent or the price
is not positive; `404` when no bond has that id; `403` without `DEAL_CREATION`.

---

## 4. Reprice / edit

```http
POST /api/admin/deals/{dealId}/calculate
Content-Type: application/json

{ "price": 98.94 }
```

or `{ "annualYtm": 12.5 }` — exactly one.

Only a `CREATED` deal can be repriced. A confirmed or cancelled deal answers
`409`:

```json
{ "message": "Deal is CONFIRMED and cannot be changed: 2026/S/OCT/144" }
```

A deal created by another employee is a `404`.

---

## 5. Confirm

```http
POST /api/admin/deals/{dealId}/confirm
```

Response `200` with the same deal shape, now `"status": "CONFIRMED"`.

Effects:

- The deal is frozen — it can no longer be repriced or cancelled.
- The reserved units stay reserved.
- A confirmation-letter request is published (asynchronously). Download it via
  the existing document endpoints once it exists.

Errors:

| Code | When |
|---|---|
| `409` | The deal is not `CREATED` (already confirmed, already cancelled, or a customer deal). |
| `404` | No deal with that id. |
| `403` | Caller lacks `DEAL_CONFIRMATION`. |

Any employee holding `DEAL_CONFIRMATION` may confirm any back-office deal; it
does not have to be a deal they created.

---

## 6. Cancel

```http
POST /api/admin/deals/{dealId}/cancel
```

Response `200` with `"status": "CANCELLED"`.

Effects:

- The deal's `totalQuantity` is added back to the bond's `quantum` in the same
  transaction.
- A bond that had been marked `SOLD_OUT` is reactivated if its quantum is
  positive again.
- No letter is generated.

Only the employee who created the deal can cancel it (`DEAL_CREATION`); another
employee's deal is a `404`. The release runs at most once per deal — a repeat
cancel answers `409` and never credits the units twice.

---

## 7. Listing and document download (unchanged)

```http
GET /api/admin/deals?search=&page=0&size=20
GET /api/admin/deals/document?reference=2026/S/OCT/144
```

Each listing row carries `status`, so the UI can show the deal's progress.
The letter exists only after the deal is confirmed and its generation has
finished; until then the download answers `404`.

---

## 8. Database

New columns on `deal_confirmations` (`V22__add_deal_confirmation_lifecycle.sql`):

| Column | Type | Notes |
|---|---|---|
| `delivery_mode` | `varchar(20)` | nullable |
| `settlement_number` | `varchar(64)` | nullable |
| `side` | `varchar(10)` | not null, default `SELL` |
| `depository` | `varchar(20)` | nullable |
| `bank_account_id` | `uuid` | nullable |
| `demat_account_id` | `uuid` | nullable |
| `send_email` | `boolean` | not null, default `false` |

`status` needed no schema change; `CONFIRMED` and `CANCELLED` are new values of
the existing varchar column. Existing rows are unaffected.

Dev/test let Hibernate create the columns (`ddl-auto=update`). The migration is
run by hand in production, like `V1`–`V21`; Flyway is not active.
