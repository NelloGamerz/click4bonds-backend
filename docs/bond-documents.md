# Bond Documents

The files attached to a bond — its information memorandum, term sheet, rating
reports — and its logo. How they are uploaded, where they are stored, who can
read them, and what happens when a logo is replaced.

---

## 1. What is stored

Two things, and only one of them is a table.

**`bond_documents`** holds one row per file: what it is, what it is called, and
where its bytes live. The bytes are **not** here and never will be — they are in
Cloudflare R2, put there directly by the uploader through a presigned URL. This
is what keeps a 20 MB memorandum out of the database and out of every backup of
it.

**`bonds.logo_key`** names the bond's logo. The logo is also stored as a
document row of type `LOGO`; the column repeats its key deliberately.

```
bonds                                bond_documents
+---------------------+              +---------------------------+
| id                  |<-------------| bond_id                   |
| isin                |              | document_type             |
| logo_key  ----------|-----------   | storage_key               |
| ...                 |           |  | file_name                 |
+---------------------+           |  | content_type              |
                                  |  | size_bytes                |
                                  |  | uploaded_by               |
                                  +--+ created_at                |
                                     +---------------------------+
                                        (the LOGO row's storage_key
                                         equals bonds.logo_key)
```

### Why the logo is duplicated

The catalogue shows a logo on every card in a listing. Reading it from
`bond_documents` would mean one more query per row. `bonds.logo_key` makes that
a column on a row the catalogue already has.

The cost of a duplicate is that it can disagree with the record, so it has
**exactly one writer**: `BondDocumentService` is the only thing that sets or
clears it, when a `LOGO` document is confirmed or deleted. That is what makes
the two safe to keep in step.

`logo_key` is the fast path. The document row is the record.

### Why the columns hold keys, not URLs

`storage_key` and `logo_key` hold the object's key within the bucket, e.g.
`bonds/ine123a01011/documents/2026/10/<uuid>.pdf`.

An address would carry the endpoint and bucket that happened to be configured
when the row was written, which pins it to a host that changes and means nothing
if the bucket is ever fronted by a different domain. A key means the same thing
until the object is deleted.

This is where this feature deliberately parts company with deal confirmations,
which store addresses. Those rows are read by a person, so human-readable wins.
These are read by the application and served through this API, so the coupling
buys nothing.

---

## 2. Document types

| Type | What it is |
|---|---|
| `LOGO` | The bond's logo. At most one per bond. Public. Not in the documents listing. |
| `INFORMATION_MEMORANDUM` | The offer document for the issue. |
| `TERM_SHEET` | The headline terms of the issue. |
| `RATING_REPORT` | A rating agency's report. |
| `FINANCIAL_STATEMENT` | Financial statements, audited or otherwise. |
| `OTHER` | Anything else. |

Stored as a name, not an ordinal, so adding a constant cannot silently relabel
historical rows, and so a value read out of the database can be understood
without consulting the enum.

A bond may hold **several documents of the same type** — two rating reports from
two different agencies, a superseded term sheet. A document is appended, never
overwritten. `LOGO` is the single exception, and it is enforced by the service
that owns the invariant rather than by a constraint.

---

## 3. Endpoints

### Admin — `BOND_MANAGEMENT` grant required

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/admin/bonds/{isin}/documents/presign` | Authorise one upload. Returns the signed URL. |
| `POST` | `/api/admin/bonds/{isin}/documents` | Confirm it. Records the document. |
| `GET` | `/api/admin/bonds/{isin}/documents` | List them, newest first. Excludes the logo. |
| `DELETE` | `/api/admin/bonds/{isin}/documents/{documentId}` | Remove it, and the object behind it. |

Guarded by the same rule as the rest of `AdminBondController` — a staff role plus
the `BOND_MANAGEMENT` grant — because attaching an information memorandum to a
bond is editing that bond.

### Read — signed-in caller required

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/bonds/{isin}/documents` | List the bond's documents. |
| `GET` | `/api/bonds/{isin}/documents/{documentId}/download` | Download one, as an attachment. |

### Public

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/bonds/{isin}/logo` | `302` to a short-lived signed URL. |

---

## 4. Uploading, step by step

```
1.  POST /api/admin/bonds/INE123A01011/documents/presign
    { "documentType": "INFORMATION_MEMORANDUM", "contentType": "application/pdf" }

    ->  201 { "storageKey":     "bonds/ine123a01011/documents/2026/10/<uuid>.pdf",
              "uploadUrl":      "https://<account>.r2.cloudflarestorage.com/...&X-Amz-Signature=...",
              "contentType":    "application/pdf",
              "expiresAt":      "2026-10-16T09:15:00Z",
              "maxSize":        26214400 }

2.  PUT <uploadUrl>   with the file as the body and Content-Type: application/pdf
    (straight to R2 — this application is not involved)

