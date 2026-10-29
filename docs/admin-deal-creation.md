# Admin Deal Creation — Frontend Integration Guide

Everything the back office needs to create a deal on a client's behalf:
authentication, the prerequisite lookups, the create call, the response, the
asynchronous confirmation letter, the listing and document download, and every
error the flow can return.

Base URL examples below use `https://api.example.com`; substitute the
environment's API host.

---

## 1. What this flow does

`POST /api/admin/deals` records a deal for a **client** (not a customer account)
and reserves the bond's inventory in the same transaction. The response returns
immediately with the deal and every figure the confirmation letter will print.
The letter itself is generated asynchronously by a Kafka consumer and can be
downloaded later.

The employee also chooses the **side of the trade**:

| `side` | Meaning | Deal reference letter | Type of transaction |
|---|---|---|---|
| `SELL` | We sell the bond to the client | `S` | `Our Sale` |
| `BUY` | We buy the bond from the client | `B` | `Our Purchase` |

A reference looks like `2026/S/OCT/144` (sell) or `2026/B/OCT/145` (buy).

---

## 2. Authentication and permissions

### 2.1 Sign in

Admin/staff accounts sign in with an email OTP. Two calls:

```http
POST /auth/email/send-otp
Content-Type: application/json

{ "email": "employee@example.com" }
```

```http
POST /auth/email/verify-otp
Content-Type: application/json

{ "email": "employee@example.com", "otp": "123456" }
```

`verify-otp` returns:

```json
{
  "accessToken": "eyJ...",
  "tokenType": "Bearer",
  "expiresInSeconds": 900,
  "userId": "3f2b9c1e-..."
}
```

It also sets an **HttpOnly session cookie**. The access token is what you send;
the cookie is only used by `/auth/refresh` and `/auth/logout`.

Send the token on every admin request:

```http
Authorization: Bearer <accessToken>
```

For browser clients, refresh with `POST /auth/refresh` using
`credentials: "include"` (it reads the cookie and returns a new `accessToken` and
a rotated cookie). `GET /auth/me` returns the signed-in profile.

### 2.2 Required role and module grants

Every endpoint in this flow is gated by **role** and, for employees, a **module
grant**:

| Endpoint group | Role | Employee module grant |
|---|---|---|
| `POST /api/admin/deals` | `ADMIN` or `EMPLOYEE` | `DEAL_CREATION` |
| `GET /api/admin/deals` | `ADMIN` or `EMPLOYEE` | `DEAL_CREATION` |
| Deal document download | `ADMIN` or `EMPLOYEE` | `DEAL_CREATION` |
| `GET /api/admin/clients/search` | `ADMIN` or `EMPLOYEE` | `DEAL_CREATION` |
| `GET /api/admin/parties/{partyId}` | `ADMIN` or `EMPLOYEE` | `CLIENT_MANAGEMENT` |
| `GET /api/admin/bonds` | `ADMIN` or `EMPLOYEE` | `BOND_MANAGEMENT` |

`ADMIN` bypasses the module check; `EMPLOYEE` is checked against the stored
grants. A missing grant is a **403**, not a 401.

> **Frontend note:** a pure `DEAL_CREATION` employee can search clients but
> cannot read the party's bank/demat accounts (`CLIENT_MANAGEMENT`) or the admin
> bond list (`BOND_MANAGEMENT`). Either grant those modules too, or have the
> backend expose selected accounts on the create response. The public
> `GET /api/bonds` is open but returns **no bond `id`**, so it cannot feed
> `bondId` on its own.

---

## 3. Request flow — what the frontend calls

### Step 1 — pick a client

```http
GET /api/admin/clients/search?name=acme
Authorization: Bearer <token>
```

Returns `ClientResponse[]`:

```json
[
  {
    "id": "0c7f1c7a-1d2f-4a0e-9b55-6b6f4e0d2a11",
    "partyId": "8e2a5d19-77b4-4e7c-a7d0-7e2ad6f9c001",
    "name": "Acme Ltd",
    "contactPerson": "R. Kumar",
    "email": "ops@acme.example",
    "mobileNumber": "9876543210",
    "address": "1 Marine Drive, Mumbai",
    "createdAt": "2026-09-01T10:15:30Z"
  }
]
```

Use `id` as `clientId` and `partyId` to fetch the client's accounts.

