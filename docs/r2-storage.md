# Storage & Upload Module

How files get into Cloudflare R2, who is allowed to put them there, and what
every configuration value means. This is the module every feature that keeps a
file goes through — deal confirmation letters today, bond documents and logos
too.

---

## 1. Architecture

Two layers, and the split between them is the whole design.

```
Business module          BondDocumentService, R2DocumentStorage, ...
      |                    decides WHAT to store and WHERE it belongs
      v
UploadService            the policy layer for client-originated bytes
      |                    validates type and size, derives the key, signs URLs
      v
ObjectStore (interface)  stores bytes and gives them back
      |
      v
R2ObjectStore            Cloudflare R2 over its S3-compatible API
```

**Why two layers, and not one façade.** There are two kinds of bytes in this
application and they must not be governed by the same rules.

*A generated letter* is produced by code in this repository, to a key it
derives, in a format it chose. Nothing about it can be hostile, and it must be
storable whatever format it is in.

*A client's upload* comes from outside. It can be anything, at any size, and the
only thing that knows its type is the client's own claim.

Putting the upload rules inside `ObjectStore` would apply a client-facing
allow-list to the application's own generated documents — so the deal module's
spreadsheet would start being refused by a rule written for somebody's scanned
PDF. So `ObjectStore` writes what it is told, and `UploadService` is where
anything that arrived from outside is judged.

Neither layer knows about deals, bonds or documents. Keys belong to the caller.

### Classes

| Class | Package | Responsibility |
|---|---|---|
| `ObjectStore` | `Modules.Storage.Service` | The interface. Put, get, stat, delete, addresses, presigned URLs. |
| `R2ObjectStore` | `Modules.Storage.Service` | Cloudflare R2, and the three settings that make the AWS client speak it. |
| `UploadService` | `Modules.Storage.Service` | The upload policy. Validates, derives keys, signs URLs, confirms uploads. |
| `StorageProperties` | `Modules.Storage.Config` | `storage.r2.*` — credentials, endpoint, bucket, and the upload policy. |
| `StoredObject` | `Modules.Storage.Model` | What a caller wrote. Echoed from the arguments it passed. |
| `ObjectMetadata` | `Modules.Storage.Model` | What the bucket *has*. Read back, so it can contradict a caller. |
| `StorageException` | `Modules.Storage.Exception` | The store could not serve the request. |
| `UploadRejectedException` | `Modules.Storage.Exception` | The caller asked for something the policy does not allow. |

The distinction between `StoredObject` and `ObjectMetadata` is not cosmetic and
is worth stating once: the first is what a caller *wrote*, the second is what
the store *has*. For an upload that went straight from a client to the bucket,
only the second can be trusted — which is why confirming an upload reads it.

---

## 2. The upload flow

Three steps. Only the first and third touch this application.

```
1. POST .../presign
   client  --------------------------->  UploadService.presignUpload(prefix, contentType)
                                             validates the type against the allow-list
                                             derives <prefix>/<yyyy>/<MM>/<uuid>.<ext>
                                             signs a PUT URL with that content type
   client  <--------------------------  { storageKey, uploadUrl, contentType, expiresAt, maxSize }

2. PUT <uploadUrl>   (straight to R2 — the bytes never reach this application)

3. POST ...            (the confirmation)
   client  --------------------------->  UploadService.confirmUpload(key, contentType)
                                             reads the object back with stat()
                                             checks its size against the limit
                                             deletes it and fails if it is over
   client  <--------------------------  the metadata, to be recorded
```

**Step three is not a formality, and skipping it is not an option.** It is the
only point at which the size limit can be applied, because a signed `PUT` cannot
bind the length of its body — R2 accepts an oversized file and this application
removes it afterwards. It is also the only point at which the application learns
that a file it authorised actually arrived. A document row written at step one
would mean a bond showing a document that is not in the bucket.

### What is trusted where

| Thing | Decided by | Enforced by |
|---|---|---|
| Object key | The server, from the caller's prefix plus a random UUID | Never accepted from a client |
| Content type | The server, from the allow-list | R2 — it is signed into the URL |
| Body size | The client | `confirmUpload`, after the upload, by reading it back |
| File name | The client | Stored for display only; never part of the key |

