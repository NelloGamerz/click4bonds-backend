-- The profile the sign-up form collects.
--
-- Five columns on users, matching the fields on the sign-up request: the two
-- question answers that shape what the platform sends and shows, and the two
-- consents. The entity has carried these fields already; this is what makes
-- them real in a schema that is validated rather than generated
-- (spring.jpa.hibernate.ddl-auto=validate in production, which is why the
-- application would otherwise refuse to start against this database).
--
-- A new file rather than an addition to an earlier one, which has already been
-- applied — a statement appended to those would never run.
--
-- Applied as a single transaction, like V1 and V2. PostgreSQL's DDL is
-- transactional, so a failure leaves the schema exactly as it was.
--
-- Nothing existing is touched but the five columns named here. No column is
-- dropped, no identifier changes, and the only rows updated are the ones whose
-- new consent columns are still null — which is every existing row, once.

BEGIN;

-- The demographic answers. All three are enum names stored as text, matching
-- @Enumerated(EnumType.STRING) on the entity — a string column, not a database
-- enum type, so adding a value later is a code change and not a migration.
--
-- Nullable, deliberately. They are required by the sign-up form and are
-- validated there, but every account that exists today was created by the
-- older phone-only sign-in, which collected none of this. A NOT NULL here
-- would have no value to backfill those rows with, and inventing one would
-- record answers nobody gave.
--
-- IF NOT EXISTS because these may already be present: a development database
-- running with ddl-auto=update has had Hibernate create them from the entity.
-- Production validates instead, and has neither.
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS age_range varchar(255),
    ADD COLUMN IF NOT EXISTS user_type varchar(255),
    ADD COLUMN IF NOT EXISTS preferred_communication_language varchar(255);

-- The consents, in three steps rather than one ADD COLUMN ... NOT NULL DEFAULT,
-- because a development database may already have them and may already have
-- rows in them. Hibernate adds a column as nullable when it cannot add it as
-- NOT NULL, so "already exists" can mean "exists with nulls in it" — and
-- ADD COLUMN ... IF NOT EXISTS would skip it and leave it that way.
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS whatsapp_communication_consent boolean,
    ADD COLUMN IF NOT EXISTS terms_accepted boolean;

-- False is the truthful value for every existing account: neither consent was
-- ever asked for, so neither was ever given. Recording a consent that was not
-- given is the one mistake a consent column must not make.
UPDATE users SET whatsapp_communication_consent = false
    WHERE whatsapp_communication_consent IS NULL;

UPDATE users SET terms_accepted = false
    WHERE terms_accepted IS NULL;

-- Now that no row is null, the constraint the entity declares can be applied.
-- Setting a default as well keeps a row inserted outside this application —
-- by a migration, a script or a console session — from failing on a column it
-- never knew about.
ALTER TABLE users
    ALTER COLUMN whatsapp_communication_consent SET DEFAULT false,
    ALTER COLUMN whatsapp_communication_consent SET NOT NULL,
    ALTER COLUMN terms_accepted SET DEFAULT false,
    ALTER COLUMN terms_accepted SET NOT NULL;

COMMIT;

-- ---------------------------------------------------------------------------
-- Verification, after running:
--
--   SELECT column_name, data_type, is_nullable, column_default
--   FROM information_schema.columns
--   WHERE table_name = 'users'
--     AND column_name IN (
--         'age_range', 'user_type', 'preferred_communication_language',
--         'whatsapp_communication_consent', 'terms_accepted')
--   ORDER BY column_name;
--
--   -- expect: the three enum columns nullable with no default,
--   --         the two consent columns NOT NULL defaulting to false
--
--   SELECT count(*) FROM users WHERE whatsapp_communication_consent IS NULL;  -- expect 0
--   SELECT count(*) FROM users WHERE terms_accepted IS NULL;                  -- expect 0
--
--   -- and that nothing was invented for the enum columns:
--   SELECT count(*) FROM users
--   WHERE age_range IS NOT NULL OR user_type IS NOT NULL;                     -- expect 0
-- ---------------------------------------------------------------------------