### Step 2 — load the client's accounts (needs `CLIENT_MANAGEMENT`)

```http
GET /api/admin/parties/{partyId}
```

Returns `PartyResponse` with masked PAN, bank accounts and demat accounts:

```json
{
  "id": "8e2a5d19-77b4-4e7c-a7d0-7e2ad6f9c001",
  "partyType": "COMPANY",
  "legalName": "Acme Ltd",
  "contactEmail": "ops@acme.example",
  "mobileNumber": "9876543210",
  "address": "1 Marine Drive, Mumbai",
  "pan": { "id": "...", "maskedPanNumber": "XXXXX1234C", "status": "VERIFIED", "verifiedAt": "...", "createdAt": "...", "updatedAt": "..." },
  "bankAccounts": [
    {
      "id": "b1...",
      "accountHolderName": "Acme Ltd",
      "bankName": "HDFC Bank",
      "branchLocation": "Marine Lines",
      "maskedAccountNumber": "XXXXXXXX0072",
      "ifscCode": "HDFC0001234",
      "primaryAccount": true,
      "status": "VERIFIED",
      "createdAt": "...",
      "updatedAt": "..."
    }
  ],
  "dematAccounts": [
    {
      "id": "d1...",
      "depository": "CDSL",
      "dpId": "12058200",
      "clientId": "00025221",
      "accountHolderName": "Acme Ltd",
      "status": "VERIFIED",
      "createdAt": "...",
      "updatedAt": "..."
    }
  ],
  "createdAt": "..."
}
```

`bankAccounts[].id` → `bankAccountId`; `dematAccounts[].id` → `dematAccountId`.

### Step 3 — pick a bond

Preferred (needs `BOND_MANAGEMENT`):

```http
GET /api/admin/bonds?search=INE123&page=0&size=20&sort=createdAt,desc
```

Returns a `Page<AdminBondDetailResponse>`. The fields the form needs are `id`
(the UUID for `bondId`), `isin`, `name`, `price`, `lotSize`, `quantum`, `rating`
and `status`.

A `Lot size` / `quantum` the deal can use require `status = ACTIVE`, a numeric
`lotSize` greater than 0, and a positive `quantum`.

### Step 4 — create the deal

```http
POST /api/admin/deals
Authorization: Bearer <token>
Content-Type: application/json
```

Body:

```json
{
  "clientId": "0c7f1c7a-1d2f-4e7c-9b55-6b6f4e0d2a11",
  "bondId": "5a6b7c8d-1e2f-3a4b-5c6d-7e8f9a0b1c2d",
  "numberOfLots": 5,
  "dealDate": "2026-10-29",
  "sendEmail": true,
  "bankAccountId": "b1...",
  "dematAccountId": "d1...",
  "side": "SELL",
  "modeOfDelivery": "DEMAT",
  "depository": null,
  "settlementNumber": null
}
```

#### Request fields

| Field | Type | Required | Rules |
|---|---|---|---|
| `clientId` | UUID string | yes | A client that exists. |
| `bondId` | UUID string | yes | A bond that is `ACTIVE` and has inventory. |
| `numberOfLots` | integer (> 0) | yes | Total units = `numberOfLots × bond.lotSize`. |
| `dealDate` | `YYYY-MM-DD` | no | Null = today. Fixes the value date (T+0) and the reference's month. |
| `sendEmail` | boolean | no | False when omitted. When true, the generated PDF is emailed to the client's `email`. |
| `bankAccountId` | UUID string | no | Must belong to the client's party. |
| `dematAccountId` | UUID string | no | Must belong to the client's party. |
| `side` | `BUY` \| `SELL` | **yes** | `SELL` = our sale, `BUY` = our purchase. |
| `modeOfDelivery` | `ICCL` \| `DEMAT` \| `NSCCL` | **yes** | Which particulars the letter prints. |
| `depository` | `NSDL` \| `CDSL` | **yes for `BUY` + `DEMAT`** | Ignored for `SELL` and for `ICCL`/`NSCCL`. |
| `settlementNumber` | string ≤ 64 | **yes for `ICCL`/`NSCCL`** | Ignored for `DEMAT`. |

#### Required-field matrix

