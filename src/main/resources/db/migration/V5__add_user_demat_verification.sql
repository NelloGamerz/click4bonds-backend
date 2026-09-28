-- The demat channel on the verification record.
--
-- Bonds settle into a demat account, so an account that cannot show one cannot
-- hold what it buys. Until now the record tracked four channels — email, phone,
-- PAN and bank account — and a user could reach the end of onboarding without
-- ever having been asked for a demat account. This column is the fifth and last
-- channel.
--
-- A new file rather than an addition to an earlier one, which has already been
-- applied to the database — a statement appended to it would never run.
--
-- Applied as a single transaction, like V1 through V4. PostgreSQL's DDL is
-- transactional, so a failure leaves the schema exactly as it was.
--
-- Why ddl-auto cannot do this: application-prod.yaml sets
-- spring.jpa.hibernate.ddl-auto=validate, so Hibernate checks the schema and
-- refuses to start rather than creating anything. This statement has to be
-- applied by hand before the new code is deployed.
--
-- Nothing existing is touched but the column named here. No column is dropped,
-- no identifier changes, and the only rows updated are the ones whose new
-- column is still null — which is every existing row, once.

BEGIN;

-- An enum name stored as text, matching @Enumerated(EnumType.STRING) on the
-- entity — a string column, not a database enum type, so adding a value later
-- is a code change and not a migration.
--
-- Added nullable first. Hibernate adds a column as nullable when it cannot add
-- it as NOT NULL, so a development database running with ddl-auto=update may
-- already have this column and may already have rows in it with nulls; ADD
-- COLUMN ... IF NOT EXISTS would skip it and leave it that way.
ALTER TABLE user_verifications
    ADD COLUMN IF NOT EXISTS demat_status varchar(255);

-- NOT_STARTED is the truthful value for every existing row and every row yet to
-- come. No account has ever been asked for a demat account, so none of them has
-- one verified — and "not started" is what an unfinished channel is. Any other
-- value would claim a verification that never happened, which is the one thing
-- a verification column must not do.
UPDATE user_verifications SET demat_status = 'NOT_STARTED'
    WHERE demat_status IS NULL;

-- Now that no row is null, the constraint the entity declares can be applied.
-- Setting a default as well keeps a row inserted outside this application —
-- by a migration, a script or a console session — from failing on a column it
-- never knew about.
ALTER TABLE user_verifications
    ALTER COLUMN demat_status SET DEFAULT 'NOT_STARTED',
    ALTER COLUMN demat_status SET NOT NULL;

COMMIT;

-- ---------------------------------------------------------------------------
-- Verification, after running:
--
--   SELECT column_name, data_type, is_nullable, column_default
--   FROM information_schema.columns
--   WHERE table_name = 'user_verifications'
--     AND column_name = 'demat_status';
--
--   -- expect: demat_status | character varying | NO | 'NOT_STARTED'::character varying
--
--   -- No row was left without a value, and none was given a false one:
--   SELECT count(*) FROM user_verifications WHERE demat_status IS NULL;          -- expect 0
--   SELECT count(*) FROM user_verifications WHERE demat_status <> 'NOT_STARTED'; -- expect 0
-- ---------------------------------------------------------------------------
