# Bond Module

The bond catalogue — what a bond is, who issued it, and how many units are left
to sell — plus a self-contained YTM calculation engine that turns a bond's
free-text maturity and coupon fields into a cash-flow series and an internal
rate of return.

---

## 1. What this module does

The module has two halves that meet only at the `Bond` entity.

**The catalogue.** Bulk-imported from Excel, exposed read-only to customers and
managed by admins. A bond belongs to at most one `Issuer`. This half is what the
HTTP API serves today.

**The calculation engine.** A set of collaborating services that derive coupon
dates from a text description, build a principal repayment schedule, compute
coupon and accrued-interest amounts, apply record-date entitlement, assemble a
cash-flow series and solve it for a yield. It is fully implemented and heavily
tested, but **nothing in the running application calls it** — see
[§9](#9-what-is-not-wired-up).

Several source files begin with a large commented-out earlier implementation;
the live class is the *second* class in the file. This trips up grep and
file-length intuition, so it is worth knowing:

| File | Live class starts at |
|---|---|
| `MaturityDescriptionParserImpl.java` | line 806 of 1774 |
| `CouponCalculationServiceImpl.java` | line 632 of 1082 |
| `AccruedInterestServiceImpl.java` | line 217 of 570 |
| `PrincipalRepaymentServiceImpl.java` | ~line 165 of 814 |

### Files

| File | Responsibility |
|---|---|
| `Controller/BondController` | The public, unauthenticated read API. |
| `Controller/AdminBondController` | Every write path. `@PreAuthorize("hasRole('ADMIN')")` on the class. |
| `Models/Bond` | The `bonds` table. |
| `Models/Issuer` | The `issuers` table. |
| `Repository/BondRepository` | ISIN lookup, search, pessimistic lock, the inventory guard. |
| `Repository/IssuerRepository` | Uniqueness probes and keyset ("cursor") pagination. |
| `Service/BondService` | Create, partial update, price update, activate/suspend/cancel. |
| `Service/IssuerService` | Attach/update an issuer, list with a cursor, bulk import. |
| `Service/IssuerMapper` | Field copying, blank-to-null normalization, uniqueness checks. |
| `Service/IssuerBulkWriter` | One row of a bulk import, in its own transaction. |
| `Exception/UnsupportedRecordDateDescriptionException` | Raised when a record-date rule cannot be parsed. |
| **Calculation** | |
| `Service/CouponDateGenerator` | Parses `ipDateDescription` into a list of coupon dates. |
| `Service/PrincipalRepaymentService(Impl)` | Builds the principal repayment schedule. |
| `Service/CouponCalculationService(Impl)` | Coupon amounts against declining principal. |
| `Service/AccruedInterestService(Impl)` | Accrued interest to the settlement date. |
| `Service/RecordDateParser(Impl)` | Derives a record date per coupon payment date. |
| `Service/RecordDateDescriptions`, `RecordDateRule`, `RelativeDaysRecordDateRule` | The pluggable record-date grammar. |
| `Service/CouponEntitlementService(Impl)` | Does the buyer receive this coupon? |
| `Service/PurchaseConsiderationService(Impl)` | Cum-interest or ex-interest, and the resulting price. |
| `Service/CouponScheduleService` | The coupon period containing a given date. |
| `Service/BondCashFlowService(Impl)` | Assembles the cash-flow series. |
| `Service/XirrCalculator` | Newton–Raphson + bisection solver. |
| `Service/YtmCalculationService(Impl)` | Orchestrates the above, persists `annualYtm`. |
| `Service/*AmortizationRule` | Sealed strategy for how principal is repaid. |

---

## 2. Domain model

### 2.1 `Bond` → `bonds`

`@Entity`, UUID id, five indexes: unique `isin`, plus `status`, `maturity_date`,
`security_type`, `rating`.

Fields are grouped by intent in the source. The load-bearing ones:

| Field | Column | Notes |
|---|---|---|
| `isin` | `isin` | `NOT NULL`, unique. Stored **upper-cased**; every lookup normalizes input first. |
| `name` | `name` | `NOT NULL`, 500. |
| `securityType` | `security_type` | `NOT NULL` enum: `SECURED`, `UNSECURED`. |
| `category` | `category` | Free text from the sheet, e.g. `Category 1 ( Gsec/ SDL )`. |
| `couponRate` | `coupon_rate` | `NOT NULL`, **stored as a percentage** — `7.20` means 7.20%. |
| `couponFrequency` | `coupon_frequency` | Nullable enum. |
| `ipDateDescription` | `ip_date_description` | The coupon-schedule text. Never parsed at write time. |
| `recordDateDescription` | `record_date_description` | The entitlement rule text. See [§8.6](#86-record-date-and-entitlement). |
| `maturityDate` | `maturity_date` | Normalized. |
| `maturityDescription` | `maturity_description` | 1500 chars. The original text, which carries amortization detail. |
| `maturityType` | `maturity_type` | Enum. **Stored but never read by the calculation engine** — see [§9](#9-what-is-not-wired-up). |
| `price` | `price` | Nullable: Excel price can be blank. Clean price per 100 face. |
| `semiYtm` / `annualYtm` / `ytc` / `ytmCalculatedAt` | | Internally calculated, never imported. Deliberately **not** in `BondResponse`. |
| `quantumDescription` / `quantumInLacs` | | Original text and the normalized lakh value. |
| `lotSizeDescription` / `lotSize` / `lotSizeType` | | Original text, numeric value, and its kind. |
| `remainingQuantity` | `remaining_quantity` | Inventory. Nullable — `NULL` means "not configured". |
| `status` | `status` | `NOT NULL`, defaults to `DRAFT`. |
| `isFlashNews` | `is_flash_news` | `NOT NULL`, default `false`. |
| `createdBy` | `created_by` → `users.clerk_user_id` | Note the FK targets **`clerk_user_id`**, not the user PK. |
| `sourceFileName` / `sourceRowNumber` / `importedAt` | | Import provenance. |
| `issuer` | `issuer_id` | `@ManyToOne(LAZY)`. |

### 2.2 `Issuer` → `issuers`

UUID id. Three unique columns — `issuer_code`, `cin`, `lei` — each also carrying a
unique index. `description` is the only `TEXT` column in the module. No
`@OneToMany` back to `Bond`: a bond owns at most one issuer, but one issuer row
can be shared by many bonds.

### 2.3 Enums

All are plain string enums with no fields or methods, persisted via
`@Enumerated(EnumType.STRING)`.

| Enum | Constants | Note |
|---|---|---|
| `BondStatus` | `DRAFT, ACTIVE, SOLD_OUT, MATURED, SUSPENDED, CANCELLED` | Only `DRAFT`, `ACTIVE`, `SUSPENDED`, `CANCELLED` are ever set by code. |
| `CouponFrequency` | `MONTHLY, QUARTERLY, HALF_YEARLY, YEARLY, AT_MATURITY` | `AT_MATURITY` is rejected by the coupon calculation. |
| `SecurityType` | `SECURED, UNSECURED` | |
| `MaturityType` | `FIXED, PERPETUAL, AMORTIZING, RANGE, UNKNOWN` | Not consumed by the engine. |
| `LotSizeType` | `DEMAT, SGL, NUMERIC, FIXED_LOT, UNKNOWN` | `DEMAT`/`SGL` mean `lotSize` is `NULL`. |
| `PurchasePriceTreatment` | `CUM_INTEREST, EX_INTEREST` | Carries the settlement-convention javadoc. |
| `BondOrderStatus` | `PENDING, PAYMENT_PENDING, PAID, CONFIRMED, CANCELLED, FAILED, REFUNDED` | **Belongs to the Order module**, not this one — it lives here for historical reasons. |

---

## 3. API surface

### 3.1 Public — `BondController` (`/api/bonds`)

No `@PreAuthorize`. `SecurityConfig` permits `GET /api/bonds` and
`GET /api/bonds/**` to everyone; there is **no write endpoint** on this path.

| Method | Path | Parameters | Returns |
|---|---|---|---|
| GET | `/api/bonds` | `search`, `isFlashNews`, pageable (default size 20, `createdAt` DESC) | `Page<BondResponse>` |
| GET | `/api/bonds/{isin}` | | `BondResponse` |
| GET | `/api/bonds/{isin}/issuer` | | `IssuerResponse` |

A `POST /{bondId}/calculate-ytm` endpoint is commented out in the source.

### 3.2 Admin — `AdminBondController` (`/api/admin`)

Class-level `@PreAuthorize("hasRole('ADMIN')")`, so every path below needs an
admin JWT. Only `createBond` reads the principal (`jwt.getSubject()`).

| Method | Path | Body / params | Status |
|---|---|---|---|
| POST | `/api/admin/bonds` | `CreateBondRequest` | 201 |
| PATCH | `/api/admin/bonds/prices` | `List<BondPriceUpdateRequest>` (`@NotEmpty`) | 200 |
| GET | `/api/admin/bonds` | `search`, pageable | 200 |
| GET | `/api/admin/bonds/{isin}` | | 200 |
| PATCH | `/api/admin/bonds/{isin}` | `UpdateBondRequest` | 200 |
| PATCH | `/api/admin/bonds/{isin}/activate` | | 200 |
| PATCH | `/api/admin/bonds/{isin}/suspend` | | 200 |
| DELETE | `/api/admin/bonds/{isin}` | | 204 |
| GET | `/api/admin/issuers` | `search`, `cursor`, `size` | 200 |
| POST | `/api/admin/bonds/{isin}/issuer` | `CreateIssuerRequest` | 201 |
| PATCH | `/api/admin/bonds/{isin}/issuer` | `UpdateIssuerRequest` | 200 |
| GET | `/api/admin/bonds/{isin}/issuer` | | 200 |
| POST | `/api/admin/bonds/issuers/bulk` | `List<BondIssuerBulkItem>` (`@NotEmpty`) | 200 |

Note the admin list endpoint calls `getBonds(search, null, pageable)` — it can
never filter by `isFlashNews`, only search by name.

### 3.3 Error responses

`GlobalExceptionHandler` maps `ResourceNotFoundException` → 404,
`ConflictException` → 409, and validation failures → 400 with a
`field: message` list.

---

## 4. Bond lifecycle

`BondService` is `@Transactional` at class level; the read methods opt into
`readOnly`. Status is only ever moved by an explicit admin call.

```
createBond ──► DRAFT
                 │
                 ├── activateBond ──► ACTIVE
                 │        │
                 │        └── suspendBond ──► SUSPENDED
                 │
                 └── cancelBond ──► CANCELLED
```

Rules enforced by `BondService`:

- **Create** — ISIN is trimmed and upper-cased, then `existsByIsin` guards
  against duplicates (`409`). `maturityType` is required; a `FIXED` bond also
  requires `maturityDate`. The new bond is `DRAFT` and `createdBy` is the admin.
  **YTM fields are explicitly set to `null`** — they are never accepted from the
  request.
- **Update** — every field is null-tolerant, so only the fields present are
  applied. Blocked once `MATURED`. Changing `price` calls
  `invalidateYieldCalculation`, nulling `semiYtm`, `annualYtm`, `ytc` and
  `ytmCalculatedAt`, so a stale yield can never be served alongside a new price.
- **`remainingQuantity` on update is absolute, not a delta.** An admin restocking
  sends the new total. Status is deliberately left alone — a restocked `SOLD_OUT`
  bond must be re-activated through `/activate`.
- **Activate** — blocked from `MATURED` and `CANCELLED`; re-checks that
  `maturityType` exists and that a `FIXED` bond has a `maturityDate`.
- **Suspend** — blocked from `MATURED` and `CANCELLED`.
- **Cancel** — blocked from `MATURED`, and rejects an already-cancelled bond.
  A comment marks where a "bond has existing purchases" check will go once the
  order model supports it; the check is not implemented.

`getBonds(search, isFlashNews, pageable)` delegates to `findAll` when both
filters are absent, otherwise to `searchBonds`. That query's `isFlashNews`
predicate is `(:isFlashNews IS NULL OR :isFlashNews = false OR b.isFlashNews = true)`
— so passing `false` returns *everything*, and the only meaningful filter value
is `true`. There is no way to ask for non-flash-news bonds.

---

## 5. Issuers

### 5.1 Attaching and updating

`addIssuerToBond` refuses a bond that already has an issuer (409, "use PATCH").
`updateIssuer` refuses a bond with none (404). Both run
`IssuerMapper.validateUnique`, which probes `issuer_code`, `cin` and `lei`
(excluding the row being updated, so an issuer never conflicts with itself).

### 5.2 Normalization

`IssuerMapper.normalize` trims every string and converts blank to `NULL`. This is
not cosmetic: without it two issuers with an empty `issuer_code` would both try
to insert `""` into a unique column and the second would fail.

### 5.3 Bulk import

`bulkUpsertIssuers` is deliberately **not** `@Transactional`. It loops the rows
and calls `IssuerBulkWriter.writeRow`, which is
`@Transactional(propagation = REQUIRES_NEW)`. Each row therefore commits on its
own, and one bad row cannot roll back the others.

For each row the writer looks up the bond by ISIN and then:

- **bond has no issuer** → insert a new `Issuer`, link it, report `created`.
- **bond already has an issuer** → overwrite the present fields, report
  `updated`. Re-importing the same sheet is therefore idempotent.

Failures are collected, not thrown. Before the writer is called, the service
rejects duplicates *within the request* (`Duplicate ISIN in request: …`,
`Duplicate issuer code in request: …`, and so on for CIN and LEI). A throw from
the writer becomes a `BulkIssuerError` carrying the 1-based `row`, the ISIN and
the message. The response reports `totalRequested`, `createdCount`,
`updatedCount`, `failedCount`, plus the successful issuers and the error list —
so partial success is visible in one payload.

### 5.4 Cursor pagination

`GET /api/admin/issuers` uses keyset pagination, not offset, so results stay
correct while rows are inserted or deleted.

The ordering is `LOWER(name)` with `id` as a tie-breaker — two issuers can share
a name, never an id. The cursor is the `(lowercased name, id)` pair of the last
row, Base64-URL encoded and split on the **last** `|` (a name may contain one).
It is decoded back into `findPageBySearchAfter`, whose predicate resumes strictly
after that pair.

The service fetches `size + 1` rows to decide `hasNext` without a second count
query, then trims. `size` is clamped to 1–100, defaulting to 20. An
undecodable cursor yields 400 "Invalid cursor". Search is a case-insensitive
substring of the name, with `%`, `_` and the escape character escaped so a
literal `50%` searches for a percent sign.

---

## 6. Inventory and the oversell guard

`Bond.remainingQuantity` is the single source of truth for stock. `NULL` means
inventory was never configured (true of every bond imported before the column
existed); `0` means sold out.

`BondRepository.reserveQuantity` deducts units with a single conditional UPDATE
(JPQL, shown with SQL-ish syntax):

```sql
UPDATE Bond b
   SET b.remainingQuantity = b.remainingQuantity - :quantity
 WHERE b.id = :id
   AND b.remainingQuantity >= :quantity
```

The `>=` predicate is evaluated by the database while it holds the row lock, so
two concurrent buyers are serialized and the loser matches zero rows instead of
driving inventory negative. Reading the quantity, checking it in Java and then
updating would reopen exactly that race. Callers must check the returned row
count: `1` means reserved, `0` is a business outcome meaning "insufficient
quantity", not a retryable error.

**This method has no caller in production code.** A test
(`BondRepositoryReserveQuantityContractTest`) pins its shape — it asserts the
statement stays a single parameterised UPDATE with the `>=` predicate and that
it is the only inventory-mutating method on the repository — so the guard cannot
be "simplified" away while the reservation step that will use it is still being
built.

Nothing flips `status` to `SOLD_OUT` automatically. A bond at zero units whose
status is still `ACTIVE` is possible, and is refused by the conditional UPDATE
matching zero rows.

`findByIdForUpdate` (`PESSIMISTIC_WRITE`, i.e. `SELECT … FOR UPDATE`) is also
currently uncalled; its one intended call site is commented out in
`BondOrderService`.

---

## 7. The calculation engine

### 7.1 Pipeline

```
CouponDateGenerator        ipDateDescription ──► coupon dates
        │
PrincipalRepaymentService  maturityDescription ──► principal repayments
        │
CouponCalculationService   ──► CouponPayment(date, amount, openingPrincipal)
        │
AccruedInterestService     previous/next coupon period ──► accrued interest
        │
RecordDateParser           recordDateDescription + paymentDate ──► record date
CouponEntitlementService   record date ──► is this coupon the buyer's?
PurchaseConsiderationService ──► cum- or ex-interest purchase price
        │
BondCashFlowService        ──► List<CashFlow>
        │
XirrCalculator             ──► annual YTM (decimal)
        │
YtmCalculationServiceImpl  ──► persists annualYtm (percentage, 2dp)
```

The single entry point is
`BondCashFlowService.generateCashFlows(bond, calculationDate)`. It validates that
the bond is non-null, the date is non-null, and the price is present and
positive, then calls the stages in the order above.

### 7.2 Coupon dates — `CouponDateGenerator`

Parses `Bond.ipDateDescription` against three patterns, after trimming and
collapsing whitespace. Only dates **strictly after** the calculation date and
**on or before** maturity are returned.

| Pattern | Example | Meaning |
|---|---|---|
| `^\s*(\d{1,2})/(\d{1,2})\s*-\s*(\d{1,2})/(\d{1,2})\s*$` | `09/02-09/08` | Semi-annual: 9 Feb and 9 Aug each year. |
| `^\s*(\d{1,2})/(\d{1,2})\s+Ann\.?\s*$` | `15/10 Ann` | Annual: 15 October each year. |
| `^\s*(\d{1,2})(?:st\|nd\|rd\|th)?\s+of\s+every\s+month\s*$` | `31st of every month` | Monthly. |

Anything else raises `Unsupported IP date description: …`. The monthly pattern
clamps the requested day to the length of the month, so `31st of every month`
yields 28 or 29 in February and 30 in April. Invalid calendar dates (e.g.
31 February in a non-leap year) are silently skipped rather than failing the
whole schedule.

**There is no business-day adjustment.** Coupon dates are pure calendar dates;
weekends and holidays are not rolled.

### 7.3 Principal repayments — `PrincipalRepaymentService`

Delegates the meaning of `maturityDescription` to `MaturityDescriptionParser`,
which returns a `MaturitySchedule(maturityDate, amortizationRules, perpetual)`.
Note it branches on **that schedule**, never on `Bond.maturityType`.

- **Perpetual** (`Perp`, `Perpetual`) → no repayments at all.
- **Bullet** (no rules) → a single repayment of 100 at maturity.
- **Amortizing** → rules are mapped onto dates and walked in chronological order.

Two `AmortizationRule` implementations, both `record`s with validating
constructors:

| Rule | Dates come from |
|---|---|
| `IpBasedAmortizationRule` | The actual coupon dates falling inside `[startDate, endDate]`. |
| `FixedFrequencyAmortizationRule` | Dates generated by stepping from `startDate` by `MONTHLY`/`QUARTERLY`/`HALF_YEARLY`/`YEARLY` until `endDate`. |

`IpBasedAmortizationRule` is what `"2.5% on Each IP till 2027"` produces — "IP"
is the *interest payment* date, so the repayment is tied to the coupon schedule.
The `till` form derives its start year as `maturityYear - 7`, a business rule
preserved from the source data.

Amortization percentages are applied to the **original face value of 100**, not
to the outstanding principal: 20% means 20 per installment, every time. The
schedule is walked with a running `remainingPrincipal`; a repayment never
exceeds what remains, the maturity date always repays the remainder in full (so
configured installments that do not sum to 100 still redeem cleanly), and two
rules landing on the same date is an error rather than a silent double payment.
Repayments on or before the calculation date reduce the outstanding principal
but are not returned, since they are not future cash flows.

### 7.4 Coupon amounts — `CouponCalculationService`

For each future coupon date:

```
couponAmount = openingPrincipal × couponRate ÷ (100 × frequencyDivisor)
```

at scale 10, `HALF_UP`. Frequency divisors are `MONTHLY 12`, `QUARTERLY 4`,
`HALF_YEARLY 2`, `YEARLY 1`; `AT_MATURITY` throws
`Unsupported coupon frequency: AT_MATURITY`.

The opening principal starts at 100 and is reduced by repayments as the schedule
is walked. Two ordering rules matter:

- A repayment dated **before** a coupon date reduces the principal used for that
  coupon.
- A repayment dated **on** a coupon date is applied **after** that coupon is
  computed. The coupon for that day was earned on the opening principal.

Once principal reaches zero, no further coupons are produced — but the coupon
already computed on the opening principal is kept.

This is why the `couponRate` and the amortization percentage must never be
confused: the amortization percentage only decides how much principal is repaid.
It is not a coupon rate.

### 7.5 Accrued interest — `AccruedInterestService`

Returns zero immediately for a zero-coupon bond (`couponRate == 0`) or an
`AT_MATURITY` frequency, and for a settlement date with no containing coupon
period or one that lands exactly on a coupon date.

Otherwise, using `CouponScheduleService` to find the containing period:

```
periodCoupon = outstandingPrincipal × couponRate ÷ (100 × frequencyDivisor)

accruedInterest = periodCoupon × accruedDays ÷ couponPeriodDays
```

where `accruedDays` is `previousCoupon → calculationDate` and
`couponPeriodDays` is `previousCoupon → nextCoupon`. This is an
**Actual/Actual within the coupon period** convention, at scale 20, `HALF_UP`.

The principal used is the amount outstanding at settlement: repayments strictly
before the settlement date are deducted from 100, while a repayment falling
exactly on the settlement date is not — mirroring the coupon convention in §7.4.
A negative result is clamped to zero.

### 7.6 Record date and entitlement

This is the part of the engine added most recently, and the one with the most
deliberate design.

**A record date is never stored.** `Bond.recordDateDescription` holds the *rule*
as text — for example `"15 days prior to interest payment date"`. Because a
monthly bond has a different record date for every coupon, the rule is resolved
per payment date. A record date is an entitlement reference date: it is **never**
a cash flow, never moves a coupon, and never replaces a payment date.

`RecordDateParser` normalizes the description (upper-case `Locale.ENGLISH`,
hyphens between alphanumerics become spaces, other punctuation becomes a space,
whitespace collapsed) and then offers it to an ordered list of `RecordDateRule`
strategies, of which one is implemented.

`RelativeDaysRecordDateRule` matches:

```
(\d{1,4})\s*DAYS?\s*(PRIOR|PREVIOUS|BEFORE|EARLIER|AFTER|AHEAD)
```

and returns the offset in days *before* the payment date (negative for
`AFTER`/`AHEAD`). The result is `paymentDate.minusDays(offset)`.

The rule refuses rather than guesses. It returns "no rule" for a **range**
(`15-20 days prior`), for **conflicting offsets** in one description
(`15 days prior … and 7 days prior to maturity`), for a non-positive day count,
and for any trailing meaning it does not model — anything left over after the
known reference words (`INTEREST`, `COUPON`, `IP`, `PAYMENT`, `DATE`, …) are
removed. Qualifiers that change the arithmetic, such as *working days* or
*hours*, are deliberately absent from that vocabulary, so
`"3 days prior to the last working day"` is rejected instead of silently
becoming three calendar days.

Descriptions that mean "no record date" — blank, `NA`, `N/A`, `N.A.`,
`Not Applicable`, `Not Relevant`, `Nil`, `None`, `No`, and bare `-`/`--`
placeholders — resolve to an empty result. These collapse to the same token
because the check runs after stripping all non-alphanumerics.

**Any other unparseable description throws
`UnsupportedRecordDateDescriptionException`.** This is a hard failure by design:
a silently assumed default offset would produce a wrong record date, hence a
wrong entitlement decision, hence a plausible-looking but wrong YTM. The
exception message carries the original text so it can be fixed at the source.
It is **not** handled by `GlobalExceptionHandler`, so it surfaces as a 500.

`CouponEntitlementService` then answers the actual question:

```
entitled = calculationDate <= recordDate
```

with two guards: a coupon paid on or before the calculation date is never
included, and a bond with **no** record-date rule includes every future coupon.
That second guard is what keeps historical YTM unchanged for the many bonds
whose source data carries no record-date information.

### 7.7 Purchase consideration

`PurchaseConsiderationService` decides what the buyer pays, using the **first
future coupon** and that coupon's own record date — never a bond-level flag,
because a monthly bond moves in and out of the ex-interest window once per
period.

| Treatment | Purchase consideration |
|---|---|
| `CUM_INTEREST` — buyer is entitled to the upcoming coupon | `cleanPrice + accruedInterest` |
| `EX_INTEREST` — buyer is not entitled | `cleanPrice` (accrued interest not charged) |

The accrued interest is dropped on an ex-interest purchase because it is
precisely the accrued portion of the coupon the buyer will not receive. It is
the seller's compensation for keeping that coupon; charging it as well would pay
the seller twice — once through the coupon and again through the price.

When the projection has no future coupon at all, or the bond has no record-date
rule, the treatment is `CUM_INTEREST` with accrued interest charged, preserving
pre-feature behaviour.

The returned `PurchaseConsideration` carries the treatment, the clean price, the
accrued interest actually charged, the consideration, and the upcoming payment
and record dates — so a caller can see *why* a figure was used, not just the
figure.

### 7.8 Cash-flow assembly

`BondCashFlowService` builds a date-keyed map:

```
calculationDate  →  -purchaseConsideration     (negative: the outflow)
coupon dates     →  +couponAmount              (only if entitled)
principal dates  →  +principalAmount
```

Same-date amounts are merged by addition, so a coupon and a principal repayment
falling on one day become a single cash flow. The purchase leg is computed
*after* the coupons, because the treatment depends on the first future coupon.

Each coupon's record date is resolved fresh for that payment — nothing is cached
or reused across payments — and a coupon always keeps its payment date.

### 7.9 XIRR and YTM

`XirrCalculator` discounts on an **Actual/365** basis:

```
years = days(startDate, date) / 365
NPV   = Σ amount / (1 + rate)^years
```

It brackets the root between `-0.9999999999` and an upper bound that doubles
(from 1.0 to 3.0 to 7.0 …) until the NPV changes sign, capped at `1e10`. It then
iterates Newton–Raphson from an initial guess of 8%, falling back to bisection
whenever the Newton step leaves the bracket or the derivative is unusable. It
returns at the first NPV below `1e-10`, or when the bracket collapses below
`1e-10` with an NPV under `1e-7`. Exhausting 100 iterations, or failing to
bracket at all, raises `IllegalStateException`.

The result is a decimal to 6 places, `HALF_UP`.

`YtmCalculationService` wraps this: it generates the cash flows, solves them,
multiplies by 100 and stores the result as `annualYtm` at scale 2, stamps
`ytmCalculatedAt`, and returns the **decimal** (so `0.106947`, while the
persisted field holds `10.69`). The `LocalDate.now()` overload is a convenience;
the explicit-date overload never reads the clock and is the one to use in tests.

### 7.10 Worked example

A bond with `couponRate` 12%, `couponFrequency` `MONTHLY`,
`ipDateDescription` `"23rd of every month"`,
`recordDateDescription` `"15 days prior to interest payment date"`, clean price
98.94, valued on 2026-10-09:

1. **Coupon dates** — 2026-10-23, 2026-11-23, 2026-12-23, … up to maturity.
2. **Principal** — bullet, so a single repayment of 100 at maturity.
3. **Coupons** — 100 × 12 ÷ (100 × 12) = 1.00 per month on the opening principal.
4. **Accrued interest** — the containing period is 2026-09-23 → 2026-10-23
   (30 days); 16 days have accrued, so 1.00 × 16 ÷ 30 = 0.5333.
5. **Record date** — the first future coupon is 2026-10-23, whose record date is
   2026-10-08.
6. **Entitlement** — the valuation date 2026-10-09 is *after* 2026-10-08, so the
   buyer is **not** entitled to that coupon.
7. **Purchase consideration** — therefore `EX_INTEREST`: 98.94, with the 0.5333
   of accrued interest **not** charged.
8. **Cash flows** — −98.94 today, then the 2026-11-23 coupon onward, plus 100
   principal at maturity. The 2026-10-23 coupon is absent.
9. **YTM** — the XIRR of that series, returned as a decimal and stored as a
   percentage.

The same bond valued on 2026-10-05 would be `CUM_INTEREST`: entitled to the
October coupon, so purchase consideration 98.94 + 0.40 (12 accrued days of the
same 30-day period) and the October coupon present in the series.

---

## 8. Conventions and rounding

| Computation | Formula | Rounding |
|---|---|---|
| Coupon amount | `principal × rate ÷ (100 × frequency)` | scale 10, `HALF_UP` |
| Accrued interest | `periodCoupon × accruedDays ÷ couponPeriodDays` | scale 20, `HALF_UP` |
| Amortization installment | `100 × percentage ÷ 100` | scale 10, `HALF_UP` |
| XIRR | Actual/365, Newton + bisection | scale 6, `HALF_UP` |
| Persisted `annualYtm` | `xirrDecimal × 100` | scale 2, `HALF_UP` |

Face value is always 100, so all amounts are per 100 of face. The engine mixes
two day-count conventions — **Actual/Actual** within the coupon period for
accrued interest, and **Actual/365** for XIRR discounting — which is intentional
but worth remembering when reconciling against an external system.

---

## 9. What is not wired up

These are current gaps, not design notes. Each was verified against the code.

**The calculation engine has no HTTP entry point.** `YtmCalculationService` has
no production caller — the `calculate-ytm` endpoint is commented out in
`BondController`, and nothing else invokes it. The pipeline is exercised only by
the 19 test classes under `src/test/.../Modules/Bond/Service`. Consequently the
YTM fields are always `NULL` in the database: `createBond` nulls them and
`updateBond` invalidates them on a price change, and nothing repopulates them.

**Bond guard failures return 500, not 400.** `BondService` and
`AdminBondController` throw `org.apache.coyote.BadRequestException` (Tomcat's),
while `GlobalExceptionHandler` only maps the project's own
`com.click4bonds.app.Modules.Common.Exceptions.BadRequestException`.
`IssuerService` imports the correct one; the two are unrelated types with the
same simple name. So messages such as "Matured bond cannot be updated" and
"Maturity type is required" fall through to the catch-all handler and surface as
`500 INTERNAL_SERVER_ERROR`.

**`reserveQuantity` and `findByIdForUpdate` are uncalled.** Inventory can be
configured and restocked through the admin API, but no code path decrements it,
so no bond can be oversold *or sold at all* yet. The reservation step is the
missing caller.

**Three statuses are unreachable.** `SOLD_OUT`, `MATURED` and `DRAFT`-after-create
matter to the guards, but nothing sets `SOLD_OUT` or `MATURED` — a matured bond
is never transitioned, so the `MATURED` guards only fire if a row is edited by
hand.

**`MaturityType` is write-only.** It is validated on create and activate, then
never read: neither `MaturityDescriptionParser` nor `PrincipalRepaymentService`
consults it. `RANGE` and `UNKNOWN` are never produced by the parser. How a bond
amortizes is decided entirely by the text in `maturityDescription`.

**`AT_MATURITY` cannot be calculated.** The enum value exists and
`AccruedInterestService` treats it as "no accrual", but
`CouponCalculationServiceImpl.frequencyDivisor` throws
`Unsupported coupon frequency: AT_MATURITY`. A bond created with that frequency
therefore fails the moment a cash-flow series is requested.

**`CouponEventResolver` is an unused seam.** It produces one `CouponEvent` per
future coupon date with no entitlement dates, and nothing calls it — the
cash-flow service uses `CouponDateGenerator`, `RecordDateParser` and
`CouponEntitlementService` directly. `CouponEvent`'s `exCouponDate` is never
populated by anything.

**`getActiveBonds` has no caller.** A public `BondService` method that nothing
invokes; the public list endpoint applies no status filter, so `DRAFT`,
`SUSPENDED` and `CANCELLED` bonds are all visible to customers via
`GET /api/bonds`.

**Dead code is retained.** Four source files open with a full commented-out
earlier implementation, and `BondRepository` carries two commented-out
`searchBonds` variants. The commented-out `AccruedInterestServiceImpl` used a
fixed 365-day accrual basis, which the live class replaced with Actual/Actual —
so the old text is actively misleading if read as current.
