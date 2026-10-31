# Admin Deal Price Preview — Frontend Integration Guide

Prices a bond for a deal that does not exist yet. The employee supplies a price
or a yield, the server calculates the other half, and the pair is cached so the
create call can save it on the deal. Nothing is written to the bond.

Base URL examples use `https://api.example.com`; substitute the environment's API
host. All calls need `Authorization: Bearer <accessToken>`.

---

## 1. Why this exists

`POST /api/admin/deals/{dealId}/calculate` prices a deal that already exists.
This endpoint is for the step before that: the employee is filling in the create
form, has negotiated a price for one client, and wants to know what yield it
means (or the other way round) — without that figure ever becoming the bond's
quoted price or yield.

The result is cached per employee and bond. The next create request for that bond
picks it up and saves it on the deal; the cache entry is then discarded. Once the
deal exists, edits go straight to the database through `/{dealId}/calculate`.

```
price-preview          create deal              edit deal
(bondId + price/ytm) → (uses cache, then DB)  → (/{dealId}/calculate, DB)
        │                        │
        └── Redis, 30 min ───────┘
```

---

## 2. Permission

Requires the `DEAL_CREATION` module grant on the employee's account (same grant
as creating a deal). Missing grant → `403`.

---

## 3. Request

```http
POST /api/admin/deals/price-preview
Content-Type: application/json

{
  "bondId": "3f2b9c1e-0000-0000-0000-000000000000",
  "price": 98.94,
  "dealDate": "2026-10-31"
}
```

| Field | Type | Required | Notes |
|---|---|---|---|
| `bondId` | UUID | yes | The bond to price. |
| `price` | number | one of `price` / `annualYtm` | Clean price as a percentage of par, > 0. |
| `annualYtm` | number | one of `price` / `annualYtm` | Annual yield as a percentage, e.g. `12.5`. |
| `dealDate` | ISO date | no | The date the deal will be struck. Sets the value date the calculation measures to. Defaults to today. |

Supply **exactly one** of `price` and `annualYtm`. The other is calculated.

Solving the price from a yield:

```json
{
  "bondId": "3f2b9c1e-0000-0000-0000-000000000000",
  "annualYtm": 12.5,
  "dealDate": "2026-10-31"
}
```

---

## 4. Response `200`

```json
{
  "bondId": "3f2b9c1e-0000-0000-0000-000000000000",
  "isin": "INE123A01016",
  "bondName": "Example Bond",
  "valueDate": "2026-10-31",
  "pricePerUnit": "98.9400",
  "annualYtm": "12.5000",
  "semiYtm": "12.1320"
}
```

- `pricePerUnit` — clean price as a percentage of par, to 4 decimals.
- `annualYtm` — annual yield as a percentage, to 4 decimals.
- `semiYtm` — the semi-annual equivalent implied by compounding, as a
  percentage, to 4 decimals.
- `valueDate` — the settlement date the figures were measured to. Use the same
  `dealDate` on the create request so the deal and the preview agree.

These are the same figures the create response carries (`pricePerUnit`,
`annualYtm`, `semiYtm`) once the cached pair is applied.

---

## 5. Cache behaviour

| What | Value |
|---|---|
| Key | `admin:deal-pricing:{employeeId}:{bondId}` |
| TTL | 30 minutes |
| Written by | this endpoint, on every successful calculation (overwrites) |
| Read by | `POST /api/admin/deals` for that employee and bond |
| Removed by | the create request that consumes it, or expiry |

Consequences the frontend can rely on:

- A preview is private to the employee who made it. Another employee pricing the
  same bond gets their own entry.
- Re-previewing the same bond replaces the cached pair — the latest calculation
  is the one create will use.
- If the entry has expired or was never made, create behaves exactly as before:
  the bond's own price, no deal-scoped yield.
- If Redis is unavailable, create still succeeds at the bond's own price (the
  failure is logged, never surfaced to the employee). The preview call itself
  fails, so it cannot silently return figures that will not be saved.

---

## 6. Using the result on create

```http
POST /api/admin/deals
Content-Type: application/json

{
  "clientId": "…",
  "bondId": "3f2b9c1e-0000-0000-0000-000000000000",
  "numberOfLots": 5,
  "dealDate": "2026-10-31",
  "sendEmail": true,
  "side": "SELL",
  "modeOfDelivery": "ICCL",
  "settlementNumber": "2610022"
}
```

The response carries the previewed figures and the values the confirmation
letter prints:

```json
{
  "dealConfirmationId": "…",
  "dealReference": "2026/S/OCT/144",
  "status": "CREATED",
  "pricePerUnit": "98.9400",
  "totalAmount": "49470.0000",
  "annualYtm": "12.5000",
  "semiYtm": "12.1320",
  …
}
```

The bond's stored price/yield are unchanged. For the lifecycle after creation —
confirm, cancel, letter generation — see
[`deal-confirmation-lifecycle.md`](./deal-confirmation-lifecycle.md).

---

## 7. Errors

| Code | When |
|---|---|
| `400` | Neither or both of `price` / `annualYtm` supplied; `price` is not positive. |
| `400` | The supplied yield implies a price that is not positive, or the bond has no future cash flow to price. |
| `404` | No bond with that `bondId`. |
| `403` | The caller lacks `DEAL_CREATION`. |

Error body (`ApiError`):

```json
{ "message": "Supply exactly one of price or annualYtm" }
```

---

## 8. Implementation map

| Concern | Where |
|---|---|
| Endpoint | `Modules/Admin/Controller/AdminDealController` → `POST /price-preview` |
| Calculation + cache | `Modules/Admin/Service/AdminDealPricingService` |
| Cache implementation | `Modules/Common/Redis/RedisService` (JSON values) |
| Applied on create | `AdminDealService.create` → `DealConfirmationWriter.createForClient` |
| Engine reused | `PriceCalculationService`, `YtmCalculationService.previewAnnualYtm` |

TTL and key prefix are constants in `AdminDealPricingService` (`CACHE_TTL`,
`key(...)`).
