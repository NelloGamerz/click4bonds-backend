-- The depository participant's name, recorded alongside its DP ID. The deal
-- confirmation letter prints the pair in the DP particulars block, so the name
-- belongs to the account rather than being inferred from the account holder.
--
-- Existing rows get an empty string; the next edit of the account supplies the
-- real name.
--
-- Run by hand, like V1-V24; see V12 for why Flyway does not apply these.
-- ddl-auto=validate in prod stops the application if the shape disagrees with
-- DematAccount.java.

BEGIN;

ALTER TABLE demat_accounts
    ADD COLUMN IF NOT EXISTS dp_name varchar(255) NOT NULL DEFAULT '';

ALTER TABLE demat_accounts
    ALTER COLUMN dp_name DROP DEFAULT;

COMMIT;

-- Verification, after running:
--
--   SELECT column_name, data_type, is_nullable, character_maximum_length
--   FROM information_schema.columns
--   WHERE table_name = 'demat_accounts' AND column_name = 'dp_name';
--   -- expect one row: character varying, NO, 255
