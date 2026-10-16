 -- Give a bond files of its own, and a logo.
--
-- WHAT THIS DOES. Adds `bond_documents` — one row per file attached to a bond,
-- holding what the file is, what it is called and where its bytes live — and
-- `bonds.logo_key`, which names the bond's logo so the catalogue can show it
-- without a query per bond.
--
-- The bytes are NOT here and never will be. Uploaded files go to Cloudflare R2
-- through the presigned-upload flow in the Storage module; this table records
-- that a file exists and how to find it. That is what keeps a 20 MB information
-- memorandum out of this database and out of every backup of it.
--
-- WHY A NEW TABLE RATHER THAN COLUMNS ON `bonds`. A bond has several documents
-- of several kinds, and how many is not knowable in advance — two rating
-- reports from two agencies, a superseded term sheet, a logo. Columns would fix
-- the count at whatever number looked plausible on the day, and every type
-- added afterwards would be another nullable column on a table that is read for
-- every catalogue page.
--
-- WHY `logo_key` IS DUPLICATED. The logo is stored as a document of type LOGO,
-- like everything else. `bonds.logo_key` repeats it deliberately: the catalogue
-- card shows a logo for every bond in a listing, and reading it from the
-- document table would mean one more query per row. The two are kept in step by
-- a single writer — BondDocumentService is the only thing that sets or clears
-- the column — so they cannot drift. `logo_key` is the fast path; the document
-- row is the record.
--
-- WHY THE COLUMN NAMES A KEY AND NOT A URL. `storage_key` and `logo_key` hold
-- the object's key within the bucket, e.g.
-- "bonds/ine123a01011/documents/2026/10/<uuid>.pdf". An address would carry the
-- endpoint and bucket that happened to be configured when the row was written,
-- which pins the row to a host that changes and means nothing if the bucket is
-- ever fronted by a different domain. A key means the same thing until the
-- object is deleted, and the application resolves it against whatever is
-- configured now. This is the same reasoning V5 applied to deal confirmations,
-- taken one step further: those rows were left holding addresses because a
-- person reads them; these are read by the application, so they hold keys.
--
-- SAFE TO RUN BY HAND, WHICH IS HOW IT WILL BE RUN. Flyway is declared in the
-- build but Spring Boot 4 moved its auto-configuration into a module this
-- project does not depend on, so `spring.flyway.*` in application-prod.yaml
-- does nothing and nothing in this directory has ever run automatically. The
-- dev schema is maintained by spring.jpa.hibernate.ddl-auto (update in dev,
-- validate in prod). This file follows the same convention as V1-V12: run the
-- statements, then run the verification queries at the bottom.
--
-- Note for whoever reconciles that: two files claim version 5
-- (V5__add_user_demat_verification.sql and V5__document_r2_path.sql), so Flyway
-- cannot be switched on until one of them is renumbered. Not this migration's
-- to fix, and worth knowing before anyone tries.
--
-- NOTHING EXISTING IS TOUCHED. No column is dropped, no row is rewritten, no
-- identifier changes, and `bonds.logo_key` is nullable so every existing bond
-- keeps working and simply reports no logo — which is the truthful state for
-- all of them.
--
-- Applied as a single transaction, like V1-V12. PostgreSQL's DDL is
-- transactional, so a failure leaves the schema exactly as it was.

BEGIN;

-- ---------------------------------------------------------------------------
-- 1. bonds.logo_key
-- ---------------------------------------------------------------------------

-- Nullable, with no default. A bond without a logo is the normal state, not a
-- missing value: nothing has ever recorded a logo for any bond that exists
-- today, and inventing one would be inventing a fact about the company that
-- issued it.
--
-- 512 is sized for "bonds/<isin>/logo/yyyy/MM/<uuid>.<ext>", which is about 60
-- characters, so this is headroom rather than a tight fit. It matches
-- bond_documents.storage_key, and will grow with it if that ever has to.
ALTER TABLE bonds
    ADD COLUMN IF NOT EXISTS logo_key varchar(512);

COMMENT ON COLUMN bonds.logo_key IS
    'Object-store key of this bond''s logo, or NULL when it has none. Mirrored from the bond''s LOGO row in bond_documents, which BondDocumentService is the only writer of.';

-- ---------------------------------------------------------------------------
-- 2. bond_documents
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS bond_documents (
    id            uuid        PRIMARY KEY,
    bond_id       uuid        NOT NULL REFERENCES bonds(id),
    document_type varchar(40) NOT NULL,
    file_name     varchar(255) NOT NULL,
    storage_key   varchar(512) NOT NULL,
    content_type  varchar(150) NOT NULL,
    size_bytes    bigint      NOT NULL,
    uploaded_by   uuid        REFERENCES users(id),
    created_at    timestamp with time zone
);

COMMENT ON TABLE bond_documents IS
    'Files attached to a bond: its information memorandum, term sheet, rating reports and logo. The row records where the object lives; the bytes are in R2.';

COMMENT ON COLUMN bond_documents.storage_key IS
    'Object key within the configured bucket, e.g. bonds/<isin>/documents/yyyy/MM/<uuid>.pdf. Not an address: see the notes at the head of this migration.';