| `modeOfDelivery` | `settlementNumber` | `depository` |
|---|---|---|
| `DEMAT` + `BUY` | not needed | **required** (`NSDL`/`CDSL`) |
| `DEMAT` + `SELL` | not needed | ignored |
| `ICCL` | **required** | ignored |
| `NSCCL` | **required** | ignored |

#### Full request examples

**Sell, delivery through demat (our bank particulars print):**

```json
{
  "clientId": "...",
  "bondId": "...",
  "numberOfLots": 5,
  "dealDate": "2026-10-29",
  "sendEmail": false,
  "bankAccountId": "...",
  "dematAccountId": "...",
  "side": "SELL",
  "modeOfDelivery": "DEMAT"
}
```

**Buy, delivery through demat (our DP particulars print for the chosen depository):**

```json
{
  "clientId": "...",
  "bondId": "...",
  "numberOfLots": 5,
  "side": "BUY",
  "modeOfDelivery": "DEMAT",
  "depository": "CDSL",
  "dematAccountId": "..."
}
```

**Cleared through ICCL:**

```json
{
  "clientId": "...",
  "bondId": "...",
  "numberOfLots": 5,
  "side": "SELL",
  "modeOfDelivery": "ICCL",
  "settlementNumber": "2610022"
}
```

**Cleared through NSCCL:**

```json
{
  "clientId": "...",
  "bondId": "...",
  "numberOfLots": 5,
  "side": "BUY",
  "modeOfDelivery": "NSCCL",
  "settlementNumber": "NSE-2026-0042"
}
```

---

## 4. Success response

`201 Created` with `DealConfirmationResponse`:

```json
{
  "dealConfirmationId": "7d1c2b3a-4e5f-4a6b-8c9d-0e1f2a3b4c5d",
  "dealReference": "2026/B/OCT/145",
  "bondId": "5a6b7c8d-1e2f-3a4b-5c6d-7e8f9a0b1c2d",
  "bondName": "TEST BOND 2027",
  "isin": "INE123A07012",
  "quantityPerLot": 100,
  "numberOfLots": 5,
  "totalQuantity": 500,
  "pricePerUnit": 100.0000,
  "totalAmount": 50000.0000,
  "status": "CREATED",
  "createdAt": "2026-10-29T09:31:12.501Z",
  "letterValues": {
    "letterDate": "2026-10-29",
    "dealReference": "2026/B/OCT/145",
    "counterpartyName": "Acme Ltd",
    "counterpartyLine": "Counterparty Name- Acme Ltd",
    "transactionType": "Our Purchase",
    "modeOfDelivery": "DEMAT",
    "dealDate": "2026-10-29",
    "valueDate": "2026-10-29",
    "isin": "INE123A07012",
    "price": 100.00,
    "securityName": "TEST BOND 2027",
    "maturityDate": "2027-08-23",
    "ipDateDescription": "23rd Of Every Month",
    "putCallDescription": null,
    "lastInterestPaymentDate": "2026-10-23",
    "couponRate": 13.70,
    "accruedDays": 6,
    "numberOfBonds": 5,
    "quantum": 500.00,
    "principalAmount": 500.00,
    "accruedInterest": 12.01,
    "considerationAmount": 512.01,
    "stampDuty": 0.00,
    "totalConsideration": 512.01,
    "gsecAccruedDays": null,
    "gsecAccruedInterest": null,
    "gsecConsiderationAmount": null,
    "gsecTds": null,
    "gsecTotalConsiderationAmount": null,
    "semiYtm": null,
    "annualYtm": 13.70,
    "deliveryMode": "DEMAT",
    "settlementNumber": "",
    "side": "BUY",
    "depository": "CDSL"
  }
}
```

Notes:

- `status` starts as `CREATED`. `CONFIRMATION_GENERATED` exists as an extension
  point; document generation does not currently flip it.
- `letterValues` is **null** when the letter's arithmetic cannot be completed
  (for example the bond has no price or no accrued interest). The deal itself is
  still created — this is presentation data, not a precondition.
- `letterValues.counterparty` is deliberately **not serialized** (PAN and bank
  numbers are never returned by this API).
- For a `SELL` the same shape applies with `"side": "SELL"`,
  `"transactionType": "Our Sale"` and `dealReference` containing `/S/`.

### Letter content by combination