The size is the one genuinely client-controlled value, and it is therefore the
one checked by reading the result rather than by constraining the request.

### Why keys are derived and not supplied

Two properties come from the same decision. The key is **unguessable** — it ends
in a random UUID — so one caller cannot name another's object to read over or
overwrite it. And it is **deterministic in shape**, grouped by the month it
arrived, so an operator looking for what was uploaded in a given month has
something to list.

Nothing from a client's own filename appears in it. Those are chosen by whoever
is uploading, may contain any character, and their only effect would be to make
the key partly somebody else's to decide — which is exactly what deriving it is
meant to avoid.

---

## 3. Configuration

All under `storage.r2.*`, in `application.yaml`, overridden per environment.
Every value is optional as far as starting up is concerned: a machine with
document generation switched off, or a CI runner, boots with none of it set, and
a missing value is reported by the operation that needed it, naming the
property.

| Property | Env var | Default | Meaning |
|---|---|---|---|
| `endpoint` | `R2_ENDPOINT` | *(blank)* | Account-scoped S3 endpoint, `https://<account-id>.r2.cloudflarestorage.com`. Required to store anything. |
| `access-key-id` | `R2_ACCESS_KEY_ID` | *(blank)* | R2 API token access key. |
| `secret-access-key` | `R2_SECRET_ACCESS_KEY` | *(blank)* | R2 API token secret. |
| `bucket` | `R2_BUCKET` | *(blank)* | The one bucket everything is written to. |
| `region` | `R2_REGION` | `auto` | R2 is region-agnostic; SigV4 signing still needs one. |
| `presigned-upload-ttl` | `R2_PRESIGNED_UPLOAD_TTL` | `15m` | How long an upload URL stays valid. |
| `presigned-download-ttl` | `R2_PRESIGNED_DOWNLOAD_TTL` | `5m` | How long a download URL stays valid. |
| `max-upload-size` | `R2_MAX_UPLOAD_SIZE` | `25MB` | Largest file a client may upload. |
| `allowed-content-types` | — | pdf, xlsx, png, jpeg | MIME types a client may upload. |

Blank credentials fall back to the AWS SDK's own chain — environment variables,
a shared profile, or an instance role — which is how a deployment that cannot
hold keys in its configuration supplies them.

**`endpoint`, `access-key-id`, `secret-access-key` and `bucket` are required in
production**, with no default, so a deployment that cannot store a file refuses
to start rather than failing after a customer has paid. See
`application-prod.yaml`.

### The upload policy

**`allowed-content-types` is an allow-list, and an empty one permits nothing.**
That is failing closed, which is the only safe reading of an absent list for a
control whose job is to keep things out.

It is a **replacement list, not an extension of the defaults**. Setting it
replaces the whole policy.

It applies **only to uploads that arrive from outside the application**. Files
this application generates — the deal confirmation spreadsheet and its PDF — are
written through `ObjectStore` directly and are not subject to it, so removing a
type here does not affect document generation.

A type that is allowed but that `UploadService.EXTENSIONS` cannot name is
refused when a key would be built for it, with a message naming the property.
That is deliberate: a key is what finds an object later, and one with no
extension is an object nobody can open.

**`max-upload-size` is enforced after the upload, not during it.** R2 accepts the
whole file and this application refuses it afterwards, removing the object. The
bytes do cross the network first — this bounds what is *kept*, not what is
*sent*. That ordering is unavoidable with presigned uploads, and it is why
confirming an upload is part of the flow rather than optional.

### Lifetimes

Both are short, because a presigned URL is handed to a browser and anyone
holding one has the access it grants until it expires. Uploads get the longer
window because the person on the other end has to find a file and send it over
whatever connection they have; a download starts the moment the URL is issued.

---

## 4. R2-specific configuration, and why it is load-bearing

Three settings in `R2ObjectStore` make the AWS SDK speak R2 rather than AWS.
Each fails in a way that looks like an authentication problem rather than a
configuration one, so none of them is obvious from the error:

