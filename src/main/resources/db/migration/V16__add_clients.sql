-- Client records maintained by the back office.
--
-- A client is a party the business deals with, not an application account: this
-- table has no foreign key to users and nothing in it is reachable from a
-- client-facing endpoint. Created by the admin client surface, which requires the
-- CLIENT_MANAGEMENT grant on an EMPLOYEE account.
--
-- As with V1-V15, this is run by hand; see V12 for why Flyway does not apply it.
-- ddl-auto=validate in prod stops the application if the shape below disagrees
-- with Client.java.

BEGIN;

CREATE TABLE IF NOT EXISTS clients (
    id              uuid         PRIMARY KEY,
    name            varchar(255) NOT NULL,
    contact_person  varchar(255),
    email           varchar(255),
    mobile_number   varchar(30),
    address         text,
    created_at      timestamp with time zone,
    updated_at      timestamp with time zone
);

COMMENT ON TABLE clients IS
    'Clients recorded by the back office. Standalone: no relation to users or to the customer/staff profiles.';

COMMIT;

-- Verification, after running:
--
--   SELECT column_name, data_type, is_nullable
--   FROM information_schema.columns
--   WHERE table_name = 'clients'
--   ORDER BY ordinal_position;
--   -- expect: id uuid NO, name varchar NO, contact_person varchar YES,
--   --         email varchar YES, mobile_number varchar YES, address text YES,
--   --         created_at timestamptz YES, updated_at timestamptz YES
--
--   SELECT count(*)
--   FROM pg_constraint
--   WHERE conrelid = 'clients'::regclass AND contype = 'f';   -- expect 0
