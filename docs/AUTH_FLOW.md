# Authentication Flow — Phone Number + OTP

Complete reference for the authentication system that replaced Clerk.

- **Status:** backend implemented; one database migration outstanding
- **Branch:** `auth`
- **Last updated:** 2026-09-22

---

## Table of contents

1. [Overview](#1-overview)
2. [Architecture](#2-architecture)
3. [The three credentials](#3-the-three-credentials)
4. [Sign-in flow](#4-sign-in-flow)
5. [Session lifecycle](#5-session-lifecycle)
6. [Endpoints](#6-endpoints)
7. [Error codes](#7-error-codes)
8. [Authorization and roles](#8-authorization-and-roles)
9. [Frontend integration](#9-frontend-integration)
10. [Backend changes](#10-backend-changes)
11. [Configuration](#11-configuration)
12. [Security properties](#12-security-properties)
13. [Database migration](#13-database-migration)
14. [Tests](#14-tests)
15. [Open items](#15-open-items)

---

## 1. Overview

Clerk has been removed completely. There is no external identity provider any more — no issuer, no
JWKS endpoint, no webhook, no second system holding an authoritative copy of a user.

Sign-in is now:

```
phone number  →  OTP by SMS  →  verify  →  find or create user
                                       →  Redis session + access JWT
```

Tokens are signed with a key this service owns and verified by this service alone.

### What did **not** change

- The domain model. No entity was redesigned.
- Every document identifier — bond, order, holding, inquiry, ISIN. Nothing was renamed or regenerated.
- The controller contract. `@AuthenticationPrincipal Jwt jwt` and `jwt.getSubject()` still work exactly
  as written throughout the codebase.

### What `jwt.getSubject()` returns

```
JWT claim          →  sub = "550e8400-e29b-41d4-a716-446655440000"   (= User.id)
Controller         →  String userId = jwt.getSubject();
Service layer      →  UUID userId = UUID.fromString(jwt.getSubject());
```

`User.id` is the canonical identity everywhere: in tokens, in customer relationships, in the
`created_by` audit reference. There is no second identifier column.

---

## 2. Architecture

```mermaid
flowchart TD
    Client["Frontend"]

    subgraph Auth["/auth — public"]
        Send["POST /auth/phone/send-otp"]
        Verify["POST /auth/phone/verify-otp"]
        Refresh["POST /auth/refresh"]
        Logout["POST /auth/logout"]
    end

    OTP[("Redis<br/>auth:otp:sms:{phone}<br/>HMAC hash · 5 min")]
    Session[("Redis<br/>auth:session:{id}<br/>30 days")]
    DB[("PostgreSQL<br/>users")]
    SMS["SMS gateway"]
    Filter["JwtAuthenticationFilter"]
    Context["SecurityContext<br/>principal = Jwt"]

    Client -->|"{ phone }"| Send
    Send --> OTP
    Send --> SMS
    SMS -->|"6-digit code"| Client

    Client -->|"{ phone, otp }"| Verify
    Verify -->|"consume"| OTP
    Verify --> DB
    Verify --> Session
    Verify -->|"access JWT + Set-Cookie"| Client

    Client -->|"cookie"| Refresh
    Refresh --> Session
    Refresh -->|"new access JWT"| Client

    Client -->|"cookie"| Logout
    Logout -->|"delete"| Session

    Client -->|"Authorization: Bearer"| Filter
    Filter --> Context
    Context --> Protected["Protected endpoints<br/>@AuthenticationPrincipal Jwt"]
```

---

## 3. The three credentials

| Credential | Lifetime | Stored in | Attached by | Used by |
|---|---|---|---|---|
| **OTP** | 5 min | Redis (HMAC-SHA256 hash, never plaintext) | frontend, in the verify body | `/auth/phone/verify-otp` |
| **Access JWT** | 15 min | frontend memory | frontend, explicitly | every protected call |
| **Session id** | 30 days | Redis, behind an `HttpOnly` cookie | the browser, automatically | `/auth/refresh`, `/auth/logout` |

The two lifetimes differ by three orders of magnitude on purpose: a stolen access token is useful for
minutes, a stolen session id until it is revoked.

> **The session id is never returned in a JSON body.** It appears only in the `Set-Cookie` header. If it
> were also in the response, it would end up in JavaScript, in a log, or in browser storage — where it
> would stay replayable for thirty days. That is the entire reason the cookie is `HttpOnly`.

---

## 4. Sign-in flow

```mermaid
sequenceDiagram
    autonumber
    participant C as Client
    participant A as API
    participant R as Redis
    participant S as SMS gateway
    participant D as PostgreSQL

    C->>A: POST /auth/phone/send-otp  { phone }
    Note over A: normalise phone (E.164)
    A->>A: rate limit per client address
    R-->>A: setIfAbsent cooldown key (60 s)
    A->>R: SET auth:otp:sms:{phone} = HMAC(code), TTL 5 min
    A->>S: send code
    A-->>C: 200 { message }  (identical whether or not the account exists)

    C->>A: POST /auth/phone/verify-otp  { phone, otp }
    A->>R: GET + compare HMAC, then DEL (single use)
    A->>D: find by mobile_number, else create
    Note over A: refuse unless status = ACTIVE
    A->>D: record phone as VERIFIED
    A->>R: SET auth:session:{id}  TTL 30 d
    A-->>C: 200 { accessToken, expiresInSeconds, user }<br/>Set-Cookie: session=…; HttpOnly
```

### Step by step

1. **Invalid or missing phone or code fails before anything is consumed.** A malformed number costs no
   rate-limit allowance and sends no SMS.
2. **The code is verified and consumed before the database is touched.** A wrong, expired or exhausted
   code creates no account and changes no state.
3. **The account is found by `mobileNumber` or created** — role `CUSTOMER`, status `ACTIVE`, no email.
4. **A non-`ACTIVE` account is refused** with `403`.
5. **The phone is recorded as `VERIFIED`**, because signing in just proved ownership of it.
6. **A Redis session is created**, and an access token minted for it.

### Onboarding for a phone-first account

An account created this way starts at `EMAIL_VERIFICATION` with its phone already verified. Onboarding
now walks forward past any step the account has already satisfied, so it does not strand on a step whose
only endpoint would answer "already verified".

An account with no email may **claim** one during email verification — mirroring the rule the phone step
already followed, and refused with `409` if another account holds it.

---

## 5. Session lifecycle

```mermaid
stateDiagram-v2
    direction LR
    [*] --> Active: verify-otp creates session (30 d)
    Active --> Active: refresh — identifier rotates, TTL restarts
    Active --> Revoked: logout deletes it
    Active --> Expired: 30 d of inactivity
    Active --> Revoked: account suspended or deleted
    Revoked --> [*]
    Expired --> [*]
```

### Rotation

Refreshing mints a **new** session identifier, so a stolen cookie is only useful until the legitimate
client next refreshes. `createdAt` is carried across — rotating continues the same sign-in, so "signed
in since" does not keep sliding forward, while the expiry does.

### The multi-tab race, and how it is avoided

Two tabs refreshing in the same moment would normally mean the first rotates and the second presents an
identifier that no longer exists — signing that tab out.

A rotating identifier therefore leaves a **60-second grace pointer** behind it:

```mermaid
flowchart LR
    A["tab A<br/>cookie A"] -->|"refresh, first"| Rot["rotate A → B<br/>30 d TTL restarts"]
    Rot --> Out["both responses<br/>carry cookie B"]
    B["tab B<br/>cookie A"] -->|"refresh, before<br/>it sees the new cookie"| Grace["grace: A → B<br/>60 s"] --> Out
```

Two rules stop this becoming a chain of redirects:

1. Rotation happens **only when the presented identifier was itself the current one**. A request that
   arrived by following a pointer extends the session it found instead of minting another identifier.
2. Every response carries the current identifier back, so the lagging tab is corrected on the spot.

Both tabs converge on one identifier within a single refresh each. Neither is signed out.

### Sign-out

`POST /auth/logout` revokes **only the presented session**. Other devices stay signed in — which is what
signing out on one device is expected to mean. There is deliberately no "sign out everywhere" endpoint;
if the product needs one it should be added separately rather than folded into normal logout.

---

## 6. Endpoints

| Method | Path | Auth | Purpose |
|---|---|---|---|
| `POST` | `/auth/phone/send-otp` | none | Send a sign-in code by SMS |
| `POST` | `/auth/phone/verify-otp` | none | Redeem the code, sign in |
| `GET` | `/auth/me` | **bearer token** | The signed-in user's full profile |
| `POST` | `/auth/refresh` | cookie | New access token from the session |
| `POST` | `/auth/logout` | cookie | Revoke the session |

> `/auth/**` is public, with **one exception**: `GET /auth/me` requires an access token and sits outside
> that rule. It is declared ahead of the public matcher in `SecurityConfig`, because matchers are
> evaluated in order and the first match wins. A missing or bad token gives the usual
> `401` + `WWW-Authenticate: Bearer`.

### 6.1 `POST /auth/phone/send-otp`

**Request**

```http
POST /auth/phone/send-otp
Content-Type: application/json

{ "phone": "+919876543210" }
```

`phone` is validated as `^\+?[0-9][0-9\s\-().]{6,24}$`, then normalised server-side — spaces, dashes,
dots and brackets are dropped, so `+91 98765-43210` and `+919876543210` are the same number.

**Response — 200**

```json
{ "message": "If the number can be used to sign in, a verification code has been sent." }
```

> **This response is identical whether or not the number is registered**, and a code is issued either
> way. Anything more specific would let an unauthenticated caller ask who has an account. The frontend
> must not branch on it and must not show a "user not found" state — there is no such signal.

**Failure modes**

| Status | Code | Cause |
|---|---|---|
| 400 | `VALIDATION_ERROR` | Malformed phone number |
| 429 | `OTP_RESEND_COOLDOWN` | Code requested again too soon for this number |
| 429 | `OTP_RATE_LIMITED` | Too many requests from this client address |
| 500 | `INTERNAL_SERVER_ERROR` | SMS provider unreachable (see [Open items](#15-open-items)) |

**Limits.** A 60-second resend cooldown per phone number, and 10 requests per 15 minutes per client
address. The cooldown stops a repeat for one number; the address limit is what stops a sweep across many
— and it is also what bounds the SMS bill, since this endpoint behaves identically for every number and
can be pointed at an arbitrary one.

### 6.2 `POST /auth/phone/verify-otp`

**Request**

```http
POST /auth/phone/verify-otp
Content-Type: application/json

{ "phone": "+919876543210", "otp": "483920" }
```

**Response — 200**

```http
Set-Cookie: session=<43-char base64url>; Path=/; HttpOnly; SameSite=Lax; Max-Age=2592000
```

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "expiresInSeconds": 900,
  "userId": "550e8400-e29b-41d4-a716-446655440000"
}
```

In production the cookie is `__Host-session=<…>; Path=/; HttpOnly; Secure; SameSite=Lax`.

> **Four fields, and that is the whole body.** Sign-in returns the wherewithal to make requests plus the
> identifier to key local state on — no profile, and no verification record. The full profile lives at
> [`GET /auth/me`](#64-get-authme), which a client calls when it has a screen to fill. Nothing here
> carries the session identifier either; that is in the cookie and nowhere else.

**Failure modes**

| Status | Code | Cause |
|---|---|---|
| 400 | `VALIDATION_ERROR` | Malformed body |
| 400 | `BAD_REQUEST` | Code not digits, or wrong length |
| 400 | `INVALID_OTP` | Wrong, expired, unknown, or already redeemed — **one message for all four** |
| 403 | `FORBIDDEN` | Account not `ACTIVE` |
| 429 | `OTP_MAX_ATTEMPTS_EXCEEDED` | Too many wrong guesses; the code was destroyed |

### 6.3 `POST /auth/refresh`

```http
POST /auth/refresh
```

No body. The browser sends the session cookie automatically — this endpoint authenticates **by cookie**,
not by access token.

**Response — 200**

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "expiresInSeconds": 900,
  "userId": "550e8400-e29b-41d4-a716-446655440000"
}
```

The same four fields as verify-otp — no profile — plus a new `Set-Cookie` carrying the rotated
identifier.

**Failure modes**

| Status | Code | Cause |
|---|---|---|
| 401 | `INVALID_SESSION` | No session, or it is expired, revoked, or rotated out |
| 403 | `FORBIDDEN` | Account suspended or deleted — **and the session is destroyed** |

The account is re-checked on every refresh rather than trusted from a session created days ago. When the
account is no longer allowed to authenticate, the session is revoked at the same time, so the refusal is
not something the caller can retry past.

### 6.4 `GET /auth/me`

The signed-in user's full profile. Requires an access token — this is the one endpoint under `/auth`
that does.

```http
GET /auth/me
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

**Response — 200, while KYC is outstanding**

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "email": null,
  "mobileNumber": "+919876543210",
  "firstName": "Asha",
  "lastName": "Menon",
  "profileImage": "https://example.com/a.png",
  "onboardingStep": "PAN_VERIFICATION",
  "role": "CUSTOMER",
  "status": "ACTIVE",
  "createdAt": "2026-09-22T06:30:00Z",
  "updatedAt": "2026-09-22T06:30:00Z",
  "isKycCompleted": false,
  "verification": {
    "id": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
    "emailStatus": "VERIFIED",
    "phoneStatus": "VERIFIED",
    "panStatus": "NOT_STARTED",
    "bankAccountStatus": "NOT_STARTED",
    "createdAt": "2026-09-22T06:30:00Z",
    "updatedAt": "2026-09-22T06:30:00Z"
  }
}
```

**Response — 200, once KYC is complete**

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "email": "asha@example.com",
  "mobileNumber": "+919876543210",
  "firstName": "Asha",
  "lastName": "Menon",
  "profileImage": "https://example.com/a.png",
  "onboardingStep": "COMPLETED",
  "role": "CUSTOMER",
  "status": "ACTIVE",
  "createdAt": "2026-09-22T06:30:00Z",
  "updatedAt": "2026-09-22T06:30:00Z",
  "isKycCompleted": true
}
```

> **`verification` is absent, not `null`, when `isKycCompleted` is `true`.** The key is omitted from the
> JSON entirely. Every channel in it reads `VERIFIED` by that point, so it has nothing left to say.
> A client must therefore test for the key's presence — `'verification' in profile`, or an optional
> property — rather than for null.

> **`email` stays present even when it is `null`.** The omission applies to `verification` alone. A
> phone-first account has no address yet, and the key is how a client tells "no address yet" from "this
> API does not return addresses".

**Failure modes**

| Status | Code | Cause |
|---|---|---|
| 401 | `INVALID_SESSION` / — | No token, or it is invalid, expired, or signed with another key |

The profile is read fresh from the database rather than projected from the token, so a name changed or a
step completed a moment ago is visible without waiting for the token to expire. The response always
includes: `id`, `email`, `mobileNumber`, `firstName`, `lastName`, `profileImage`, `onboardingStep`,
`role`, `status`, `createdAt`, `updatedAt`, `isKycCompleted`.

### 6.5 `POST /auth/logout`

```http
POST /auth/logout

204 No Content
Set-Cookie: session=; Path=/; HttpOnly; SameSite=Lax; Max-Age=0
```

Idempotent. With no session, with an expired one, or called twice, it still answers `204` and still
clears the cookie. The cookie is cleared even when there was no session, so a stale cookie from an
already-expired session is not left behind in the browser.

### 6.6 Public endpoints

Unchanged, and asserted by tests:

- `GET /api/bonds`, `GET /api/bonds/**`
- `POST /api/v1/contact-inquiries`
- `/actuator/health`, `/actuator/health/**`
- `/swagger-ui/**`, `/swagger-ui.html`, `/v3/api-docs/**`
- `/public/**`, `/error`
- `/auth/**` (how a caller obtains a token in the first place)

Everything else requires authentication and answers **401 with a `WWW-Authenticate: Bearer` challenge**
when the token is missing or bad.

> A stale token on a **public** endpoint is ignored rather than rejected — the request is served as
> anonymous. The public bond list does not start refusing callers whose token happens to have expired.

---

## 7. Error codes

Every error body is `{ "code": "...", "message": "..." }`.

| Status | Code | Means | Frontend should |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | Body failed validation | Show field errors from `message` |
| 400 | `BAD_REQUEST` | Malformed phone, or code of the wrong shape | Show message |
| 400 | `INVALID_OTP` | Wrong / expired / unknown / already redeemed | Show message, keep the OTP field open |
| 401 | `INVALID_SESSION` | No session, or expired / revoked | Clear local auth state, return to sign-in |
| 403 | `FORBIDDEN` | Account not `ACTIVE`, or a resource that is not yours | Show message; do not retry |
| 409 | `CONFLICT` | Email or phone already owned by another account | Show message |
| 429 | `OTP_RESEND_COOLDOWN` | Code requested again too soon | Disable resend, count down ~60 s |
| 429 | `OTP_MAX_ATTEMPTS_EXCEEDED` | Too many wrong guesses; code destroyed | Return to the send step |
| 429 | `OTP_RATE_LIMITED` | Too many requests from this client address | Show message, back off |
| 500 | `INTERNAL_SERVER_ERROR` | Unexpected — includes SMS provider outage | Generic retry |

> **A `401` on an ordinary API call means refresh, not signed out.** The access token most likely just
> expired. Try `/auth/refresh` once, then retry the original request. Only if refresh itself answers
> `401` is the user actually signed out.

---

## 8. Authorization and roles

The token carries the user's `UserRole` name, and it becomes a Spring Security authority by the existing
convention:

| Role claim | Authority |
|---|---|
| `CUSTOMER` | `ROLE_CUSTOMER` |
| `ADMIN` | `ROLE_ADMIN` |
| `EMPLOYEE` | `ROLE_EMPLOYEE` |

The converter also attaches `FACTOR_BEARER` (Spring Security's authentication-factor model).
`hasRole('ADMIN')` matches on the `ROLE_` authority alone, so the extra entry does not affect any
existing `@PreAuthorize` expression.

**JWT claims — and nothing else:**

```json
{ "sub": "550e8400-e29b-41d4-a716-446655440000",
  "role": "CUSTOMER",
  "iat": "2026-09-22T06:30:00Z",
  "exp": "2026-09-22T06:45:00Z" }
```

A token is readable by anyone holding it, so only the identifier and the role travel in one. Not the
email, not the phone number, not the name. A test asserts this exact claim set.

> **Known gap, pre-existing:** `@EnableMethodSecurity` is absent from `SecurityConfig`, so every
> `@PreAuthorize` in the project is currently inert — any authenticated user can call
> `/api/admin/**`. This was true before the migration too. See [Open items](#15-open-items).

---

## 9. Frontend integration

### Changes required

| # | Change | Why |
|---|---|---|
| 1 | Remove the Clerk SDK and its provider wrapper | No Clerk tokens are accepted any more |
| 2 | Add phone + OTP screens calling the two `/auth/phone/*` endpoints | Replaces the Clerk sign-in UI |
| 3 | Hold the access token in memory, attach `Authorization: Bearer` | It is the only credential the API accepts |
| 4 | `credentials: 'include'` (fetch) or `withCredentials: true` (axios) | Without it the cookie is never sent and refresh always 401s |
| 5 | Refresh on `401`, ideally earlier using `expiresInSeconds` | A 15-minute token will expire mid-session |
| 6 | Read the profile from `user.userId`, then fetch `/auth/me` when a screen needs it | Sign-in and refresh return four fields; they no longer carry a profile |
| 7 | Drop `clerkUserId`; tolerate `email: null` | `clerkUserId` was removed from three DTOs |

> **Do not put the access token in `localStorage`.** Keep it in memory — a module variable or React
> context. Persisting it across reloads means persisting it where any script can read it, and the design
> already went to the trouble of making the long-lived credential unreadable to scripts. On a cold load,
> call `/auth/refresh` to recover the session rather than reading a stored token.

### Where the profile comes from now

Sign-in and refresh answer with four fields — `accessToken`, `tokenType`, `expiresInSeconds`, `userId`
— and nothing else. The nested `user` object and its nested `verification` object are gone from both.

Anything that needs a name, a role, an onboarding step, or a verification channel calls
`GET /auth/me`:

```js
// After sign-in: you have an identity, not a profile.
const { accessToken, userId } = await res.json();

// When a screen actually needs the profile.
const me = await api("/auth/me").then(r => r.json());
// → { id, email, mobileNumber, firstName, lastName, profileImage,
//     onboardingStep, role, status, createdAt, updatedAt, isKycCompleted }

// verification is present only while KYC is outstanding:
if (me.isKycCompleted) {
  // no me.verification key at all — nothing left to chase
  showDashboard(me);
} else {
  routeToOnboardingStep(me.onboardingStep, me.verification);
}
```

`userId` from the auth response is the same value as `id` on the profile, so a client can key its own
store on it immediately after sign-in without a second call.

> **Test for the `verification` key, not for null.** When `isKycCompleted` is `true` the key is omitted
> from the JSON entirely — so `'verification' in me`, or an optional property, is the check. `me.verification
> === null` is never true, because the key is not there to be null.
>
> **`email` is different: it stays present when null.** A phone-first account has no address until the
> email step supplies one, and the key is how a client tells "no address yet" from "this API does not
> return addresses".

The profile is read fresh from the database on every call, so a name changed or a step completed a
moment ago shows up without waiting for the token to expire. It is cheap enough to call on each
navigation that needs it; caching it across a session risks showing a stale onboarding step.

### API client

```js
// One place that knows about the token, so nothing else has to.
let accessToken = null;
let inFlightRefresh = null;

export async function api(path, options = {}) {
  const res = await send(path, options);

  if (res.status !== 401) return res;

  // Expired access token. Refresh once, then retry the original request.
  // The shared promise collapses N concurrent 401s into one refresh — the
  // backend rotates the session, so parallel refreshes would race.
  inFlightRefresh ??= refresh().finally(() => { inFlightRefresh = null; });

  if (await inFlightRefresh) return send(path, options);
  throw new Error("signed out");
}

function send(path, options) {
  return fetch(`${API_BASE}${path}`, {
    ...options,
    credentials: "include",                       // needed for the cookie
    headers: {
      "Content-Type": "application/json",
      ...(accessToken && { Authorization: `Bearer ${accessToken}` }),
      ...options.headers,
    },
  });
}

export async function refresh() {
  const res = await fetch(`${API_BASE}/auth/refresh`, {
    method: "POST",
    credentials: "include",                       // this is what authenticates it
  });

  if (!res.ok) { accessToken = null; return false; }

  const body = await res.json();
  accessToken = body.accessToken;
  userId = body.userId;                           // identity only — no profile
  return true;
}
```

### Screen flows

**Sign in**

1. Collect the phone number. Send it as typed — the server normalises separators itself.
2. `POST /auth/phone/send-otp`. Show the generic message verbatim; do not imply whether the account
   exists.
3. Show the code field. Start a ~60 s resend timer.
4. `POST /auth/phone/verify-otp`. Store `accessToken` and `userId` in memory, then call `/auth/me` to
   get `onboardingStep` and route on it. Sign-in itself no longer tells you where the user is in
   onboarding.

**Cold load**

1. No token in memory — call `/auth/refresh` once.
2. Success → signed in. `accessToken` and `userId` come back; fetch `/auth/me` if the first screen needs
   the profile.
3. `401` → genuinely signed out; show the phone screen.
4. Never render a loading state that can hang: this call either resolves or 401s.

**Sign out**

1. `POST /auth/logout` with credentials included.
2. Drop the in-memory token regardless of the response — the endpoint is idempotent and always clears
   the cookie. Clear any cached profile too.

**Onboarding.** The existing `/api/users/verification/**` endpoints are unchanged and still take the
user from the token subject. An account with no email may now claim one during email verification, which
it could not before.

### CORS

Allowed origins are explicit — `clickforbonds.com`, `www.clickforbonds.com`, `localhost:3000` — with
credentials enabled. The frontend and API share a registrable domain, which is what lets the session
cookie use `SameSite=Lax` and still be sent.

> If the frontend ever moves to a genuinely cross-site host, the cookie needs `SameSite=None; Secure`
> and the refresh/logout endpoints need real CSRF tokens. That is not a config tweak — it is a security
> change, because `SameSite` is the only thing currently protecting those two cookie-bearing endpoints
> against CSRF.

---

## 10. Backend changes

### Added — 12 classes + 1 migration

| File | Role |
|---|---|
| `Modules/Auth/Controller/AuthController.java` | The four endpoints |
| `Modules/Auth/Service/AuthService.java` | OTP → user → session orchestration |
| `Modules/Auth/Service/AuthJwtService.java` | Signs and verifies our tokens |
| `Modules/Auth/Service/AuthSessionService.java` | Redis sessions, rotation, grace pointer |
| `Modules/Auth/Service/AuthCookieService.java` | Cookie attributes; refuses unsafe config at startup |
| `Modules/Auth/Service/AuthKeyFactory.java` | The `auth:*` Redis namespace |
| `Modules/Auth/Filter/JwtAuthenticationFilter.java` | Token → security context |
| `Modules/Auth/Config/AuthProperties.java` | Every tunable; nothing hard-coded |
| `Modules/Auth/Model/AuthSession.java` | What a session holds |
| `Modules/Auth/Dto/AuthResponse.java` | Login/refresh response |
| `Modules/Auth/Exception/InvalidSessionException.java` | 401 |
| `Modules/Auth/Exception/OtpRateLimitedException.java` | 429 |
| `resources/db/migration/V1__replace_clerk_identity_with_user_id.sql` | The migration |

### Removed — 9 files, all Clerk-only

`WebhookController`, `WebhookService`, `ClerkWebhookRequest`, `ClerkService`, `ClerkOutboxProcessor`,
`UserRoleChangedEvent`, `OutboxEvent`, `OutboxEventRepository`, `OutboxStatus`.

`/api/webhooks/clerk` existed only for Clerk user sync (its `user.created/updated/deleted` handlers
called `UserService.createUser/updateUser/softDeleteUser`, which took Clerk-shaped payloads). Its
security exception is gone too. The outbox existed for one reason: pushing a role change to Clerk's
copy. The database is now the only place a role lives, so there is no second system to notify.

> The `outbox_events` **table** is deliberately left in place — dropping a populated table is not a
> migration's call. Nothing writes to it.

`com.svix:svix` was removed from `pom.xml`.

### Changed — production code

| File | Change |
|---|---|
| `Config/SecurityConfig.java` | Resource server out, JWT filter in, `/auth/**` public, explicit 401 entry point, `STATELESS` sessions |
| `Modules/User/Model/User.java` | `clerkUserId` removed, `idx_user_clerk_id` removed, `email` now nullable |
| `Modules/User/Repository/UserRepository.java` | `findByMobileNumber` replaces the Clerk lookups |
| `Modules/User/Service/UserService.java` | `findOrCreateByMobileNumber`; lookups by `User.id` |
| `Modules/User/Service/VerificationService.java` | Looks up by subject-as-id; an unset email can now be claimed; onboarding walks past satisfied steps |
| `Modules/Bond/Models/Bond.java` | `created_by` now joins `users.id` instead of `users.clerk_user_id` |
| `Modules/Bond/Service/BondService.java` | Admin resolved by `UUID` |
| `Modules/Order/Repository/BondOrderRepository.java` | `findByCustomer_Id` |
| `Modules/Order/Service/BondOrderService.java` | `UUID` customer id |
| `Modules/Order/Controller/BondOrderController.java` | `UUID.fromString(jwt.getSubject())` |
| `Modules/Holding/Repository/BondHoldingRepository.java` | `findByCustomer_Id` |
| `Modules/Holding/Service/BondHoldingService.java` | `UUID` customer id |
| `Modules/Holding/Controller/BondHoldingController.java` | `UUID.fromString(jwt.getSubject())` |
| `Modules/Bond/Controller/AdminBondController.java` | `UUID.fromString(jwt.getSubject())` |
| `Modules/Admin/Service/AdminUserService.java` | Outbox write removed from the role change |
| `Modules/Admin/Dto/AdminUserSummaryResponse.java` | `clerkUserId` removed |
| `Modules/Admin/Dto/AdminUserDetailsResponse.java` | `clerkUserId` removed |
| `Modules/User/Dto/UserResponse.java` | `clerkUserId` removed |
| `Modules/Common/Redis/RedisService.java` | Added `increment` for rate limiting |
| `Modules/Common/Redis/RedisServiceImpl.java` | Implementation |
| `Modules/Common/Exceptions/GlobalExceptionHandler.java` | Maps the two new exceptions |
| `Modules/Sms/service/SmsService.java` | Stopped logging the gateway response, which echoes the OTP |
| `Modules/Admin/Controller/AdminUserController.java` | Dropped a now-unused `throws` |
| `Modules/Analytics/Controller/AnalyticsTestController.java` | Stale comment |
| `pom.xml` | `com.svix:svix` out; `spring-security-test` in (test scope) |
| `resources/application{,-dev,-prod}.yaml` | `auth.*` block added; Clerk and JWKS config removed |

> **Why the OAuth2 resource-server dependency is still present.** It is there only for the `Jwt` class,
> `JwtAuthenticationConverter`, `BearerTokenAuthenticationEntryPoint` and the JOSE encoder/decoder.
> Keeping the principal a `Jwt` is precisely what lets every existing controller compile untouched. The
> Clerk **configuration** and the `oauth2ResourceServer(...)` call are gone.

### Reused rather than rebuilt

The existing OTP module was already complete and was used as-is: `OtpService` (HMAC-hashed codes, Redis
TTL, attempt limit, atomic `setIfAbsent` resend cooldown), `SecureOtpGenerator`, `OtpHasher`,
`OtpKeyFactory`, `IdentifierNormalizer`, `OtpProperties`. The existing `SmsService` and the existing
`RedisService`/`RedisTemplate` infrastructure were reused too. No second OTP implementation, no second
Redis configuration.

---

## 11. Configuration

Every value has a working default except the signing key, which is required.

| Environment variable | Default | Notes |
|---|---|---|
| `AUTH_JWT_SECRET` | — | **Required.** ≥32 bytes, used as its own bytes (not decoded). Generate with `openssl rand -base64 48`. Rotating it invalidates every outstanding token. |
| `AUTH_ACCESS_TOKEN_TTL` | `15m` | Access token lifetime |
| `AUTH_SESSION_TTL` | `30d` | Sliding — a device stays signed in while it keeps refreshing |
| `AUTH_COOKIE_NAME` | `__Host-session` | Dev profile overrides to `session`, since plain HTTP cannot satisfy the prefix |
| `AUTH_COOKIE_SECURE` | `true` | Dev overrides to `false` |
| `AUTH_COOKIE_SAME_SITE` | `Lax` | `None` is refused unless `Secure` is also on |
| `AUTH_COOKIE_DOMAIN` | *(empty)* | Leave empty; a `Domain` attribute is incompatible with `__Host-` |
| `AUTH_OTP_MAX_REQUESTS_PER_WINDOW` | `10` | Per client address; `0` disables the check |
| `AUTH_OTP_RATE_LIMIT_WINDOW` | `15m` | Length of that window |

Unchanged and still required: `OTP_HASH_SECRET`, the other `OTP_*` settings, `DB_*`, `REDIS_*`,
`RESEND_*`, `SMS_GATEWAY_UKEY`, `CLICKHOUSE_*`, `KAFKA_*`.

Removed: `JWK_SET_URI`, `CLERK_SECRET_KEY`, `CLERK_WEBHOOK_SECRET` — already stripped from `.env`.

### Misconfiguration is refused at startup, not discovered later

`AuthCookieService` and `AuthJwtService` fail the boot on:

- a missing or short `AUTH_JWT_SECRET`
- `SameSite=None` without `Secure` — which would remove the only CSRF defence the cookie endpoints have
- a `__Host-` cookie name without `Secure`, or with a `Domain` attribute — both of which browsers reject
  silently, producing a login that appears to succeed while the cookie never arrives

> **Note:** `application.yaml`, `application-dev.yaml`, `application-prod.yaml` and `.env` are all
> gitignored and untracked. The `auth.*` block therefore exists only in the working tree and whatever the
> Docker build context contains — the deployment must supply these through real environment variables.
> See [Open items](#15-open-items).

---

## 12. Security properties

| Property | How |
|---|---|
| OTP generation | `SecureRandom`, one draw per digit — leading zeroes preserved |
| OTP at rest | HMAC-SHA256 keyed by `OTP_HASH_SECRET`; the plaintext never reaches Redis or a log |
| OTP expiry | Redis key TTL (5 min), not an application timestamp |
| Attempt limit | 5 failed guesses destroys the code; the remaining TTL is carried across so a wrong guess never extends its life |
| Resend cooldown | 60 s per number, claimed atomically with `setIfAbsent` |
| Request rate limit | 10 per 15 min per client address, atomic counter |
| Session id | 256 bits from `SecureRandom`, base64url — not derived from the token, carries no information |
| Session at rest | JSON in Redis under `auth:session:{id}` with a TTL; the id is the key, never a field, so a dumped value cannot be replayed |
| Cookie | `HttpOnly` + `Secure` + `SameSite=Lax` + `__Host-` prefix + `Path=/` |
| Access token | HMAC-SHA256, 15 min, 30 s clock skew |
| Account enumeration | Send-otp answers identically for known and unknown numbers; all four OTP failure modes share one message; all session failures share one 401 |
| Inactive accounts | Refused at sign-in **and** on every refresh, with the session destroyed |
| JWT contents | `sub`, `role`, `iat`, `exp` only — asserted by a test |
| Logging | No token, no code, no cookie value, no phone number. A rejected token logs only the exception's class, because JWT library errors quote the token they could not read |

---

## 13. Database migration

**File:** `src/main/resources/db/migration/V1__replace_clerk_identity_with_user_id.sql`

Runs as a single transaction — PostgreSQL's DDL is transactional, so a failure leaves the schema exactly
as it was rather than half-migrated.

### What it does, in order

1. **Guards first.** Raises and aborts if any `bonds.created_by` matches no user, or if any
   `clerk_user_id` is not unique. No mapping is invented.
2. **Moves `bonds.created_by`** by adding a `uuid` column, copying from `users.id`, verifying nothing was
   lost, then dropping the old column and renaming. `fk_bonds_created_by` is re-pointed at `users(id)`.
3. **Makes `users.email` nullable** — a phone-first account has no address until the email step. The
   unique index stays: PostgreSQL treats nulls as distinct, so many accounts may await an address while
   no two may share one.
4. **Drops `clerk_user_id`** and its two unique constraints, last, because step 2 needed it.

### Pre-flight check

Expect `0`. If it is not `0`, the migration will refuse to run — and that is correct, because there would
be no safe mapping.

```sql
SELECT count(*) AS orphan_bonds
FROM bonds b
WHERE b.created_by IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM users u WHERE u.clerk_user_id = b.created_by);
```

### Run it

```bash
PGPASSWORD=$(grep '^DB_PASSWORD=' .env | cut -d= -f2-) psql \
  "host=<host> dbname=<db> user=<user> sslmode=require" \
  -v ON_ERROR_STOP=1 \
  -f src/main/resources/db/migration/V1__replace_clerk_identity_with_user_id.sql
```

> **Irreversible.** Dropping `clerk_user_id` destroys the Clerk → `user.id` mapping; once it is gone the
> mapping cannot be recovered from this database. Snapshot first if the Neon branch does not already give
> you one.

### Verified before running

Checked against the live schema via `information_schema`:

- 4 users, 49 bonds, 0 rows with a null `created_by`, **0 orphans**
- exactly one column and one foreign key reference Clerk: `users.clerk_user_id`, `bonds.created_by`
- everything else already referenced `users(id)` and needs **no data migration at all**:
  `bond_orders.customer_id`, `bond_holdings.customer_id`, `deal_confirmations.customer_id`,
  `user_verifications.user_id`, `audit_logs.performed_by_id`

### Verification, after

```sql
-- clerk_user_id is gone
SELECT column_name FROM information_schema.columns
WHERE table_name = 'users' AND column_name = 'clerk_user_id';          -- 0 rows

-- created_by is a uuid and every bond still has a creator
SELECT data_type FROM information_schema.columns
WHERE table_name = 'bonds' AND column_name = 'created_by';             -- uuid

SELECT count(*) FROM bonds WHERE created_by IS NULL;                   -- 0 (was 0)

-- email is nullable
SELECT is_nullable FROM information_schema.columns
WHERE table_name = 'users' AND column_name = 'email';                  -- YES
```

### No migration tool exists

There is no Flyway and no Liquibase in this project, despite the file's `V1__` naming — that name is
there so it can be adopted directly if you add Flyway later (which would then need
`baseline-on-migrate`, since the database already exists). For now, applying it is a manual step.

Dev runs `ddl-auto: update` and prod runs `validate`, which is exactly why the entity change alone was
not enough.

---

## 14. Tests

75 new tests, all passing.

| Suite | Tests | Covers |
|---|---|---|
| `AuthJwtServiceTest` | 12 | Subject is `User.id`; role claim; expiry and clock skew; foreign signature; tampered signature; tampered payload; refuses a weak secret |
| `AuthSessionServiceTest` | 14 | Creation and TTL; expiry; revocation; rotation; the two-tab race; grace expiry; two sessions independent; configurable TTL |
| `AuthCookieServiceTest` | 12 | `HttpOnly`, `Path`, `Max-Age`, `SameSite`; `__Host-` rules; `None`-without-`Secure` refused; clear matches set; read-back |
| `AuthServiceTest` | 22 | Send, verify, invalid, expired, replayed, attempt limit, cooldown, per-address rate limit; account created once; suspended refused; refresh and revoke paths |
| `JwtAuthenticationFilterTest` | 15 | Principal is a `Jwt` and `getSubject()` is the id; every role maps to `ROLE_*`; case-insensitive scheme; every rejection path |
| `AuthEndpointTest` | 13 | Real filter chain: public bond list stays public, protected 401s, refresh/logout cookie behaviour |

Four existing suites were updated to key off `User.id` instead of a Clerk id — behaviour unchanged.

### Full-suite status: 457 tests, 8 failures

| Failures | Status |
|---|---|
| 6 × bond-math suites (`AccruedInterestServiceImplTest`, `BondCashFlowServiceImplTest`, `BondCashFlowXirrInteractionTest`, `YtmCalculationIntegrationTest`) | **Pre-existing.** Verified byte-identical on a pristine `HEAD` worktree, before any of this work. Unrelated to auth. |
| `AuthEndpointTest.theBondListStaysPublic`<br>`AuthEndpointTest.aPublicEndpointIsStillReachableWithAStaleToken` | **Blocked on the migration.** Green once it runs. |

Those two are not padding — they are what caught the breakage. They should stay red until the migration
is applied, rather than being disabled.

---

## 15. Open items

### Blocking

**The migration above must run.** Bond reads return 500 until it does, including the public bond list.

### Pre-existing: every `@PreAuthorize` in this project is inert

`@EnableMethodSecurity` is absent from `SecurityConfig`. It was absent before this migration too, so it
is not a regression — but the effect is that **any authenticated user can currently call
`/api/admin/**`**. The annotations on `AdminUserController`, `AdminBondController`,
`BondHoldingController`, `BondOrderController` and `UserVerificationController` are documentation only.

The fix is one line. It was not applied, because enabling it starts actually enforcing role checks and
would lock out any admin whose `role` column is not `ADMIN` — a behavioural change to choose
deliberately, not discover.

### Pre-existing: the dev profile points at the production database

`application-dev.yaml` sets `datasource.url` to the production Neon instance with `ddl-auto: update`.
Every dev boot and every `@SpringBootTest` issues DDL against production.

During this work those runs attempted the `created_by` type change there and it failed harmlessly; I
confirmed `clerk_user_id` and `email NOT NULL` survived intact.

Practical consequence: **running `mvn test` is not safe against a production database.** Give dev its own
branch or database before it becomes a problem.

### An SMS provider outage surfaces as a generic 500

`SmsService` throws `IllegalStateException("Unable to send OTP right now, please try again")`, but the
catch-all handler turns any `Exception` into `500 INTERNAL_SERVER_ERROR` with a generic message — so that
carefully-worded text never reaches the caller. The email path already has the right shape
(`EmailSendException` → `502 EMAIL_SEND_FAILED`). A typed SMS exception plus a handler would make a
provider outage diagnosable instead of looking like a bug.

### Secrets are in plaintext in `.env`

Live database, Redis, Resend, SMS and Grafana credentials. The file is gitignored so it is not in
history, but it belongs in a real secret store. A fresh `AUTH_JWT_SECRET` was generated into it.

### Stale documentation and a stray directory

`docs/BOND_PLATFORM_BACKEND_DOCUMENTATION.md` still describes Clerk as the identity provider throughout
— lines 9, 14, 22, 47, 60–66, 98–129, 196, 209–247, 270–272, 321, 336, 342, 554, 585–611, 702–715. It
was left unedited because it reads as a point-in-time audit of the repository rather than living docs,
and quietly editing an audit artifact seemed worse than flagging it. It should be superseded by this
document.

`bin/` is a stray tracked duplicate of the project root — 12 files (`pom.xml`, `mvnw`, `dockerfile`, a
copy of that same doc) with no unique content. `git rm -r bin/`.

### Remaining Clerk references

Two places, both deliberate:

1. The migration SQL — it has to read `clerk_user_id` to do the mapping.
2. The stale documentation above.

Zero in Java, zero in configuration, zero in dependencies.

---

## Appendix: Redis keys owned by auth

| Key | Value | TTL |
|---|---|---|
| `auth:session:{sessionId}` | `AuthSession` JSON — `userId`, `createdAt`, `expiresAt`, `lastUsedAt`, `userAgent` | session TTL (30 d) |
| `auth:session:grace:{oldId}` | the replacement session id | 60 s |
| `auth:otpratelimit:{clientAddress}` | request counter | rate-limit window (15 m) |

The OTP module keeps its own separate namespace (`otp:v2:*`), so `auth:*` and `otp:*` can never
collide. Keys are plain strings, so the store stays inspectable from `redis-cli`.

---

## Appendix: request flow summary

```
Frontend
   │  phone
   ▼
POST /auth/phone/send-otp ──▶ Redis OTP (HMAC, 5 min) ──▶ SMS provider
   │
   │  OTP
   ▼
POST /auth/phone/verify-otp ──┬──▶ PostgreSQL   (find or create user)
                              ├──▶ Redis        (session, 30 d)
                              ├──▶ access JWT   (15 min, sub = User.id)
                              └──▶ HttpOnly cookie
   │
   │  Authorization: Bearer <access JWT>
   ▼
JwtAuthenticationFilter ──▶ SecurityContext ──▶ @AuthenticationPrincipal Jwt
                                              └─▶ jwt.getSubject() == User.id

Refresh:  cookie ──▶ POST /auth/refresh ──▶ Redis session ──▶ new access JWT
Logout:   cookie ──▶ POST /auth/logout  ──▶ delete Redis session ──▶ clear cookie
```
