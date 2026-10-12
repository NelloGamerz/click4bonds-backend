-- Replace the free-text record-date rule with the day offset it always stood
-- for.
--
-- WHY. bonds.record_date_description held a hand-written sentence such as
-- "15 days prior to interest payment date", which RecordDateParser turned back
-- into a number of days. The sentence carried no information the number does
-- not: every supported wording reduced to a single positive whole number of
-- days before the interest payment date. Storing the number directly removes a
-- whole grammar (RecordDateDescriptions / RecordDateRule /
-- RelativeDaysRecordDateRule) from the path between the admin and a priced
-- bond, and removes the class of failure where wording the parser did not
-- recognise was rejected outright.
--
-- WHAT THE NUMBER MEANS. record_date is the number of days before each interest
-- payment date on which the record date falls:
--
--   record_date      = 15
--   payment date     = 2026-10-23
--   derived record   = 2026-10-08
--
-- The window from the record date up to the payment date is the record period.
-- A settlement inside it is ex-interest: the buyer is not entitled to that
-- coupon and the accrued interest on it is not charged.
--
-- NULLABLE, AND NULL FOR EVERY EXISTING ROW. The offset is not recoverable from
-- the database for the rows already stored: extracting "15" from the text would
-- silently re-assert a rule for bonds whose wording may have been partially
-- understood, and some rows have no rule at all ("NA", blank). A NULL row means
-- exactly what the old text meant by no rule - keep the historical cum-interest
-- behaviour and include every future coupon - so nothing is invented, and the
-- admin sets the offset on the bonds that need one.
--
-- INTEGER, NOT SMALLINT. A day offset has no plausible bound anywhere near
-- 32,767, and the width of the column is not the place to encode a validation
-- rule; the API enforces a positive value instead.
--
-- THE OLD COLUMN IS DELIBERATELY LEFT IN PLACE. This migration adds a column and
-- touches no existing data, so it is reversible in the sense that matters: if
-- the offset turns out to be wrong, the original wording is still there to fall
-- back on. It is no longer mapped by the entity. Once the offsets have been
-- populated and verified in every environment, a later migration can drop it.
--
-- Applied as a single transaction, like the migrations before it. PostgreSQL's
-- DDL is transactional, so a failure leaves the schema as it was.

BEGIN;

ALTER TABLE bonds
    ADD COLUMN record_date integer;

COMMIT;

-- ---------------------------------------------------------------------------
-- Verification, after running:
--
--   SELECT column_name, data_type, is_nullable
--   FROM information_schema.columns
--   WHERE table_name = 'bonds'
--     AND column_name = 'record_date';
--
--   -- expect: integer, YES
--
--   -- and that no existing row was given a value:
--   SELECT count(*) FROM bonds WHERE record_date IS NOT NULL;
--   -- expect: 0
--
--   -- the old text column is untouched and still populated:
--   SELECT count(*) FROM bonds WHERE record_date_description IS NOT NULL;
--   -- expect: the same count as before this migration
-- ---------------------------------------------------------------------------