3.  POST /api/admin/bonds/INE123A01011/documents
    { "storageKey": "...", "documentType": "INFORMATION_MEMORANDUM",
      "fileName": "ATSPL-IM-2026.pdf", "contentType": "application/pdf" }

    ->  201 { "id": "...", "fileName": "ATSPL-IM-2026.pdf",
              "sizeBytes": 4821134, "downloadUrl": "/api/bonds/INE123A01011/documents/.../download" }
```

**The `Content-Type` header in step 2 must match.** It is signed into the URL, so
R2 rejects a request that sends a different one — which is what turns the
allow-list into something the bucket enforces rather than advice.

**Nothing is listed until step 3.** A file that was authorised but never
confirmed exists in the bucket and nowhere else, so an abandoned upload leaves no
trace in the catalogue.

### What step 3 checks

Four things, before a row is written:

1. **The key was issued for this bond and this kind of file.** It must sit under
   `bonds/<isin>/documents/` or `bonds/<isin>/logo/` — the folder a `LOGO`
   confirmation expects is not the one a document's is. See §6.
2. **The object exists.** A client that reports success without uploading is an
   ordinary outcome — a dropped connection, a closed browser — not an attack.
3. **Its content type is what was authorised**, read from the object store
   rather than taken on trust.
4. **Its size is within `storage.r2.max-upload-size`**, read from the object
   store, because a signed upload cannot be given a limit.

If any of these fails the object is **removed**, so a refused upload leaves
nothing behind. Failure to remove it is logged and does not replace the
rejection — the caller still needs to be told their upload was refused.

---

## 5. Reading: who can see what, and why they differ

The logo is public. The documents are not. That difference is the most
consequential decision in this feature and it is deliberate.

**The logo is public**, served from a path the filter chain leaves open. It
appears on the public catalogue card, and it is fetched by an `<img>` element —
which cannot carry an `Authorization` header. An authenticated logo endpoint
would simply not work for its only caller.

When `storage.r2.public-base-url` is configured, `logoUrl` points straight at
the bucket's custom domain, so this endpoint is not in the path at all. Without
that configuration it is answered as a **redirect** to a short-lived signed
URL, not by streaming the image. Either way the bytes do not pass through this
application — the same file is served to every viewer.

**An information memorandum is not.** It is a document an admin chose to attach,
its contents are reviewed by nothing here, and the safe default for something
uploaded rather than generated is that it is not readable by the public until
someone decides otherwise. Requiring a signed-in caller costs a login, and it can
be relaxed by an explicit rule in `SecurityConfig`. The opposite mistake cannot
be corrected after the fact.

The rule enforcing this lives in `SecurityConfig` and **must stay before the
`/api/bonds/**` permitAll rule**, because matchers are evaluated in order and the
first match wins. Removing it would make every uploaded memorandum public the
moment an admin attached it.

```
.requestMatchers(HttpMethod.GET, "/api/bonds/*/documents", "/api/bonds/*/documents/**")
        .authenticated()

.requestMatchers(HttpMethod.GET, "/api/bonds", "/api/bonds/**")   // logo is covered here
        .permitAll()
```

Downloads are **streamed**, not redirected, unlike the logo. The caller is saving
a named file, and a redirect would offer it whatever the object store calls the
object — a UUID — instead of the name an admin gave it. It would also publish the
bucket's address to anyone who followed it.

`BondResponse.logoUrl` and `BondDetailResponse.logoUrl` carry `null` when the
bond has no logo. When it has one:

- **`storage.r2.public-base-url` is set** — the absolute URL of the object on
  the bucket's custom domain, e.g.
  `https://assets.indiabonds.com/bonds/ine123a01011/logo/...png`. The browser
  fetches it from the edge directly: no API request, no redirect, no signing.
- **It is blank** (local machines, deployments with no public domain) —
  `/api/bonds/{isin}/logo`, which redirects to a freshly signed URL.

Neither form hands out a signed URL in the response itself, because that would
expire while the catalogue card was still on screen. Only the paths an operator
has deliberately published on the custom domain are readable; the bucket stays
private for documents.

---

## 6. The key a client sends back is not trusted

The confirmation takes a `storageKey` from the caller, and that is the one value
there which decides **which object is claimed**. Without a check, a staff member
who can manage one bond could confirm a key belonging to another — and, for a
logo, make it that bond's public image — or attach an object to a bond that never
uploaded it.

So the key must start with the prefix this application would have issued for that
bond and that kind of file:

```
bonds/<isin lower-cased>/documents/...
bonds/<isin lower-cased>/logo/...
```

The **trailing separator is part of the check**. Without it,
`bonds/ine123a01011/documents-old/...` would pass a naive `startsWith` and file a
document outside the folder it was authorised for.

The reported key is also refused if it names the other folder: a document key
cannot be confirmed as a `LOGO`, because that would set the bond's mirror to a
key in the wrong place and make one file both.

The prefix is built from the bond's **stored** ISIN, lower-cased, not from the
path variable. ISINs are case-insensitive, so folding the case means the same
bond files under one folder however the ISIN was spelled in the request, and it
means the key is built from a string this application wrote rather than from
request input.

---

## 7. Replacing and deleting a logo

**Confirming a `LOGO` replaces the bond's previous one.** Its object is deleted
before its row, and `bonds.logo_key` is then set to the new key. The old key is
left on the bond until the new document is saved, so a failure between the two
leaves the bond pointing at a logo that still exists rather than at nothing.

Confirming the **same key twice** — a retried request — does not delete the
object the confirmation is about to describe, which would leave a row pointing
at nothing.

**Deleting a `LOGO` clears `bonds.logo_key`**, so the catalogue stops pointing at
an object that is gone and every card falls back to its placeholder.

Deletion always removes **the object first, then the row**. That order matters:
deleting the row first would leave an object with nothing pointing at it if the
object store then failed — unreachable, unpaid for, and impossible to find
again. In this order the only failure is a row describing a file that is still
there, which a retry fixes.

---

## 8. Schema

Applied by hand — see the notes at the head of
`db/migration/V13__add_bond_documents_and_logo.sql`. **Flyway does not run in
this project**, so that file must be executed before the code is deployed, after
taking a backup.

```
bonds
  + logo_key varchar(512) NULL

bond_documents
  id            uuid        PRIMARY KEY
  bond_id       uuid        NOT NULL REFERENCES bonds(id)
  document_type varchar(40) NOT NULL
  file_name     varchar(255) NOT NULL
  storage_key   varchar(512) NOT NULL UNIQUE
  content_type  varchar(150) NOT NULL
  size_bytes    bigint      NOT NULL
  uploaded_by   uuid        NULL REFERENCES users(id)
  created_at    timestamptz NULL
```

`bond_id` is a plain foreign key with the default `RESTRICT`, not
`ON DELETE CASCADE`. Bonds are cancelled, never deleted, so a cascade would never
fire; what it would do is silently destroy a bond's documents if anyone ever did
run a delete. Failing loudly is the better behaviour.

`uploaded_by` is nullable because a document may outlive the account that filed
it, and removing an account must not delete a bond's information memorandum.

**The one-per-bond logo rule is a service invariant, not a constraint.** A
partial unique index on `(bond_id) WHERE document_type = 'LOGO'` would enforce it
in the schema and is worth adding if `BondDocumentService` ever stops being the
only writer. The verification block in V13 checks the invariant holds.

---

## 9. Configuration

Nothing specific to bond documents. Everything comes from
`storage.r2.*` — see `docs/r2-storage.md` §3.

Two values are worth restating for this feature, because they bound what a
misused upload URL is worth:

- **`max-upload-size`** (default `25MB`) — an information memorandum is
  comfortably inside this; a scanned set of financial statements may not be.
  Raise it deliberately, not reactively.
- **`presigned-upload-ttl`** (default `15m`) — an admin choosing a file and
  sending it from a laptop has to fit inside this window.

A bond logo is an `image/png` or `image/jpeg`, both allowed by default. A
document is normally `application/pdf`. **If either is removed from
`allowed-content-types`, the corresponding upload stops working** — with a
`400 UPLOAD_REJECTED` naming the type, before any URL is signed.

---

## 10. Error responses

| Status | Code | When |
|---|---|---|
| `400` | `UPLOAD_REJECTED` | The type is not allowed, the file is over the limit, or the object is not what was authorised |
| `400` | `BAD_REQUEST` | The key was not issued for this bond |
| `400` | `VALIDATION_ERROR` | A required field of the request is missing or too long |
| `404` | `NOT_FOUND` | No such bond, no such document on that bond, or no logo |
| `503` | `STORAGE_UNAVAILABLE` | The object store could not be reached |

A document belonging to another bond is reported as `404`, indistinguishable
from one that does not exist — the lookup is scoped by bond in the query, so the
endpoint cannot be used to discover which document ids are real.

A bond with no logo also answers `404` on the logo endpoint, identically to a
bond that does not exist, for the same reason.

---

## 11. Tests

`BondDocumentServiceTest` covers the service's own decisions with mocked
repositories and a mocked storage module — no database and no bucket:

- the prefix a document and a logo are filed under, and that it comes from the
  stored ISIN lower-cased;
- every ownership refusal: another bond's key, the wrong folder, and a key that
  is merely a *prefix* of the folder;
- that the recorded size comes from the object store, not the request;
- that a logo replaces its predecessor, mirrors onto the bond, and that
  re-confirming the same key does not delete the object it describes;
- that the listing excludes the logo, and that deleting a logo clears the
  mirror;
- that a document of another bond is not reachable.

Storage's own behaviour — signing, the size check, key derivation — is covered
in `UploadServiceTest` and `R2ObjectStoreTest`; see `docs/r2-storage.md` §7.
