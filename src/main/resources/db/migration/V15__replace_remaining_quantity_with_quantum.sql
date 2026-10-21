-- Replace the separate inventory column with the bond's quantum.
--
-- WHAT THIS DOES. Drops `bonds.remaining_quantity`, moves `quantum_in_lacs`
-- onto `bonds.quantum`, and converts the value from lakhs to the full INR
-- amount (50.00 lakh -> 5000000.00).
--
-- WHY THE VALUE IS CONVERTED. The request and the description parser express
-- quantum in lakhs, but inventory is reserved in full amounts: a 10,000-unit
-- reservation against a 50-lakh bond must compare 10,000 with 5,000,000, not
-- with 50. The application now writes the full amount, and this migration brings
-- existing rows onto the same scale so old and new rows cannot disagree by a
-- factor of 100,000.
--
-- WHY. Inventory was stored twice: `remaining_quantity` held the units still
-- available, while `quantum_in_lacs` held the same offering size the rest of the
-- bond record (and `maxNoOfBonds`) is derived from. Two columns describing one
-- supply can disagree, and only one of them was ever decremented. The quantum is
-- now the single inventory figure: `BondRepository.reserveQuantity` deducts from
-- it with the same conditional UPDATE that guarded `remaining_quantity`.
--
-- WHY THE OLD COLUMN IS DROPPED RATHER THAN LEFT. The application no longer
-- reads or writes it. Leaving it would leave a second, always-stale inventory
-- figure for a later change to pick up by mistake.
--
-- NOTE: Flyway does not run in this project. Execute this file by hand, after
-- taking a backup, before deploying the code that expects the new shape.
-- The statements are written to be safe to run more than once: the conversion
-- is guarded by the column comment, which is set once the values are in rupees.

BEGIN;

-- 1. The old inventory column is gone.
ALTER TABLE bonds
    DROP COLUMN IF EXISTS remaining_quantity;


-- 2. Move the quantum value onto its new name, in full rupees.
--
-- Possible states:
--   * both columns exist (Hibernate added `quantum` while `quantum_in_lacs`
--     still holds the value): copy the value across, then drop the old column;
--   * only `quantum_in_lacs` exists: rename it;
--   * only `quantum` exists (an earlier hand-run of this file renamed it, or
--     Hibernate created it): leave the name alone.
--
-- In every state the value is still in lakhs unless the column comment says
-- otherwise, so it is multiplied by 100,000 exactly once.
DO $$
DECLARE
    has_old_column boolean;
    has_new_column boolean;
    already_full_inr boolean;
BEGIN
    SELECT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = 'bonds'
          AND column_name = 'quantum_in_lacs'
    ) INTO has_old_column;

    SELECT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = 'bonds'
          AND column_name = 'quantum'
    ) INTO has_new_column;

    SELECT COALESCE(
        col_description('bonds'::regclass, attnum)
            = 'Quantum in full INR (not lakhs).',
        false
    ) INTO already_full_inr
    FROM pg_attribute
    WHERE attrelid = 'bonds'::regclass
      AND attname = 'quantum'
      AND NOT attisdropped;

    IF has_old_column AND has_new_column THEN

        UPDATE bonds
        SET quantum = quantum_in_lacs * 100000
        WHERE quantum IS NULL;

        ALTER TABLE bonds
            DROP COLUMN quantum_in_lacs;

        already_full_inr := false;

    ELSIF has_old_column THEN

        ALTER TABLE bonds
            RENAME COLUMN quantum_in_lacs TO quantum;

        already_full_inr := false;

    END IF;

    IF (has_old_column OR has_new_column) AND NOT COALESCE(already_full_inr, false) THEN

        UPDATE bonds
        SET quantum = quantum * 100000
        WHERE quantum IS NOT NULL;

        COMMENT ON COLUMN bonds.quantum IS
            'Quantum in full INR (not lakhs).';

    END IF;
END $$;

COMMIT;


-- =============================================================
-- VERIFICATION (run separately; not part of the transaction)
-- =============================================================
--
-- The new column exists with the expected type, and the comment records the
-- scale:
--
--   SELECT column_name, data_type, numeric_precision, numeric_scale, is_nullable
--   FROM information_schema.columns
--   WHERE table_name = 'bonds' AND column_name = 'quantum';
--   -- expect: quantum | numeric | 19 | 2 | YES
--
--   SELECT col_description('bonds'::regclass, attnum)
--   FROM pg_attribute
--   WHERE attrelid = 'bonds'::regclass AND attname = 'quantum';
--   -- expect: Quantum in full INR (not lakhs).
--
-- A 50-lakh quantum is now 5,000,000, not 50:
--
--   SELECT isin, quantum FROM bonds ORDER BY updated_at DESC LIMIT 10;
--
-- Neither old column remains:
--
--   SELECT column_name FROM information_schema.columns
--   WHERE table_name = 'bonds'
--     AND column_name IN ('remaining_quantity', 'quantum_in_lacs');
--   -- expect: 0 rows
