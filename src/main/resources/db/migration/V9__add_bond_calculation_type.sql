-- Add bonds.calculation_type: which half of the price/yield pair a bond was
-- created from, and so which half the platform derived.
--
-- WHY A COLUMN AND NOT A DERIVED FLAG. A price and a yield are two readings of
-- one bond, and either can be worked out from the other. Which one the admin
-- supplied is not recoverable afterwards -- both columns are simply populated --
-- so the choice has to be recorded at the point it was made, or the platform
-- cannot tell a bond whose yield it calculated from one whose yield was typed
-- in.
--
-- Values are the CalculationType enum names, as strings:
--
--   BY_PRICE : price supplied  -> annual_ytm was calculated
--   BY_YTM   : yield supplied  -> price was calculated
--   NULL     : neither declared; both were stored as supplied
--
-- NULLABLE, AND NULL FOR EVERY EXISTING ROW. Every bond already stored was
-- created before this field existed, so none of them declared a calculation
-- type, and back-filling a guess would assert something about how a figure was
-- arrived at that the data does not support. NULL is also the value the code
-- reads as "leave both as they are", so the existing rows keep their existing
-- behaviour.
--
-- LENGTH 20 because the longest name is 8 characters; the width is headroom,
-- not a judgement about how many types there will ever be.
--
-- Applied as a single transaction, like the migrations before it.
-- PostgreSQL's DDL is transactional, so a failure leaves the schema as it was.

BEGIN;

ALTER TABLE bonds
    ADD COLUMN calculation_type varchar(20);

COMMIT;

-- ---------------------------------------------------------------------------
-- Verification, after running:
--
--   SELECT column_name, data_type, character_maximum_length, is_nullable
--   FROM information_schema.columns
--   WHERE table_name = 'bonds'
--     AND column_name = 'calculation_type';
--
--   -- expect: varchar, 20, YES
--
--   -- and that no existing row was given a value:
--   SELECT count(*) FROM bonds WHERE calculation_type IS NOT NULL;
--   -- expect: 0
-- ---------------------------------------------------------------------------
