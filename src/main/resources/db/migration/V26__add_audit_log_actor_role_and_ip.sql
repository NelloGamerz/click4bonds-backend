-- Audit entries: record the actor's role at the time of the action and the
-- caller's address.
--
-- performed_by_role is a snapshot, not a join: the actor's current role can
-- change after the entry is written, but the entry has to keep describing what
-- was true when the action happened.
--
-- Applied as a single transaction, like the earlier migrations.

BEGIN;

ALTER TABLE audit_logs
    ADD COLUMN IF NOT EXISTS performed_by_role VARCHAR(32),
    ADD COLUMN IF NOT EXISTS ip_address VARCHAR(64);

COMMIT;
