-- Correct the interest-payment anniversary of the Hero Fincorp 2036 NCD.
--
-- A DATA CORRECTION, NOT A SCHEMA CHANGE.
--
-- bonds.ip_date_description for INE957N08201 holds "18/01 Ann". 18 January is
-- the bond's MATURITY date, not its interest-payment anniversary. The coupon
-- engine reads the anniversary out of this text (CouponDateGenerator), so every
-- coupon it projected for this bond landed on 18 January instead of 20 November
-- - the first one a full two months early, and the last one a full coupon where
-- a 59-day broken period was owed.
--
-- The security is 9.10% HERO FINCORP LTD 2036, issued 19-Nov-2025, paying
-- annually on 20 November and maturing 18-Jan-2036:
--
--   20-11-2026 .. 20-11-2035   annual coupon of 9.10 per 100 face
--   18-01-2036                 stub coupon of 59 days
--   18-01-2036                 redemption of 100
--
-- The stub is 100 x 9.10 x 59 / (100 x 365) = 1.4709589041 per 100 face, which
-- is the figure the source schedule states.
--
-- WHY THIS IS A MIGRATION AND NOT A CODE CHANGE.
--
-- There is nowhere else to read the real dates from. No table in this database
-- stores a payment schedule: bonds carries the free-text ip_date_description
-- and a maturity date, and maturity_description for this bond is just
-- "18-Jan-36". The engine therefore has no source of truth but this column, and
-- the column is wrong. Correcting one row is the fix; special-casing the ISIN
-- inside the calculation would be the wrong one, and is deliberately not done -
-- the accompanying change to CouponDateGenerator is generic and touches no bond
-- by name.
--
-- Applied as a single transaction, like V1 through V5. PostgreSQL's DML is
-- transactional, so a failure leaves the row exactly as it was.
--
-- The WHERE clause is guarded on the known-wrong value. Running this against a
-- database where the row has already been corrected - by hand, by an operator,
-- or by a second run of this migration - matches nothing and changes nothing,
-- and it can never overwrite a value somebody has since set deliberately.

BEGIN;

UPDATE bonds
SET ip_date_description = '20/11 Ann'
WHERE isin = 'INE957N08201'
  AND ip_date_description = '18/01 Ann';

COMMIT;

-- ---------------------------------------------------------------------------
-- Verification, after running:
--
--   SELECT isin, coupon_rate, coupon_frequency, ip_date_description,
--          maturity_date
--   FROM bonds WHERE isin = 'INE957N08201';
--
--   -- expect: INE957N08201 | 9.10 | YEARLY | 20/11 Ann | 2036-01-18
--
--   -- Nothing else moved. Before and after should both be 1:
--   SELECT count(*) FROM bonds WHERE ip_date_description = '18/01 Ann';  -- expect 0
--   SELECT count(*) FROM bonds WHERE ip_date_description = '20/11 Ann';  -- expect 1
--
-- Then re-project the bond's cash flow. GET /api/bonds/INE957N08201/ytm with
-- calculationDate=2026-10-05 should show ten coupons of 9.10 per 100 face on
-- 20 November 2026 through 2035, and a final 1.4709589041 coupon together with
-- the 100 redemption on 18-Jan-2036.
--
-- One known difference from the source schedule remains, and this migration does
-- not address it: 20-11-2033 is a Sunday, and the source schedule pays on
-- Monday 21-11-2033. Coupon dates in this engine are pure calendar dates with no
-- business-day adjustment, by design - see docs/bond-module.md section 7.2.
-- ---------------------------------------------------------------------------
