-- Split the role-specific half of a user into two profile tables.
--
-- WHAT THIS DOES. Until now `users` held everything about everyone: the shared
-- identity (email, mobile number, name, role, status) mixed in with fields that
-- only mean something for a customer (the KYC flag, the onboarding step, the
-- sign-up answers and consents) and nowhere to put anything that only means
-- something for a member of staff. This migration moves the customer-only
-- columns into `customer_profiles`, creates `admin_profiles` — which carries the
-- employment record and the JSONB module grants — and re-points the per-channel
-- verification record from the account to the customer profile it verifies.
--
--   users             -> identity: id, email, mobile_number, first_name,
--                        last_name, role, status, created_at, updated_at
--   customer_profiles -> the customer half, one per CUSTOMER
--   admin_profiles    -> the staff half, one per ADMIN or EMPLOYEE
--   user_verifications-> now keyed by customer_profile_id, not user_id
--
-- The invariant the application enforces — a CUSTOMER has exactly one customer
-- profile and no admin profile, an ADMIN or EMPLOYEE the reverse — is a
-- service-layer rule (UserService). What the schema enforces is what it can:
-- one profile per user, on either side.
--
-- WHY THE PROFILE OWNS THE FOREIGN KEY. `customer_profiles.user_id` and
-- `admin_profiles.user_id` are columns on the profile, not two nullable columns
-- on `users`. That is the shape `user_verifications` already had, and it means
-- the exclusivity of the two profiles needs no check constraint — they are
-- separate tables, so "both set" is not a state that exists.
--
-- SAFE TO RUN BY HAND, WHICH IS HOW IT WILL BE RUN. Flyway is declared in the
-- build but Spring Boot 4 moved its auto-configuration into a module this
-- project does not depend on, so no FlywayAutoConfiguration is contributed and
-- `spring.flyway.*` in application-prod.yaml does nothing. Nothing in this
-- directory has ever run automatically; the dev schema is maintained by
-- spring.jpa.hibernate.ddl-auto (update in dev, validate in prod). This file
-- follows the same convention as V1-V11: run the statements, then run the
-- verification queries at the bottom.
--
-- Note for whoever reconciles that: two files claim version 5
-- (V5__add_user_demat_verification.sql and V5__document_r2_path.sql), so Flyway
-- cannot be switched on until one of them is renumbered. That is not this
-- migration's to fix.
--
-- TWO THINGS ARE DISCARDED, DELIBERATELY, AND BOTH ARE REPORTED AT RUN TIME.
-- Everything else is copied to its new home before the old column is dropped.
--
--   * `users.profile_image` — the field is removed from the model entirely. The
--     column was written by the identity provider this application used before
--     the internal user id replaced it, so a database predating that change can
--     still hold avatar URLs from a provider that is no longer in use. Nothing
--     in the application reads or writes the field any more. Section 4 counts the
--     values and raises a NOTICE before dropping them, so the number is on the
--     record rather than gone without a trace.
--   * Verification records belonging to ADMIN/EMPLOYEE accounts — these are
--     customer KYC channels, on accounts that were customers when they signed up
--     and changed role afterwards. They are read by nothing on the staff path,
--     and `admin_profiles.email_verified` is what replaces them. Section 3 counts
--     them and raises a NOTICE before deleting them.
--
-- Take a backup before running this. A dropped column cannot be recovered from
-- the database itself, which is what makes the counts worth printing.
--
-- WHAT IS NOT TOUCHED. No row in `users` is deleted. Every primary key is kept,
-- including every user id, and `bond_orders.customer_id`,
-- `bond_holdings.customer_id`, `deal_confirmations.customer_id`,
-- `bonds.created_by` and `audit_logs.performed_by_id` still reference `users(id)`
-- exactly as before — those entities still point at the account, not at a
-- profile, because the account is what they mean.
--
-- Applied as a single transaction, like V1-V11. PostgreSQL's DDL is
-- transactional, so a failure at any point leaves the schema exactly as it was.
-- One consequence worth stating: ADD CONSTRAINT has no IF NOT EXISTS, so a
-- second run of this file fails on the constraint it added the first time and
-- rolls the whole thing back. That is the intended behaviour for a migration
-- that has already been applied — re-running it should not be a silent no-op —
-- and the verification block below is how to tell whether it has.
--
-- Order matters and is the reverse of the obvious one: the profiles are created
-- and filled BEFORE anything is dropped from `users`, so the old columns are
-- still there to copy from. Nothing is dropped until every row it described has
-- a new home.

BEGIN;

