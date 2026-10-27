-- Back-office deals: a deal can now belong to a client record instead of a user.
--
-- customer_id becomes nullable because a client is not an application account
-- and cannot be stored as one; exactly one of customer_id and client_id is set
-- for a deal. deal_date stores the date the employee chose (null for a
-- customer-placed deal, whose date is its creation date).
--
-- Run by hand, like V1-V16; see V12 for why Flyway does not apply these.
-- ddl-auto=validate in prod stops the application if the shape disagrees with
-- DealConfirmation.java.

BEGIN;

ALTER TABLE deal_confirmations ALTER COLUMN customer_id DROP NOT NULL;

ALTER TABLE deal_confirmations
    ADD COLUMN IF NOT EXISTS client_id uuid REFERENCES clients(id);

ALTER TABLE deal_confirmations
    ADD COLUMN IF NOT EXISTS deal_date date;

CREATE INDEX IF NOT EXISTS idx_deal_client ON deal_confirmations (client_id);

COMMIT;

-- Verification, after running:
--
--   SELECT column_name, data_type, is_nullable
--   FROM information_schema.columns
--   WHERE table_name = 'deal_confirmations'
--     AND column_name IN ('customer_id', 'client_id', 'deal_date')
--   ORDER BY column_name;
--   -- expect: client_id uuid YES, customer_id uuid YES, deal_date date YES
--
--   -- Every existing deal still has its customer, and none has a client.
--   SELECT count(*) FROM deal_confirmations WHERE customer_id IS NULL;  -- expect 0
--   SELECT count(*) FROM deal_confirmations WHERE client_id IS NOT NULL; -- expect 0