| `side` + `modeOfDelivery` | Reference | Transaction type | Our details block |
|---|---|---|---|
| `SELL` + `DEMAT` | `YYYY/S/...` | `Our Sale` | PAN, Branch Location, Our Bank Name, IFSC `HDFC0001347`, Account No. |
| `BUY` + `DEMAT` | `YYYY/B/...` | `Our Purchase` | PAN, DP name `SHRI PARASRAM HOLDINGS PVT.LTD`, DP ID / Client ID of the chosen depository, bank name, account no. |
| `BUY` + `DEMAT` + `NSDL` | `YYYY/B/...` | `Our Purchase` | DP ID `IN302365`, Client ID `10762882` |
| `BUY` + `DEMAT` + `CDSL` | `YYYY/B/...` | `Our Purchase` | DP ID `12058200`, Client ID `00025221` |
| `ICCL` / `NSCCL` | `YYYY/{S or B}/...` | depends on `side` | Clearing-member / bank particulars as before |

For `BUY` + `DEMAT`, the customer block prints the counterparty's bank branch,
bank name, IFSC and account number.

---

## 5. The document step (asynchronous)

The response does **not** contain the letter. After the deal is committed the
backend publishes a request to Kafka; a consumer generates the PDF, stores it,
and — when `sendEmail` was true — emails it to the client's address.

Consequences for the frontend:

1. Do not block the UI waiting for the letter.
2. The document is usually ready within a few seconds, but the create response
   gives no "document ready" flag. The listing response has none either.
3. To fetch the letter, call the download endpoint; a `404` before generation
   completes is normal. Retry with a short backoff.
4. Delivery of the email is independent of the deal: the deal stands even if the
   email fails.

### Download

Preferred form, because references contain slashes:

```http
GET /api/admin/deals/document?reference=2026%2FB%2FOCT%2F145
Authorization: Bearer <token>
```

Alternatives:

```http
GET /api/admin/deals/2026/B/OCT/145/document
GET /api/admin/deals/{dealReference}/document
```

The first alternative is the safest when letting the browser build the URL. Do
**not** percent-encode a slash into a path segment on the frontend: Spring
Security rejects an encoded slash before CORS runs, which the browser reports as
a misleading CORS error. Use the query-parameter form instead.

Success is a binary body (PDF or XLSX) with
`Content-Disposition: attachment; filename="2026/B/OCT/145.pdf"`. Use
`response.blob()` and read the filename from the header.

Errors: `404` when the reference is not one of the employee's deals **or** no
document exists yet; `503 STORAGE_UNAVAILABLE` when the object store is down.

---

## 6. Listing the employee's deals

```http
GET /api/admin/deals?search=acme&page=0&size=20&sort=createdAt,desc
Authorization: Bearer <token>
```

- Scoped to the authenticated employee: they only see deals they created.
- `search` matches reference, client name, bond name and ISIN.
- `sort` accepts `createdAt`, `dealDate`, `dealReference`, `status`,
  `totalAmount`. Default is `createdAt,desc`. Any other property is a `400`.
- Paging is Spring Data (`page`, `size`).

Response is a `Page<AdminDealSummaryResponse>`:

```json
{
  "content": [
    {
      "id": "7d1c2b3a-4e5f-4a6b-8c9d-0e1f2a3b4c5d",
      "dealReference": "2026/B/OCT/145",
      "clientName": "Acme Ltd",
      "bondName": "TEST BOND 2027",
      "isin": "INE123A07012",
      "numberOfLots": 5,
      "totalQuantity": 500,
      "pricePerUnit": 100.0000,
      "totalAmount": 50000.0000,
      "status": "CREATED",
      "dealDate": "2026-10-29",
      "createdAt": "2026-10-29T09:31:12.501Z"
    }
  ],
  "pageable": { "pageNumber": 0, "pageSize": 20, "paged": true, "unpaged": false, "offset": 0, "sort": { "sorted": true, "unsorted": false, "empty": false } },
  "totalPages": 1,
  "totalElements": 1,
  "last": true,
  "size": 20,
  "number": 0,
  "numberOfElements": 1,
  "first": true,
  "empty": false,
  "sort": { "sorted": true, "unsorted": false, "empty": false }
}
```

---

## 7. Error responses

Every application error is a JSON body:

```json
{ "code": "BAD_REQUEST", "message": "Settlement number is required for ICCL" }
```

`422` is never used. Statuses and codes:

| HTTP | `code` | When |
|---|---|---|
| 400 | `VALIDATION_ERROR` | Bean-validation failure; message is `field: message` joined by `, `. |
| 400 | `MALFORMED_BODY` | Body is not valid JSON, wrong shape, or an enum value is not allowed. |
| 400 | `BAD_REQUEST` | A business rule failed (see below), or an invalid `sort` property. |
| 401 | — | Missing/expired/invalid access token (`WWW-Authenticate` header, normally no `ApiError`). |
| 401 | `INVALID_SESSION` | The session cookie could not be resolved (refresh/logout). |
| 403 | `FORBIDDEN` | Role missing, or the employee lacks the module grant. Message: `You do not have permission to perform this action.` |
| 404 | `NOT_FOUND` | Client, bond, account, deal or document not found. |
| 409 | `CONFLICT` | Insufficient inventory, or a database constraint collision. |
| 500 | `INTERNAL_SERVER_ERROR` | Unexpected server fault. |
| 503 | `STORAGE_UNAVAILABLE` | Object store unreachable during download. |

### Error messages from the create call

**Validation (400 `VALIDATION_ERROR`)** — exact messages:

- `Client id is required`
- `Bond id is required`
- `Number of lots is required`
- `Number of lots must be greater than 0`
- `Side is required`
- `Mode of delivery is required`
- `Settlement number must be at most 64 characters`

**Business rules (400 `BAD_REQUEST`):**

- `Settlement number is required for ICCL`
- `Settlement number is required for NSCCL`
- `Depository is required for a DEMAT buy`
- `Bond is not available for purchase: {isin} (status {status})`
- `Inventory has not been set for bond: {isin}`
- `Lot size has not been set for bond: {isin}`
- `Lot size is not a usable quantity for bond: {isin} (lot size {n})`
- `Lot size must be greater than 0 for bond: {isin} (lot size {n})`
- `Total quantity is too large: {lotSize} x {lots}`

**Not found (404 `NOT_FOUND`):**

- `Client not found: {clientId}`
- `Bond not found: {bondId}` (or the bond lookup's message)
- `Demat account not found for this deal: {dematAccountId}`
- `Bank account not found for this deal: {bankAccountId}`
- `The counterparty has no party, so no account can belong to it`

**Conflict (409 `CONFLICT`):**

- `Insufficient quantity available for bond: {isin}` or
  `Insufficient quantity available for bond: {isin} (requested {n}, available {m})`
- `These details conflict with a record that already exists.` (database fallback)

### Status handling on the frontend

- `401` → refresh once via `/auth/refresh`, retry; if it still fails, sign out.
- `403` → show "no permission"; do not retry.
- `404` on download before generation completes → retry later.
- `400` → show `message` next to the form; it is written for the employee.

---

## 8. Frontend checklist

1. Sign in (`/auth/email/verify-otp`), keep `accessToken`, schedule refresh
   before `expiresInSeconds`.
2. Check `/auth/me` shows an admin/staff role; confirm the employee holds
   `DEAL_CREATION` (and `BOND_MANAGEMENT`/`CLIENT_MANAGEMENT` if the form needs
   bonds and accounts).
3. Client search → `clientId` + `partyId`.
4. Party detail → `bankAccountId` / `dematAccountId` (optional).
5. Bond list → `bondId`; show price, lot size, quantum, status.
6. Build the body:
   - always: `clientId`, `bondId`, `numberOfLots`, `side`, `modeOfDelivery`;
   - `settlementNumber` for `ICCL`/`NSCCL`;
   - `depository` for `BUY` + `DEMAT`;
   - `sendEmail` if the client should receive the letter;
   - `bankAccountId`/`dematAccountId` when the letter should carry their
     particulars.
7. `POST /api/admin/deals`; show `dealReference` and `letterValues` from the
   response.
8. Poll or offer a download button; use
   `GET /api/admin/deals/document?reference=...`.
9. List deals with `GET /api/admin/deals`.

### Common mistakes

- Sending `settlementNumber` for `DEMAT` (ignored) or omitting it for
  `ICCL`/`NSCCL` (400).
- Omitting `depository` on a `BUY` + `DEMAT` (400).
- Percent-encoding a slash in a path segment for download (use the query form).
- Assuming the letter exists in the create response — it does not.
- Using the public `GET /api/bonds` row as `bondId` — it has no `id` field.