-- ---------------------------------------------------------------------------
-- 1. customer_profiles
--
--    One row per CUSTOMER. The columns are the ones being moved off `users`,
--    with the types they already had there: the three sign-up answers and the
--    onboarding step are enum names stored as text (matching
--    @Enumerated(EnumType.STRING)), and the two consents are booleans that
--    default to false.
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS customer_profiles (
    id                                uuid        PRIMARY KEY,
    user_id                           uuid        NOT NULL UNIQUE REFERENCES users(id),
    is_kyc_completed                  boolean     NOT NULL DEFAULT false,
    onboarding_step                   varchar(255) NOT NULL DEFAULT 'PHONE_VERIFICATION',
    age_range                         varchar(255),
    user_type                         varchar(255),
    preferred_communication_language  varchar(255),
    whatsapp_communication_consent    boolean     NOT NULL DEFAULT false,
    terms_accepted                    boolean     NOT NULL DEFAULT false,
    created_at                        timestamp with time zone,
    updated_at                        timestamp with time zone
);

COMMENT ON TABLE customer_profiles IS
    'The customer half of a user: KYC state, onboarding progress, sign-up answers and consents. One row per user with role CUSTOMER.';

-- The copy. Wrapped in a check because the source columns may legitimately be
-- absent: on a database where the new application code has already run against
-- ddl-auto=update, Hibernate will have created these tables itself, and a
-- database built from the new model never had the columns on `users` at all.
-- Attempting the copy unconditionally would fail to parse against such a schema
-- even though there is nothing to copy.
--
-- COALESCE rather than a plain read, because the consents and the KYC flag were
-- added to `users` at different times: V3 backfilled the consents but left the
-- three answers nullable, and a hand-created row could be missing any of them.
-- False and PHONE_VERIFICATION are the truthful values for a value that was
-- never recorded — the same reasoning V3 used. Nothing is invented: no answer a
-- customer did not give is fabricated, and the two enum answers stay null.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'users' AND column_name = 'is_kyc_completed'
    ) THEN
        EXECUTE $copy$
            INSERT INTO customer_profiles (
                id, user_id, is_kyc_completed, onboarding_step, age_range,
                user_type, preferred_communication_language,
                whatsapp_communication_consent, terms_accepted,
                created_at, updated_at)
            SELECT gen_random_uuid(),
                   u.id,
                   COALESCE(u.is_kyc_completed, false),
                   COALESCE(u.onboarding_step, 'PHONE_VERIFICATION'),
                   u.age_range,
                   u.user_type,
                   u.preferred_communication_language,
                   COALESCE(u.whatsapp_communication_consent, false),
                   COALESCE(u.terms_accepted, false),
                   now(),
                   now()
            FROM users u
            WHERE u.role = 'CUSTOMER'
              AND NOT EXISTS (
                  SELECT 1 FROM customer_profiles cp WHERE cp.user_id = u.id
              )
        $copy$;
    END IF;
END $$;

-- ---------------------------------------------------------------------------
-- 2. admin_profiles
--
--    One row per ADMIN or EMPLOYEE. Both internal roles, because the two are the
--    same kind of thing — a member of staff with an employment record and a set
--    of grants — and differ only in how much the grants constrain them.
--
--    The employment columns are all nullable. No employee record exists in this
--    database yet, so there is nothing to backfill and inventing an employee
--    code or a department would record something nobody said.
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS admin_profiles (
    id             uuid        PRIMARY KEY,
    user_id        uuid        NOT NULL UNIQUE REFERENCES users(id),
    employee_code  varchar(255) UNIQUE,
    department     varchar(255),
    designation    varchar(255),
    email_verified boolean     NOT NULL DEFAULT false,
    access_config  jsonb       NOT NULL DEFAULT '[]'::jsonb,
    created_at     timestamp with time zone,
    updated_at     timestamp with time zone
);

COMMENT ON TABLE admin_profiles IS
    'The staff half of a user: employment details, whether the staff member has confirmed their email, and the JSONB module grants that constrain an EMPLOYEE. One row per user with role ADMIN or EMPLOYEE.';

-- '[]' is the truthful grant set for every existing staff account: no module
-- permission has ever been recorded for anyone, because the column did not
-- exist. An empty set grants nothing, which is the safe direction to be wrong
-- in — and it does not lock anyone out, because ADMIN is not constrained by its
-- grants at all (see AdminPermissionService).
--
-- email_verified starts false for every existing staff account. It records that
-- the person has confirmed the address by signing in with a code sent to it,
-- and nothing has ever recorded that for these accounts — the column did not
-- exist. It is set the next time each of them signs in by email.
INSERT INTO admin_profiles (id, user_id, email_verified, access_config, created_at, updated_at)
SELECT gen_random_uuid(), u.id, false, '[]'::jsonb, now(), now()
FROM users u
WHERE u.role IN ('ADMIN', 'EMPLOYEE')
  AND NOT EXISTS (
      SELECT 1 FROM admin_profiles ap WHERE ap.user_id = u.id
  );

