# Party API — Frontend Integration Guide

Everything a frontend needs for client records, customer parties, PAN, bank accounts and demat
accounts.

- **Backend:** `com.click4bonds.app.Modules.Party` + client endpoints in `Modules.Admin`
- **Auth:** same phone/OTP session as the rest of the app — see [`AUTH_FLOW.md`](AUTH_FLOW.md)
- **Migration:** `V19__add_party_architecture.sql` must be applied before this API returns data
- **Last updated:** 2026-10-28

---

## Contents

1. [The model in one picture](#1-the-model-in-one-picture)
2. [Who calls what](#2-who-calls-what)
3. [Auth on every call](#3-auth-on-every-call)
4. [Enums](#4-enums)
5. [Customer API](#5-customer-api)
6. [Admin API — clients](#6-admin-api--clients)
7. [Admin API — parties and staff entry](#7-admin-api--parties-and-staff-entry)
8. [Response types](#8-response-types)
9. [Status rules and what they mean in the UI](#9-status-rules-and-what-they-mean-in-the-ui)
10. [Errors](#10-errors)
11. [End-to-end UI flows](#11-end-to-end-ui-flows)
12. [Rules the frontend must not break](#12-rules-the-frontend-must-not-break)
13. [Current limitations](#13-current-limitations)

---

## 1. The model in one picture

```
User ──1:1── CustomerProfile ──N:1── Party ──1:1── PanDetails
                    │                  │
                    │                  ├──1:N── BankAccount
                    └──1:1── UserVerification
                                       └──1:N── DematAccount

Client ──N:1── Party
```

- A **User** is a login. A **Party** is a business identity. They are separate records.
- A **Client** is a back-office record. It has a Party but is **not** a login.
- A **CustomerProfile** is the account-side record of a customer. It also points at a Party.
- Normally a client and a customer have **different** parties. They share one only after an
  explicit admin link (`POST /api/admin/clients/{id}/link-party`).

---

## 2. Who calls what

| Actor | Role | Base path |
|---|---|---|
| Customer (signed in) | `CUSTOMER` | `/api/users/party` |
| Employee / Admin | `EMPLOYEE` or `ADMIN` + `CLIENT_MANAGEMENT` grant | `/api/admin/clients`, `/api/admin/parties` |

> The admin customer detail view (`GET /api/admin/users/{id}/details`, `USER_MANAGEMENT`) now
> includes `partyId`, so a staff screen can jump straight to that customer's financial records.

The customer's party is **always** derived from the access token. No customer endpoint accepts a
`partyId`. Admin endpoints always take one in the path.

---

## 3. Auth on every call

Same as the rest of the API:

```js
fetch(`${API_BASE}${path}`, {
  credentials: "include",                 // refresh cookie
  headers: {
    "Content-Type": "application/json",
    Authorization: `Bearer ${accessToken}`,
  },
});
```

- Customer endpoints return **403** for a non-customer token.
- Admin endpoints return **403** without `CLIENT_MANAGEMENT` on an employee account.
  An `ADMIN` passes the module check automatically.

---

## 4. Enums

| Enum | Values |
|---|---|
| `PartyType` | `INDIVIDUAL`, `COMPANY`, `LLP`, `PARTNERSHIP`, `TRUST`, `OTHER` |
| `Depository` | `NSDL`, `CDSL` |
| `VerificationStatus` | `NOT_STARTED`, `PENDING`, `VERIFIED`, `REJECTED` |

All enums are serialized as strings.

---

## 5. Customer API

Base: `/api/users/party`

| Method | Path | Body | Returns |
|---|---|---|---|
| GET | `/` | — | `PartyResponse` |
| PUT | `/pan` | `PanDetailsRequest` | `PanDetailsResponse` |
| GET | `/bank-accounts` | — | `BankAccountResponse[]` |
| POST | `/bank-accounts` | `BankAccountCreateRequest` | `201` `BankAccountResponse` |
| PATCH | `/bank-accounts/{accountId}` | `BankAccountUpdateRequest` | `BankAccountResponse` |
| DELETE | `/bank-accounts/{accountId}` | — | `204` |
| GET | `/demat-accounts` | — | `DematAccountResponse[]` |
| POST | `/demat-accounts` | `DematAccountCreateRequest` | `201` `DematAccountResponse` |
| PATCH | `/demat-accounts/{accountId}` | `DematAccountUpdateRequest` | `DematAccountResponse` |
| DELETE | `/demat-accounts/{accountId}` | — | `204` |

### 5.1 Read the party

```http
GET /api/users/party
```

```json
{
  "id": "8f0c2e5a-…",
  "partyType": "INDIVIDUAL",
  "legalName": "Karan Pareek",
  "contactEmail": null,
  "mobileNumber": "+919876543210",
  "address": null,
  "pan": {
    "id": "…",
    "maskedPanNumber": "AA*******C",
    "status": "PENDING",
    "verifiedAt": null,
    "createdAt": "2026-10-28T09:30:00Z",
    "updatedAt": "2026-10-28T09:30:00Z"
  },
  "bankAccounts": [
    {
      "id": "…",
      "accountHolderName": "Karan Pareek",
      "bankName": "HDFC Bank",
      "maskedAccountNumber": "******7890",
      "ifscCode": "HDFC0001234",
      "primaryAccount": true,
      "status": "PENDING",
      "createdAt": "…",
      "updatedAt": "…"
    }
  ],
  "dematAccounts": [
    {
      "id": "…",
      "depository": "CDSL",
      "dpId": "12088700",
      "clientId": "12345678",
      "accountHolderName": "Karan Pareek",
      "status": "PENDING",
      "createdAt": "…",
      "updatedAt": "…"
    }
  ],
  "createdAt": "2026-10-28T09:20:00Z"
}
```

`pan` is a single object or `null`. `bankAccounts` / `dematAccounts` are arrays (possibly empty).

> An account that signed up before the V19 backfill may get **404 `No party is linked to this
> account`**. Treat that as "party setup pending", not a crash.

### 5.2 Submit / update PAN

```http
PUT /api/users/party/pan
{ "panNumber": "AAAPZ1234C" }
```

- Backend uppercases the value and stores it **encrypted**.
- Resulting status is always `PENDING`. The customer cannot mark it verified.
- A PAN that is already `VERIFIED` cannot be resubmitted by the customer → **409**.
- The response is masked. The raw PAN is never returned again.

```json
{
  "id": "…",
  "maskedPanNumber": "AA*******C",
  "status": "PENDING",
  "verifiedAt": null,
  "createdAt": "…",
  "updatedAt": "…"
}
```

### 5.3 Add a bank account

```http
POST /api/users/party/bank-accounts
{
  "accountHolderName": "Karan Pareek",
  "bankName": "HDFC Bank",
  "accountNumber": "1234567890",
  "ifscCode": "HDFC0001234",
  "primaryAccount": true
}
```

Created with status `PENDING`. A party may hold several accounts.

**Primary account rule:** setting `primaryAccount: true` clears whichever account was primary before
it. If you omit the field it defaults to `false`. Zero primaries is valid; more than one is
impossible.

### 5.4 Edit a bank account

`PATCH` — every field is optional; `null`/absent means unchanged.

```http
PATCH /api/users/party/bank-accounts/{accountId}
{ "bankName": "ICICI Bank" }
```

> **Do not send `maskedAccountNumber` back as `accountNumber`.** If the number was not edited, leave
> `accountNumber` out. If it was edited, send the full new number (6–20 digits). Editing an account
> does not change its verification status.

### 5.5 Delete

`DELETE /api/users/party/bank-accounts/{accountId}` → `204`.
`DELETE /api/users/party/demat-accounts/{accountId}` → `204`.

An id belonging to another customer's party returns **404**, not 403.

### 5.6 Demat accounts

```http
POST /api/users/party/demat-accounts
{
  "depository": "NSDL",
  "dpId": "IN300214",
  "clientId": "12345678",
  "accountHolderName": "Karan Pareek"
}
```

`dpId` and `clientId` are alphanumeric, max 20 characters. The backend intentionally does **not**
enforce a depository-specific format, because NSDL/CDSL rules differ for joint and corporate
accounts. Validate hints in the UI only.

`PATCH` takes any subset of `depository`, `dpId`, `clientId`, `accountHolderName`.

---

## 6. Admin API — clients

Base: `/api/admin/clients` — `ADMIN`/`EMPLOYEE` + `CLIENT_MANAGEMENT`.

| Method | Path | Purpose |
|---|---|---|
| POST | `/` | Create client (creates its Party too) |
| GET | `/` | List all clients |
| GET | `/search?name=` | Type-ahead (max 20, needs `DEAL_CREATION` instead) |
| PATCH | `/{id}` | Edit client + its party identity fields |
| DELETE | `/{id}` | Delete client (party and financial records are kept) |
| POST | `/{id}/link-party` | Attach client to an existing, verified party |

### 6.1 Create

```http
POST /api/admin/clients
{
  "name": "Acme Investments Pvt Ltd",
  "contactPerson": "Ravi Kumar",
  "email": "ravi@acme.example",
  "mobileNumber": "+919876543210",
  "address": "Mumbai",
  "partyType": "COMPANY"
}
```

`partyType` is optional; when omitted the backend uses `COMPANY`.

Response (`ClientResponse`) now contains `partyId` — keep it, every financial call needs it:

```json
{
  "id": "…",
  "partyId": "…",
  "name": "Acme Investments Pvt Ltd",
  "contactPerson": "Ravi Kumar",
  "email": "ravi@acme.example",
  "mobileNumber": "+919876543210",
  "address": "Mumbai",
  "createdAt": "…"
}
```

### 6.2 Link a client to an existing party

```http
POST /api/admin/clients/{clientId}/link-party
{ "partyId": "…" }
```

Used when a client record and a registered customer are the same entity. Refused with **409** unless
the party's PAN status is `VERIFIED`. The client's previous party is left untouched.

---

## 7. Admin API — parties and staff entry

Base: `/api/admin/parties` — `ADMIN`/`EMPLOYEE` + `CLIENT_MANAGEMENT`.

| Method | Path | Purpose | Saved status |
|---|---|---|---|
| GET | `/{partyId}` | Party + PAN + bank + demat (masked) | — |
| PUT | `/{partyId}/pan` | Enter/update PAN | **VERIFIED** |
| POST | `/{partyId}/bank-accounts` | Add bank account | **VERIFIED** |
| PATCH | `/{partyId}/bank-accounts/{accountId}` | Edit | unchanged |
| POST | `/{partyId}/demat-accounts` | Add demat account | **VERIFIED** |
| PATCH | `/{partyId}/demat-accounts/{accountId}` | Edit | unchanged |
| PATCH | `/{partyId}/pan/verification` | Set outcome for a customer PAN | body status |
| PATCH | `/{partyId}/bank-accounts/{accountId}/verification` | Set outcome | body status |
| PATCH | `/{partyId}/demat-accounts/{accountId}/verification` | Set outcome | body status |

### 7.1 Staff entry is auto-verified

An employee or admin entering details is recording what they already checked, so these calls save
the record as `VERIFIED` immediately:

```http
PUT /api/admin/parties/{partyId}/pan
{ "panNumber": "AAAPZ1234C" }
```

```json
{ "maskedPanNumber": "AA*******C", "status": "VERIFIED", "verifiedAt": "2026-10-28T10:00:00Z", … }
```

Bank and demat `POST` bodies are identical to the customer ones in §5.3 and §5.6; only the path and
the resulting status differ.

`PATCH` edits do **not** change status: editing a `PENDING` customer submission does not verify it,
and editing a `VERIFIED` staff record keeps it verified.

`PUT /{partyId}/pan` **may replace an already-verified PAN** — that is a staff correction.

### 7.2 Deciding a customer submission

```http
PATCH /api/admin/parties/{partyId}/pan/verification
{ "status": "VERIFIED" }
```

`NOT_STARTED` is rejected with **400**. `VERIFIED` stamps `verifiedAt`; `REJECTED` or `PENDING`
clears it.

Same body for the bank and demat verification paths.

> This does **not** complete KYC and does **not** move `onboardingStep`. KYC remains driven by the
> existing `/api/users/verification` email/phone flow in `AUTH_FLOW.md`.

### 7.3 Read a party

`GET /api/admin/parties/{partyId}` returns the same `PartyResponse` as the customer endpoint, with
masked PAN and bank account number.

---

## 8. Response types

```ts
type PartyType = "INDIVIDUAL" | "COMPANY" | "LLP" | "PARTNERSHIP" | "TRUST" | "OTHER";
type Depository = "NSDL" | "CDSL";
type VerificationStatus = "NOT_STARTED" | "PENDING" | "VERIFIED" | "REJECTED";

interface PanDetailsResponse {
  id: string;
  maskedPanNumber: string;        // "AA*******C" — never the real PAN
  status: VerificationStatus;
  verifiedAt: string | null;      // ISO-8601
  createdAt: string;
  updatedAt: string;
}

interface BankAccountResponse {
  id: string;
  accountHolderName: string;
  bankName: string;
  maskedAccountNumber: string;    // "******7890" — never the real number
  ifscCode: string;
  primaryAccount: boolean;
  status: VerificationStatus;
  createdAt: string;
  updatedAt: string;
}

interface DematAccountResponse {
  id: string;
  depository: Depository;
  dpId: string;
  clientId: string;
  accountHolderName: string;
  status: VerificationStatus;
  createdAt: string;
  updatedAt: string;
}

interface PartyResponse {
  id: string;
  partyType: PartyType;
  legalName: string;
  contactEmail: string | null;
  mobileNumber: string | null;
  address: string | null;
  pan: PanDetailsResponse | null;
  bankAccounts: BankAccountResponse[];
  dematAccounts: DematAccountResponse[];
  createdAt: string;
}

interface ClientResponse {
  id: string;
  partyId: string;                // use this for /api/admin/parties/{partyId}
  name: string;
  contactPerson: string | null;
  email: string | null;
  mobileNumber: string | null;
  address: string | null;
  createdAt: string;
}

// Request bodies
interface PanDetailsRequest        { panNumber: string; }
interface BankAccountCreateRequest {
  accountHolderName: string;
  bankName: string;
  accountNumber: string;          // 6–20 digits
  ifscCode: string;               // e.g. HDFC0001234
  primaryAccount?: boolean;
}
interface BankAccountUpdateRequest {
  accountHolderName?: string;
  bankName?: string;
  accountNumber?: string;
  ifscCode?: string;
  primaryAccount?: boolean;
}
interface DematAccountCreateRequest {
  depository: Depository;
  dpId: string;                   // alphanumeric, max 20
  clientId: string;               // alphanumeric, max 20
  accountHolderName: string;
}
interface DematAccountUpdateRequest {
  depository?: Depository;
  dpId?: string;
  clientId?: string;
  accountHolderName?: string;
}
interface VerificationUpdateRequest { status: VerificationStatus; }
interface LinkPartyRequest          { partyId: string; }
```

Validation rules the backend enforces:

| Field | Rule |
|---|---|
| `panNumber` | 5 letters + 4 digits + 1 letter (case-insensitive) |
| `accountNumber` | 6–20 digits |
| `ifscCode` | `^[A-Z]{4}0[A-Z0-9]{6}$` |
| `dpId`, `clientId` | alphanumeric, max 20 |
| `accountHolderName`, `bankName` | max 255 |
| client `name` | required, max 255 |

---

## 9. Status rules and what they mean in the UI

| Status | Where it comes from | UI meaning |
|---|---|---|
| `NOT_STARTED` | no record / initial entity default | nothing submitted |
| `PENDING` | **customer** submitted | "Under verification" |
| `VERIFIED` | staff entered it, or admin verified it | "Verified" |
| `REJECTED` | admin set it | "Rejected — resubmit" (customer PAN can be resubmitted; a rejected PAN is not `VERIFIED`, so `PUT /pan` works) |

- Customer-entered records start `PENDING`. Never show them as verified.
- Staff-entered records start `VERIFIED`.
- `verifiedAt` is populated only while status is `VERIFIED`.
- A record can be added without being verified; the UI should not gate account creation on `VERIFIED`.

---

## 10. Errors

Every error body is `{ "code": "...", "message": "..." }`.

| HTTP | Code | Typical cause | Frontend |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | body failed validation | show field errors from `message` |
| 400 | `BAD_REQUEST` | e.g. verifying with `NOT_STARTED`, no PAN to verify | show message |
| 401 | `UNAUTHORIZED` | missing/expired access token | refresh, then retry |
| 403 | `FORBIDDEN` | wrong role, missing module grant, non-customer calling customer API | hide the surface |
| 404 | `NOT_FOUND` | party/account missing, or an account id from another party, or "No party is linked to this account" | show empty state |
| 409 | `CONFLICT` | PAN already verified (resubmission), client link to unverified party, duplicate data | show message |
| 500 | `INTERNAL_ERROR` | unexpected | generic error |

---

## 11. End-to-end UI flows

### 11.1 Customer onboarding — PAN, bank, demat

1. `GET /api/users/party` → render existing records.
2. PAN card: if `pan == null` or `pan.status !== "VERIFIED"`, show the form.
   `PUT /api/users/party/pan` → replace the card with the response; badge = `PENDING`.
3. Bank screen: `GET /api/users/party/bank-accounts`. "Add account" → `POST`.
   Show `maskedAccountNumber`, the bank name and the primary star.
   "Make primary" → `PATCH { primaryAccount: true }`, then re-read the list
   (the previous primary has been cleared server-side).
4. Demat screen: same shape with `POST /api/users/party/demat-accounts`.
5. Show a "Pending verification" banner while any status is `PENDING`. No polling required; re-read
   the party when KYC status in `/auth/me` changes or on navigation.

### 11.2 Back office — create a client and fill its details

1. `POST /api/admin/clients` → store `ClientResponse.partyId`.
2. Open the party: `GET /api/admin/parties/{partyId}`.
3. Enter PAN: `PUT /api/admin/parties/{partyId}/pan` → badge `VERIFIED`.
4. Enter bank: `POST /api/admin/parties/{partyId}/bank-accounts` → badge `VERIFIED`.
5. Enter demat: `POST /api/admin/parties/{partyId}/demat-accounts` → badge `VERIFIED`.
6. Correct a mistake: `PATCH` the account or `PUT` the PAN again.

### 11.3 Back office — link an existing client to a registered customer

1. Find the client and its current `partyId`.
2. Find the customer's party (`GET /api/admin/parties/{id}` — PAN must be `VERIFIED`).
3. `POST /api/admin/clients/{clientId}/link-party` with that party id.
4. **409** means the PAN is not verified yet; verify it first.

### 11.4 Back office — decide a customer submission

1. Fetch the customer's party id: `GET /api/admin/users/{userId}/details` → `partyId`
   (requires `USER_MANAGEMENT`). A staff account returns `partyId: null`.
2. Open the party: `GET /api/admin/parties/{partyId}`.
3. Check the record, then:
   - `PATCH /api/admin/parties/{partyId}/pan/verification { "status": "VERIFIED" }`
   - or `REJECTED`.
4. Re-read the party to show the new badges.

---

## 12. Rules the frontend must not break

1. **Masked values are display-only.** Never send `maskedPanNumber` or `maskedAccountNumber` back
   to the API.
2. **Customer calls never take a party id.** One customer cannot address another's party by
   changing a path parameter.
3. **Do not derive "verified" from the presence of a record.** Only `status` says that.
4. **Do not treat `PENDING` as failure.** It is the normal state of a customer submission.
5. **Bank `PATCH` is partial.** Omit fields you did not edit.
6. **A submitted PAN cannot be edited by the customer after verification.** Show it read-only with
   the masked value; if it is rejected, allow resubmission.
7. **Registering a customer never links them to a client.** Only the admin link endpoint does.

---

## 13. Current limitations

- **No admin delete** for bank/demat accounts. Admin can add and edit; the customer can delete.
- **Admin cannot create a client's PAN without a valid PAN format** — same regex as the customer.
- **No PAN/bank/demat verification provider** is integrated. `VERIFIED` means a staff member or an
  admin decision said so; there is no automated check.
- **`UserVerification` channel flags are not updated** when these records are verified. Onboarding
  progress still comes from `/auth/me` and `/api/users/verification`.
- **Existing pre-migration customers** may return 404 for `GET /api/users/party` until V19 backfill
  runs.
