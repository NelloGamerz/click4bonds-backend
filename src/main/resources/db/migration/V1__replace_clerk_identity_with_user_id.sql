-- Replace the external identity provider's identifier with the internal user id.
--
-- Sign-in is now a phone number and an OTP, so a user is identified by
-- users.id and nothing else. The external provider's identifier was the only
-- other identity a row could hold, and this migration retires it.
--
-- Scope, verified against the live schema before being written:
--   * users.clerk_user_id is the only column carrying an external identifier.
--   * bonds.created_by is the only column referencing it.
--   * bond_orders.customer_id, bond_holdings.customer_id,
--     deal_confirmations.customer_id, user_verifications.user_id and
--     audit_logs.performed_by_id already reference users(id) and are untouched.
--
-- Applied as a single transaction: PostgreSQL's DDL is transactional, so a
-- failure at any point leaves the schema exactly as it was. That matters
-- because the alternative — a schema that is half-migrated — is not something
-- the application can run against.
--
-- Business and document identifiers are not touched. No row is deleted, and no
-- primary key or bond, order, holding or inquiry identifier changes.

BEGIN;

-- ---------------------------------------------------------------------------
-- 1. Refuse to proceed unless every creator can be resolved unambiguously.
--
--    This runs before anything is altered. If a bond names a creator that no
--    user row matches, there is no safe mapping to invent, and silently
--    nulling the column would destroy an audit trail. Stopping is the only
--    correct outcome, so the migration raises and the transaction rolls back.
-- ---------------------------------------------------------------------------

DO $$
DECLARE
    unmapped integer;
    ambiguous integer;
BEGIN
    SELECT count(*) INTO unmapped
    FROM bonds b
    WHERE b.created_by IS NOT NULL
      AND NOT EXISTS (
          SELECT 1 FROM users u WHERE u.clerk_user_id = b.created_by
      );

    IF unmapped > 0 THEN
        RAISE EXCEPTION
            'Aborted: % bond row(s) have a created_by that matches no user.clerk_user_id. '
            'The mapping cannot be inferred and must be resolved by hand.',
            unmapped;
    END IF;

    -- A duplicate external identifier would make the mapping ambiguous. The
    -- unique constraint should prevent it; this checks that it did.
    SELECT count(*) INTO ambiguous
    FROM (
        SELECT clerk_user_id
        FROM users
        WHERE clerk_user_id IS NOT NULL
        GROUP BY clerk_user_id
        HAVING count(*) > 1
    ) duplicates;

    IF ambiguous > 0 THEN
        RAISE EXCEPTION
            'Aborted: % user.clerk_user_id value(s) are not unique, so a bond '
            'cannot be attributed to one user.', ambiguous;
    END IF;
END $$;

-- ---------------------------------------------------------------------------
-- 2. Bonds: point created_by at the user id.
--
--    Built as a new column populated from the old one — add, copy, verify,
--    swap — rather than altering the type in place. Each step is observable,
--    and the verification between copy and swap means a partial mapping cannot
--    slip through.
-- ---------------------------------------------------------------------------

ALTER TABLE bonds ADD COLUMN created_by_user_id uuid;

UPDATE bonds b
SET created_by_user_id = u.id
FROM users u
WHERE u.clerk_user_id = b.created_by;

DO $$
DECLARE
    lost integer;
BEGIN
    SELECT count(*) INTO lost
    FROM bonds
    WHERE created_by IS NOT NULL
      AND created_by_user_id IS NULL;

    IF lost > 0 THEN
        RAISE EXCEPTION
            'Aborted: % bond row(s) did not map to a user during the copy.', lost;
    END IF;
END $$;

-- Dropped before the old column so the foreign key does not block the swap.
ALTER TABLE bonds DROP CONSTRAINT fk_bonds_created_by;

ALTER TABLE bonds DROP COLUMN created_by;

ALTER TABLE bonds RENAME COLUMN created_by_user_id TO created_by;

ALTER TABLE bonds
    ADD CONSTRAINT fk_bonds_created_by
    FOREIGN KEY (created_by) REFERENCES users(id);

-- ---------------------------------------------------------------------------
-- 3. Email becomes optional.
--
--    An account is now created by proving a phone number, which happens before
--    any address is known; the email verification step is what supplies one.
--    The unique index is left in place — PostgreSQL treats nulls as distinct,
--    so many accounts may be awaiting an address while no two may share one.
-- ---------------------------------------------------------------------------

ALTER TABLE users ALTER COLUMN email DROP NOT NULL;

-- ---------------------------------------------------------------------------
-- 4. Retire the external identifier.
--
--    Last, because step 2 needed it to resolve the mapping. Doing this any
--    earlier would have discarded the only means of attributing existing rows.
-- ---------------------------------------------------------------------------

ALTER TABLE users DROP CONSTRAINT IF EXISTS idx_user_clerk_id;
ALTER TABLE users DROP CONSTRAINT IF EXISTS uq_users_clerk_user_id;

ALTER TABLE users DROP COLUMN clerk_user_id;

COMMIT;

-- ---------------------------------------------------------------------------
-- Not done here, deliberately:
--
--   * outbox_events is left in place. Its only purpose was to push role changes
--     to the external provider, and nothing writes to it now, but dropping a
--     populated table is not something a migration should decide on its own.
--     It can be dropped separately once the rows are confirmed unwanted:
--         DROP TABLE outbox_events;
--
--   * No index is added on bonds.created_by. There was none before, and the
--     column is an audit reference that no query filters on.
-- ---------------------------------------------------------------------------