-- ---------------------------------------------------------------------------
-- 3. Re-home user_verifications from the account to the customer profile.
--
--    A verification record is a set of customer KYC channels, and it now hangs
--    off the customer profile. Records belonging to a customer map across
--    directly. Records belonging to a member of staff do not, and are deleted.
--
--    WHY THERE ARE SUCH RECORDS, AND WHY DELETING THEM LOSES NOTHING. The only
--    writer of a verification row has ever been the sign-up flow, which always
--    created a CUSTOMER. Accounts that are staff today hold a record because
--    they were customers when they signed up and their role was changed
--    afterwards. Those records describe channels a staff account is never asked
--    about, and they are read by nothing on the staff path: an admin endpoint
--    authorises on role and module grants, never on KYC. A staff account's own
--    confirmation of its email address is a separate fact, tracked as
--    admin_profiles.email_verified from now on.
--
--    The delete is counted and reported rather than silent, and the transaction
--    rolls back if anything under it fails, so the rows are recoverable from a
--    backup taken before this runs.
-- ---------------------------------------------------------------------------

DO $$
DECLARE
    staff_records integer;
BEGIN
    SELECT count(*) INTO staff_records
    FROM user_verifications uv
    JOIN users u ON u.id = uv.user_id
    WHERE u.role <> 'CUSTOMER';

    IF staff_records > 0 THEN

        RAISE NOTICE
            'Discarding % verification record(s) belonging to ADMIN/EMPLOYEE '
            'accounts. They are customer KYC channels, meaningless for staff, '
            'and admin_profiles.email_verified replaces what matters.',
            staff_records;

        DELETE FROM user_verifications uv
        USING users u
        WHERE u.id = uv.user_id
          AND u.role <> 'CUSTOMER';
    END IF;
END $$;

ALTER TABLE user_verifications ADD COLUMN IF NOT EXISTS customer_profile_id uuid;

UPDATE user_verifications uv
   SET customer_profile_id = cp.id
  FROM customer_profiles cp
 WHERE cp.user_id = uv.user_id
   AND uv.customer_profile_id IS NULL;

-- Verified before the constraint is applied, so a row that did not map is
-- reported as such rather than surfacing as a not-null violation. Every
-- remaining row is a customer's, so every one of them has a profile.
DO $$
DECLARE
    unmapped integer;
BEGIN
    SELECT count(*) INTO unmapped
    FROM user_verifications
    WHERE customer_profile_id IS NULL;

    IF unmapped > 0 THEN
        RAISE EXCEPTION
            'Aborted: % user_verifications row(s) did not map to a customer profile.',
            unmapped;
    END IF;
END $$;

ALTER TABLE user_verifications ALTER COLUMN customer_profile_id SET NOT NULL;

ALTER TABLE user_verifications
    ADD CONSTRAINT uk_user_verification_customer_profile
        UNIQUE (customer_profile_id),
    ADD CONSTRAINT fk_user_verification_customer_profile
        FOREIGN KEY (customer_profile_id) REFERENCES customer_profiles(id);

-- Drops the old unique constraint and the foreign key with it — PostgreSQL
-- removes constraints that depend on a dropped column — so their names never
-- have to be known. That matters because the names differ between a database
-- built by Hibernate and one built by hand.
ALTER TABLE user_verifications DROP COLUMN IF EXISTS user_id;

-- ---------------------------------------------------------------------------
-- 4. Drop the columns that have moved off users.
--
--    Last, and only now that every row they described has been copied.
-- ---------------------------------------------------------------------------

-- profile_image goes with no destination: the field is removed from the model.
--
-- THE VALUES ARE DISCARDED, AND THE COUNT IS REPORTED. The column was written
-- by the identity provider this application used before the internal user id
-- replaced it, so a database that predates that change can still hold avatar
-- URLs hosted by a provider that is no longer in use. Nothing in the
-- application reads or writes the field any more, and the decision taken with
-- the business was to remove it rather than keep a dead column.
--
-- This does not abort, unlike a copy that fails: the discard is intended. It
-- raises a NOTICE carrying the count so the number appears in the migration
-- output and can be reconciled against the pre-migration backup if anyone wants
-- the values, rather than disappearing without a trace.
DO $$
DECLARE
    images integer;
