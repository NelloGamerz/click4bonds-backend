-- Widen bonds.annual_ytm from two decimal places to four.
--
-- A SCHEMA CHANGE, WITH A DATA CONSEQUENCE.
--
-- annual_ytm is the yield this application calculates and caches (see
-- YtmCalculationServiceImpl). It was stored as numeric(10,2), and the
-- calculation rounded to two places to match. The rate it computes carries six
-- decimals, and a basis point is the fourth, so two places were discarding real
-- differences between bonds that quote to a hundredth of a percent. The
-- calculated value is now kept to four, and the column has to be able to hold
-- it: production runs spring.jpa.hibernate.ddl-auto=validate, so an entity
-- declaring scale=4 against a column of scale=2 stops the application from
-- starting.
--
-- ONLY annual_ytm. semi_ytm and ytc are two-decimal columns for a different
-- reason — they are imported from the source sheet, which states them that way —
-- and widening them would record a precision their source never had. Nothing
-- else on bonds is touched.
--
-- WHAT THIS DOES TO EXISTING ROWS. Widening is exact, never lossy: the values
-- already stored are two-decimal figures and remain those same figures, now
-- written as 10.6900 rather than 10.69. They are NOT recalculated here — a
-- stored yield is a cached answer, and recomputing it from the bond's cash flows
-- is the calculation service's job, not a migration's. The figure a bond is
-- showing today is the figure it shows after this runs; the next recalculation
-- refreshes it to four places.
--
-- Applied as a single transaction, like V1 through V6. PostgreSQL's DDL is
-- transactional, so a failure leaves the schema exactly as it was.

BEGIN;

ALTER TABLE bonds
    ALTER COLUMN annual_ytm TYPE numeric(10, 4);

COMMIT;

-- ---------------------------------------------------------------------------
-- Verification, after running:
--
--   SELECT column_name, numeric_precision, numeric_scale
--   FROM information_schema.columns
--   WHERE table_name = 'bonds'
--     AND column_name IN ('semi_ytm', 'annual_ytm', 'ytc')
--   ORDER BY column_name;
--
--   -- expect: annual_ytm numeric(10,4); semi_ytm and ytc still numeric(10,2)
--
--   -- and that no stored figure moved:
--   SELECT isin, annual_ytm FROM bonds WHERE annual_ytm IS NOT NULL;
--   -- expect: the same numbers as before, now with two trailing zeros
-- ---------------------------------------------------------------------------
