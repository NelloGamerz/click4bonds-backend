-- Deal lifecycle: a back-office deal is lettered only when an employee confirms
-- it, and the inputs its letter needs are captured on the deal at creation so
-- the letter can be built after the request that carried them has returned.
--
-- The status column already exists (CREATED, CONFIRMATION_GENERATED); this
-- migration adds the statuses' values (CONFIRMED, CANCELLED) only in the
-- application's enum, so nothing changes here for it.
--
-- Run by hand, like V1-V21; see V12 for why Flyway does not apply these.
-- ddl-auto=validate in prod stops the application if the shape disagrees with
-- DealConfirmation.java.

BEGIN;

ALTER TABLE deal_confirmations
    ADD COLUMN IF NOT EXISTS delivery_mode varchar(20),
    ADD COLUMN IF NOT EXISTS settlement_number varchar(64),
    ADD COLUMN IF NOT EXISTS side varchar(10) NOT NULL DEFAULT 'SELL',
    ADD COLUMN IF NOT EXISTS depository varchar(20),
    ADD COLUMN IF NOT EXISTS bank_account_id uuid,
    ADD COLUMN IF NOT EXISTS demat_account_id uuid,
    ADD COLUMN IF NOT EXISTS send_email boolean NOT NULL DEFAULT false;

COMMIT;

-- Verification, after running:
--
--   SELECT column_name, data_type, is_nullable
--   FROM information_schema.columns
--   WHERE table_name = 'deal_confirmations'
--     AND column_name IN ('delivery_mode', 'settlement_number', 'side',
--                         'depository', 'bank_account_id', 'demat_account_id',
--                         'send_email');
--   -- expect seven rows; only delivery_mode, settlement_number, depository,
--   --        bank_account_id and demat_account_id are nullable