BEGIN
    SELECT count(*) INTO images
    FROM users
    WHERE profile_image IS NOT NULL;

    IF images > 0 THEN
        RAISE NOTICE
            'Discarding % users.profile_image value(s) with the column. These '
            'are legacy avatar URLs from the retired identity provider and are '
            'recoverable from a pre-migration backup if needed.', images;
    END IF;
END $$;

ALTER TABLE users
    DROP COLUMN IF EXISTS profile_image,
    DROP COLUMN IF EXISTS onboarding_step,
    DROP COLUMN IF EXISTS is_kyc_completed,
    DROP COLUMN IF EXISTS age_range,
    DROP COLUMN IF EXISTS user_type,
    DROP COLUMN IF EXISTS preferred_communication_language,
    DROP COLUMN IF EXISTS whatsapp_communication_consent,
    DROP COLUMN IF EXISTS terms_accepted;

COMMIT;

-- ---------------------------------------------------------------------------
-- Verification, after running:
--
--   -- Every account has exactly the profile its role implies, and no other.
--   -- Both expect 0.
--   SELECT count(*) FROM users u
--   WHERE u.role = 'CUSTOMER'
--     AND NOT EXISTS (SELECT 1 FROM customer_profiles cp WHERE cp.user_id = u.id);
--
--   SELECT count(*) FROM users u
--   WHERE u.role IN ('ADMIN','EMPLOYEE')
--     AND NOT EXISTS (SELECT 1 FROM admin_profiles ap WHERE ap.user_id = u.id);
--
--   -- No account has the profile for the other side. Both expect 0.
--   SELECT count(*) FROM customer_profiles cp
--   JOIN users u ON u.id = cp.user_id WHERE u.role <> 'CUSTOMER';
--
--   SELECT count(*) FROM admin_profiles ap
--   JOIN users u ON u.id = ap.user_id WHERE u.role = 'CUSTOMER';
--
--   -- One profile each, never two. Both expect 0.
--   SELECT count(*) FROM (
--       SELECT user_id FROM customer_profiles GROUP BY user_id HAVING count(*) > 1
--   ) d;
--
--   SELECT count(*) FROM (
--       SELECT user_id FROM admin_profiles GROUP BY user_id HAVING count(*) > 1
--   ) d;
--
--   -- The verification records survived and all point at a profile.
--   -- Expect: the count of CUSTOMER users, and 0.
--   SELECT count(*) FROM user_verifications;
--   SELECT count(*) FROM user_verifications WHERE customer_profile_id IS NULL;
--   SELECT count(*) FROM user_verifications uv
--   WHERE NOT EXISTS (
--       SELECT 1 FROM customer_profiles cp WHERE cp.id = uv.customer_profile_id);
--
--   -- No verification record is left for a staff account, and none is left
--   -- keyed to an account. Both expect 0.
--   SELECT count(*) FROM user_verifications uv
--   JOIN customer_profiles cp ON cp.id = uv.customer_profile_id
--   JOIN users u ON u.id = cp.user_id
--   WHERE u.role <> 'CUSTOMER';
--
--   SELECT count(*) FROM information_schema.columns
--   WHERE table_name = 'user_verifications' AND column_name = 'user_id';   -- expect 0
--
--   -- The moved columns are gone from users and nothing else is.
--   SELECT column_name FROM information_schema.columns
--   WHERE table_name = 'users' ORDER BY column_name;
--   -- expect: created_at, email, first_name, id, last_name, mobile_number,
--   --         role, status, updated_at
--
--   -- access_config is JSONB, not null, and defaults to an empty set;
--   -- email_verified is not null and defaults to false.
--   SELECT column_name, data_type, is_nullable, column_default
--   FROM information_schema.columns
--   WHERE table_name = 'admin_profiles'
--     AND column_name IN ('access_config', 'email_verified')
--   ORDER BY column_name;
--   -- expect: access_config  | jsonb   | NO | '[]'::jsonb
--   --         email_verified | boolean | NO | false
--
--   -- The foreign keys other entities hold on users are untouched.
--   SELECT conrelid::regclass AS referencing_table, confrelid::regclass AS referenced_table
--   FROM pg_constraint
--   WHERE contype = 'f' AND confrelid = 'users'::regclass
--   ORDER BY 1;
--   -- expect bond_holdings, bond_orders, bonds, deal_confirmations,
--   --        audit_logs, customer_profiles, admin_profiles
--
-- Then start the application under the prod profile against this schema.
-- spring.jpa.hibernate.ddl-auto is validate there, so a column name or type
-- this migration got wrong stops the application at startup rather than surfacing
-- as a failure at runtime.
-- ---------------------------------------------------------------------------