COMMENT ON COLUMN bond_documents.file_name IS
    'The name to offer a person downloading this. Record-keeping, not addressing: the key finds the bytes, this is what they are called.';

COMMENT ON COLUMN bond_documents.document_type IS
    'A BondDocumentType constant name, stored as text to match @Enumerated(EnumType.STRING).';

-- `bond_id` is a plain foreign key with the default RESTRICT, not ON DELETE
-- CASCADE, and that is on purpose. Bonds are cancelled, never deleted — V-nothing
-- has ever removed one — so a cascade would never fire; what it would do is
-- silently destroy a bond's documents if anyone ever did run a delete. Failing
-- loudly is the better behaviour for a mistake like that.

-- `uploaded_by` is nullable because a document may outlive the account that
-- filed it, and removing an account must not delete a bond's information
-- memorandum or make the row untrue. It is also why this is not ON DELETE SET
-- NULL either: the column is already allowed to be empty, so the constraint has
-- nothing to enforce.

-- `document_type` is stored as text rather than as an ordinal, matching the
-- @Enumerated(EnumType.STRING) on the entity: an ordinal would make the meaning
-- of every row depend on the order of a Java enum, so adding a constant in the
-- wrong place would silently relabel historical rows.

-- `storage_key` is unique across the whole table, which is stated as a separate
-- constraint below rather than inline so its name is known. Every key carries a
-- random UUID, so a collision means one object was confirmed twice — a mistake
-- worth failing on rather than two rows quietly describing one file.

ALTER TABLE bond_documents
    ADD CONSTRAINT uk_bond_document_storage_key
        UNIQUE (storage_key);

-- Both indexes serve the only two ways this table is read: everything filed
-- against one bond (the listing), and one bond's logo (every catalogue page
-- that shows one). Neither is read any other way today, so no index is added
-- speculatively — a write to this table is an admin action performed a handful
-- of times per bond, and an unused index is a cost paid on every one of them.
CREATE INDEX IF NOT EXISTS idx_bond_document_bond
    ON bond_documents (bond_id);

CREATE INDEX IF NOT EXISTS idx_bond_document_type
    ON bond_documents (bond_id, document_type);

COMMIT;

-- ---------------------------------------------------------------------------
-- Verification, after running:
--
--   -- The table and its shape.
--   SELECT column_name, data_type, is_nullable, character_maximum_length
--   FROM information_schema.columns
--   WHERE table_name = 'bond_documents'
--   ORDER BY ordinal_position;
--   -- expect: id            | uuid                     | NO  | (null)
--   --         bond_id       | uuid                     | NO  | (null)
--   --         document_type | character varying        | NO  | 40
--   --         file_name     | character varying        | NO  | 255
--   --         storage_key   | character varying        | NO  | 512
--   --         content_type  | character varying        | NO  | 150
--   --         size_bytes    | bigint                   | NO  | (null)
--   --         uploaded_by   | uuid                     | YES | (null)
--   --         created_at    | timestamp with time zone | YES | (null)
--
--   -- The logo column exists on bonds, is nullable, and is empty for every
--   -- bond that existed before this migration.
--   SELECT column_name, data_type, is_nullable, character_maximum_length
--   FROM information_schema.columns
--   WHERE table_name = 'bonds' AND column_name = 'logo_key';
--   -- expect: logo_key | character varying | YES | 512
--
--   SELECT count(*) FROM bonds WHERE logo_key IS NOT NULL;
--   -- expect 0
--
--   -- The foreign keys point where they should.
--   SELECT conname, confrelid::regclass AS references
--   FROM pg_constraint
--   WHERE conrelid = 'bond_documents'::regclass AND contype = 'f'
--   ORDER BY conname;
--   -- expect: bond_documents_bond_id_fkey    | bonds
--   --         bond_documents_uploaded_by_fkey | users
--
--   -- storage_key is unique.
--   SELECT conname, contype FROM pg_constraint
--   WHERE conrelid = 'bond_documents'::regclass AND contype = 'u';
--   -- expect: uk_bond_document_storage_key | u
--
--   -- No bond has more than one logo. This is a service-layer invariant, not a
--   -- constraint — a partial unique index on (bond_id) WHERE document_type =
--   -- 'LOGO' would enforce it in the schema, and is worth adding if the
--   -- service ever stops being the only writer. Expect 0 either way today.
--   SELECT count(*) FROM (
--       SELECT bond_id FROM bond_documents
--       WHERE document_type = 'LOGO'
--       GROUP BY bond_id HAVING count(*) > 1
--   ) d;
--
--   -- The mirror on bonds agrees with the document table. Expect 0.
--   SELECT count(*)
--   FROM bonds b
--   WHERE b.logo_key IS NOT NULL
--     AND NOT EXISTS (
--         SELECT 1 FROM bond_documents d
--         WHERE d.bond_id = b.id
--           AND d.document_type = 'LOGO'
--           AND d.storage_key = b.logo_key);
--
-- Then start the application under the prod profile against this schema.
-- spring.jpa.hibernate.ddl-auto is validate there, so a column name or type
-- this migration got wrong stops the application at startup rather than
-- surfacing as a failure at runtime.
-- ---------------------------------------------------------------------------
