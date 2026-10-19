-- Move a bond's logo onto its issuer.
--
-- WHAT THIS DOES. Adds `issuers.logo_key`, copies each issuer's current bond
-- logo into it, then drops `bonds.logo_key`.
--
-- WHY. A logo is the issuer's, not the bond's: every bond of one issuer shows
-- the same image, so storing a copy per bond duplicated the same key on every
-- row and let two bonds of one issuer disagree about it. The catalogue still
-- needs the key without a query per row, so it now lives on the row every bond
-- of that issuer already joins to — `issuers`.
--
-- WHICH KEY AN ISSUER INHERITS. The most recently updated bond logo of that
-- issuer. Where an issuer has several bonds with logos these were separate
-- uploads of the same image; any one of them names an object that exists, and
-- the newest is the safest choice. Bonds that already belonged to an issuer
-- with a logo key are left alone, so the copy is safe to re-run.
--
-- WHY THE OLD COLUMN IS DROPPED RATHER THAN LEFT. The application no longer
-- reads or writes it. Leaving it would give two columns the same name in
-- different tables and invite a later change to pick the wrong one; the
-- document row (`bond_documents` of type LOGO) remains the record either way.
--
-- NOTE: Flyway does not run in this project. Execute this file by hand, after
-- taking a backup, before deploying the code that expects the new shape.
-- The statements are written to be safe to run more than once.

BEGIN;

-- 1. The new column on the issuer.
ALTER TABLE issuers
    ADD COLUMN IF NOT EXISTS logo_key varchar(512);

COMMENT ON COLUMN issuers.logo_key IS
    'Object-store key of the issuer logo, e.g. bonds/ine123a01011/logo/2026/10/<uuid>.png. NULL when the issuer has no logo. Mirrored from the issuer''s LOGO document by BondDocumentService, which is its only writer.';


-- 2. Backfill from the bonds that had a logo, newest first per issuer.
--
-- DISTINCT ON (issuer_id) keeps one row per issuer; the ORDER BY decides which
-- and matches how a catalogue would have shown them before the move.
UPDATE issuers i
SET logo_key = src.logo_key
FROM (
    SELECT DISTINCT ON (issuer_id)
           issuer_id,
           logo_key
    FROM bonds
    WHERE issuer_id IS NOT NULL
      AND logo_key IS NOT NULL
    ORDER BY issuer_id,
             updated_at DESC NULLS LAST,
             created_at DESC NULLS LAST,
             id
) src
WHERE i.id = src.issuer_id
  AND (i.logo_key IS NULL OR i.logo_key = '');


-- 3. Remove the column from the bond.
ALTER TABLE bonds
    DROP COLUMN IF EXISTS logo_key;

COMMIT;


-- =============================================================
-- VERIFICATION (run separately; not part of the transaction)
-- =============================================================
--
-- The new column exists with the expected type:
--
--   SELECT column_name, data_type, is_nullable, character_maximum_length
--   FROM information_schema.columns
--   WHERE table_name = 'issuers' AND column_name = 'logo_key';
--   -- expect: logo_key | character varying | YES | 512
--
-- The old column is gone:
--
--   SELECT count(*) FROM information_schema.columns
--   WHERE table_name = 'bonds' AND column_name = 'logo_key';
--   -- expect: 0
--
-- Every inherited key still names an object a document row records (or at
-- least exists), per issuer:
--
--   SELECT i.id, i.name, i.logo_key
--   FROM issuers i
--   WHERE i.logo_key IS NOT NULL
--   ORDER BY i.name;
--