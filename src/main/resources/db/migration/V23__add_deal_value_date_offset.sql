-- Settlement lag chosen for a back-office deal: 0 = T+0 (same day), 1 = T+1,
-- 2 = T+2. The value date is the deal date plus that many business days, and
-- an ICCL letter prints ICDM(T+n) from it. Captured on the deal because the
-- letter is generated when the deal is confirmed, after the create request.
--
-- Run by hand, like V1-V22; see V12 for why Flyway does not apply these.
-- ddl-auto=validate in prod stops the application if the shape disagrees with
-- DealConfirmation.java.

BEGIN;

ALTER TABLE deal_confirmations
    ADD COLUMN IF NOT EXISTS value_date_offset_days integer NOT NULL DEFAULT 0;

COMMIT;

-- Verification, after running:
--
--   SELECT column_name, data_type, is_nullable, column_default
--   FROM information_schema.columns
--   WHERE table_name = 'deal_confirmations'
--     AND column_name = 'value_date_offset_days';
--   -- expect one row: integer, NOT NULL, default 0
