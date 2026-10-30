-- Deal-scoped yield: the annual and semi-annual YTM an employee calculated for
-- this particular deal. Kept on the deal, not on the bond, so pricing one deal
-- never moves the bond's own quoted yield or price.
--
-- Null until a calculation is saved for the deal.
--
-- Run by hand, like V1-V20; see V12 for why Flyway does not apply these.
-- ddl-auto=validate in prod stops the application if the shape disagrees with
-- DealConfirmation.java.

BEGIN;

ALTER TABLE deal_confirmations
    ADD COLUMN IF NOT EXISTS annual_ytm numeric(19, 4),
    ADD COLUMN IF NOT EXISTS semi_ytm numeric(19, 4);

COMMIT;

-- Verification, after running:
--
--   SELECT column_name, data_type, numeric_precision, numeric_scale
--   FROM information_schema.columns
--   WHERE table_name = 'deal_confirmations'
--     AND column_name IN ('annual_ytm', 'semi_ytm');
--   -- expect: annual_ytm numeric 19 4
--   --         semi_ytm   numeric 19 4