1. **Path-style addressing.** R2 serves objects from the account endpoint with
   the bucket in the path. The client's default moves the bucket into the
   hostname, which produces a name that does not resolve.
2. **Chunked encoding off.** The SDK's default upload uses `aws-chunked`
   transfer encoding; R2 rejects the resulting signature with a `403` that reads
   as though the credentials were wrong.
3. **Checksums `WHEN_REQUIRED`.** From AWS SDK 2.30.0 uploads carry a CRC32
   checksum by default, which R2 rejects outright. Both the request and response
   settings are pinned.

These are asserted by tests on the configuration object itself, because a mocked
client accepts anything and neither is observable from its arguments.

> **Upgrading the AWS SDK is safe; downgrading past 2.30.0 makes two of these
> settings no-ops and breaks every write.**

---

## 5. Error handling

Both failures are answered through `GlobalExceptionHandler`, and the split is
about remedies rather than about where the exception came from.

| Exception | Status | Code | Message |
|---|---|---|---|
| `UploadRejectedException` | `400` | `UPLOAD_REJECTED` | The reason, passed through |
| `StorageException` | `503` | `STORAGE_UNAVAILABLE` | Generic text |

`UploadRejectedException` extends `BadRequestException`, so it is also caught by
anything handling that type; the dedicated handler is declared separately only
so the code is `UPLOAD_REJECTED` rather than the generic `BAD_REQUEST`. Its
message is passed through because it names a type that is not allowed or a size
that is too large — facts about the caller's own request, which is what they
need in order to change it, and which disclose nothing about the bucket.

`StorageException` is answered `503` on the same reasoning as the analytics
handler: the request was well-formed and the application is healthy, but a
dependency is not, and the same call is worth retrying. Its message names the
configuration that is missing and the bucket that could not be reached — detail
an operator needs in the log and a caller has no business seeing — so it is
logged in full and kept out of the response.

**Expired URLs** are not an exception this application raises. An expired
presigned URL is refused by R2, and the client sees a `403` from the bucket
rather than from here. The remedy is always to ask for a new URL; the `expiresAt`
in the presign response is what a client should check before sending a large
file.

---

## 6. Using it from a business module

`UploadService` is what to inject. It does not re-expose reading or deleting —
those are `ObjectStore`'s, and a module that needs them injects the store, as
`R2DocumentStorage` and `BondDocumentService` both do. A pass-through method for
each would make `UploadService` a façade with no policy in it for most of its
surface.

```java
// 1. Authorise
UploadService.PresignedUpload upload =
        uploadService.presignUpload("kyc", request.getContentType());
// hand upload.url() and upload.contentType() to the client

// 2. The client PUTs the file to that URL

// 3. Confirm — this is what applies the size limit
ObjectMetadata metadata =
        uploadService.confirmUpload(request.getStorageKey(), request.getContentType());

// record metadata.size(), metadata.contentType() and the key
```

Whatever a module stores as the object's location, **store the key, not the
address**. An address carries the endpoint and bucket that built it, so it pins
the row to a host that changes and means nothing if the bucket is ever fronted
by a different domain. `ObjectStore.keyFor` accepts both, so a row holding an
address still resolves — that leniency exists for historical data, not as a
recommendation.

**Validate any key a client sends back.** The key decides *which object is
claimed*, so a confirmation that accepts one without checking it belongs to the
caller's own scope lets one caller claim another's file. `BondDocumentService`
does this with a prefix check; see `docs/bond-documents.md`.

---

## 7. Tests

| Test | Covers |
|---|---|
| `StoragePropertiesTest` | That the YAML names reach the fields, and the defaults of the upload policy. |
| `R2ObjectStoreTest` | The path-style/chunked/checksum settings, key validation, the mapping onto S3 calls, addresses, presigned GET and PUT, `stat`. |
| `UploadServiceTest` | The upload policy: type allow-listing, key derivation, prefix safety, configured lifetimes, and every refusal. |

The live-bucket round trip in `R2ObjectStoreTest` is skipped unless `R2_ENDPOINT`,
`R2_BUCKET` and both credentials are in the environment, so CI runs everything
except the one test that needs a bucket.
