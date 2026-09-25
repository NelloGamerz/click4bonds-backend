-- Record where a deal's generated confirmation document was stored.
--
-- Until now a deal could not say whether its confirmation letter had been
-- produced. The document step runs after the deal's transaction commits, so its
-- outcome was only ever visible in the logs: a deal whose document generation
-- failed sat at status CREATED with nothing recording that it still owed a
-- letter. These two columns are what a retry or an operator reads.
--
-- A new file rather than an addition to an earlier one, which has already been
-- applied to the database — a statement appended to it would never run.
--
-- Applied as a single transaction, like V1 and V2. PostgreSQL's DDL is
-- transactional, so a failure leaves the schema exactly as it was.
--
-- Nothing existing is touched. No column is dropped, no row is rewritten, and
-- no identifier changes. Both columns are nullable, so every deal created before
-- this migration keeps working and simply reports "no document yet", which is
-- the truthful state for all of them.
--
-- Why ddl-auto cannot do this: application-prod.yaml sets
-- spring.jpa.hibernate.ddl-auto=validate, so Hibernate checks the schema and
-- refuses to start rather than creating anything. This statement has to be
-- applied by hand before the new code is deployed.

BEGIN;

-- Relative to the configured document storage root, never an absolute path: a
-- deal row must not be tied to one host's directory layout, or moving the
-- storage root would leave every historical row pointing at nothing. Nullable
-- because a document is produced asynchronously and may never be produced at
-- all.
ALTER TABLE deal_confirmations
    ADD COLUMN document_path varchar(512);

-- When the document was written. Kept alongside the path because "which deals
-- are stuck" is a question about age, and the path alone cannot answer it.
ALTER TABLE deal_confirmations
    ADD COLUMN document_generated_at timestamp with time zone;

COMMIT;

-- ---------------------------------------------------------------------------
-- Verification, after running:
--
--   SELECT column_name, data_type, is_nullable, character_maximum_length
--   FROM information_schema.columns
--   WHERE table_name = 'deal_confirmations'
--     AND column_name IN ('document_path', 'document_generated_at');
--
--   -- expect: document_path         | character varying | YES | 512
--   --         document_generated_at | timestamp with time zone | YES | (null)
--
--   -- Every pre-existing deal is undocument, not wrongly documented:
--   SELECT count(*) FROM deal_confirmations WHERE document_path IS NOT NULL;
--   -- expect 0
--
--   -- The two columns are either both set or both unset:
--   SELECT count(*) FROM deal_confirmations
--   WHERE (document_path IS NULL) <> (document_generated_at IS NULL);
--   -- expect 0
-- ---------------------------------------------------------------------------
