-- The staff account that created a back-office deal, so an employee can list
-- the deals they created.
--
-- Null for a deal a customer placed through the app: there is no employee
-- behind it. Every deal created through AdminDealController sets it.
--
-- Run by hand, like V1-V17; see V12 for why Flyway does not apply these.
-- ddl-auto=validate in prod stops the application if the shape disagrees with
-- DealConfirmation.java.

BEGIN;

ALTER TABLE deal_confirmations
    ADD COLUMN IF NOT EXISTS created_by uuid REFERENCES users(id);

CREATE INDEX IF NOT EXISTS idx_deal_created_by ON deal_confirmations (created_by);

COMMIT;

-- Verification, after running:
--
--   SELECT column_name, data_type, is_nullable
--   FROM information_schema.columns
--   WHERE table_name = 'deal_confirmations'
--     AND column_name = 'created_by';
--   -- expect: created_by uuid YES
