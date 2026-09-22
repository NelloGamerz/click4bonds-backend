-- Record whether an account has completed KYC.
--
-- A separate flag from onboarding_step, which tracks how far a user has got
-- rather than whether they are finished: the step can sit at its final value
-- while a re-verification is still outstanding.
--
-- A new file rather than an addition to V1, which has already been applied to
-- the database — a statement appended to it would never run.
--
-- Applied as a single transaction, like V1. PostgreSQL's DDL is transactional,
-- so a failure leaves the schema exactly as it was.
--
-- Nothing existing is touched. No column is dropped, no row is rewritten, and
-- no identifier changes.

BEGIN;

-- NOT NULL with a default is safe here and on PostgreSQL 11+ it is also fast:
-- the default is recorded in the catalog rather than written to every row, so
-- this does not rewrite the table. Every existing account is created as "not
-- KYC-complete", which is the truthful starting state — none of them have been
-- through a KYC process.
ALTER TABLE users
    ADD COLUMN is_kyc_completed boolean NOT NULL DEFAULT false;

COMMIT;

-- ---------------------------------------------------------------------------
-- Verification, after running:
--
--   SELECT column_name, data_type, is_nullable, column_default
--   FROM information_schema.columns
--   WHERE table_name = 'users' AND column_name = 'is_kyc_completed';
--
--   -- expect: is_kyc_completed | boolean | NO | false
--
--   SELECT count(*) FROM users WHERE is_kyc_completed IS NULL;   -- expect 0
-- ---------------------------------------------------------------------------
