-- Shared party architecture: one identity record for clients and customers,
-- with PAN, bank account and demat account records hanging off it.
--
-- As with V1-V18 this is run by hand: Flyway's auto-configuration is not on the
-- classpath (see V8 and V12 for why). dev uses ddl-auto=update and will create
-- these tables on first boot; this script is what makes prod, which runs
-- ddl-auto=validate, match the entities. Run it with the application stopped.
--
-- Sensitive columns:
--   pan_details.pan_number       AES-256-GCM ciphertext (base64 of IV+ct+tag)
--   bank_accounts.account_number AES-256-GCM ciphertext, same format
-- The key lives in PARTY_ENCRYPTION_KEY / party.encryption-key, never in this
-- database. These columns are text, not varchar: ciphertext length is not the
-- plaintext length.

BEGIN;

-- ---------------------------------------------------------------------------
-- Parties
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS parties (
    id              uuid         PRIMARY KEY,
    party_type      varchar(255) NOT NULL,
    legal_name      varchar(255) NOT NULL,
    contact_email   varchar(255),
    mobile_number   varchar(255),
    address         text,
    created_at      timestamp with time zone,
    updated_at      timestamp with time zone
);

COMMENT ON TABLE parties IS
    'An individual or legal entity the business holds financial records against. Not a login account.';

CREATE INDEX IF NOT EXISTS idx_party_legal_name ON parties (legal_name);

-- ---------------------------------------------------------------------------
-- PAN
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS pan_details (
    id              uuid         PRIMARY KEY,
    party_id        uuid         NOT NULL,
    pan_number      text         NOT NULL,
    status          varchar(255) NOT NULL,
    verified_at     timestamp with time zone,
    created_at      timestamp with time zone,
    updated_at      timestamp with time zone,
    CONSTRAINT uk_pan_details_party UNIQUE (party_id)
);

COMMENT ON COLUMN pan_details.pan_number IS
    'AES-GCM ciphertext. Never plaintext. Masked in API responses.';

-- ---------------------------------------------------------------------------
-- Bank accounts
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS bank_accounts (
    id                  uuid         PRIMARY KEY,
    party_id            uuid         NOT NULL,
    account_holder_name varchar(255) NOT NULL,
    bank_name           varchar(255) NOT NULL,
    account_number      text         NOT NULL,
    ifsc_code           varchar(11)  NOT NULL,
    is_primary          boolean      NOT NULL DEFAULT false,
    status              varchar(255) NOT NULL,
    created_at          timestamp with time zone,
    updated_at          timestamp with time zone
);

COMMENT ON COLUMN bank_accounts.account_number IS
    'AES-GCM ciphertext. Never plaintext. Masked in API responses.';

CREATE INDEX IF NOT EXISTS idx_bank_account_party ON bank_accounts (party_id);

-- The concurrency backstop for "at most one primary account per party".
-- BankAccountService clears the previous primary in the same transaction; two
-- racing transactions both fail here rather than leaving two primaries.
CREATE UNIQUE INDEX IF NOT EXISTS uk_bank_account_primary
    ON bank_accounts (party_id)
    WHERE is_primary;

-- ---------------------------------------------------------------------------
-- Demat accounts
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS demat_accounts (
    id                  uuid         PRIMARY KEY,
    party_id            uuid         NOT NULL,
    depository          varchar(255) NOT NULL,
    dp_id               varchar(20)  NOT NULL,
    client_id           varchar(20)  NOT NULL,
    account_holder_name varchar(255) NOT NULL,
    status              varchar(255) NOT NULL,
    created_at          timestamp with time zone,
    updated_at          timestamp with time zone
);

COMMENT ON TABLE demat_accounts IS
    'No uniqueness constraint on (depository, dp_id, client_id): joint and corporate holdings '
    'can legitimately appear on more than one party record.';

CREATE INDEX IF NOT EXISTS idx_demat_account_party ON demat_accounts (party_id);

-- ---------------------------------------------------------------------------
-- Foreign keys (idempotent: the tables above may pre-exist from ddl-auto=update)
-- ---------------------------------------------------------------------------

ALTER TABLE pan_details      DROP CONSTRAINT IF EXISTS fk_pan_details_party;
ALTER TABLE pan_details      ADD CONSTRAINT fk_pan_details_party
    FOREIGN KEY (party_id) REFERENCES parties (id);

ALTER TABLE bank_accounts    DROP CONSTRAINT IF EXISTS fk_bank_accounts_party;
ALTER TABLE bank_accounts    ADD CONSTRAINT fk_bank_accounts_party
    FOREIGN KEY (party_id) REFERENCES parties (id);

ALTER TABLE demat_accounts   DROP CONSTRAINT IF EXISTS fk_demat_accounts_party;
ALTER TABLE demat_accounts   ADD CONSTRAINT fk_demat_accounts_party
    FOREIGN KEY (party_id) REFERENCES parties (id);

-- ---------------------------------------------------------------------------
-- Backfill clients -> parties
--
-- A temp mapping avoids reusing row ids across tables and keeps the script
-- re-runnable. Existing names, contact details and addresses are preserved;
-- the identity type is assumed COMPANY for a back-office client record and can
-- be corrected by an admin afterwards.
-- ---------------------------------------------------------------------------

ALTER TABLE clients ADD COLUMN IF NOT EXISTS party_id uuid;

CREATE TEMP TABLE client_party_map ON COMMIT DROP AS
SELECT id AS client_id, gen_random_uuid() AS party_id
FROM clients
WHERE party_id IS NULL;

INSERT INTO parties (id, party_type, legal_name, contact_email, mobile_number, address, created_at, updated_at)
SELECT m.party_id,
       'COMPANY',
       c.name,
       c.email,
       c.mobile_number,
       c.address,
       COALESCE(c.created_at, now()),
       COALESCE(c.updated_at, now())
FROM clients c
JOIN client_party_map m ON m.client_id = c.id;

UPDATE clients c
SET party_id = m.party_id
FROM client_party_map m
WHERE c.id = m.client_id;

ALTER TABLE clients DROP CONSTRAINT IF EXISTS fk_clients_party;
ALTER TABLE clients ADD CONSTRAINT fk_clients_party
    FOREIGN KEY (party_id) REFERENCES parties (id);
ALTER TABLE clients ALTER COLUMN party_id SET NOT NULL;

-- ---------------------------------------------------------------------------
-- Backfill customer profiles -> parties
--
-- Each profile gets its own party. Profiles are never merged with each other
-- or with clients on matching name, email or phone: that would invent an
-- identity match. Linking is the explicit admin operation on
-- POST /api/admin/clients/{id}/link-party, and it requires a verified PAN.
-- ---------------------------------------------------------------------------

ALTER TABLE customer_profiles ADD COLUMN IF NOT EXISTS party_id uuid;

CREATE TEMP TABLE customer_party_map ON COMMIT DROP AS
SELECT id AS profile_id, gen_random_uuid() AS party_id
FROM customer_profiles
WHERE party_id IS NULL;

INSERT INTO parties (id, party_type, legal_name, contact_email, mobile_number, address, created_at, updated_at)
SELECT m.party_id,
       CASE cp.user_type
           WHEN 'CORPORATE'        THEN 'COMPANY'
           WHEN 'LLP'              THEN 'LLP'
           WHEN 'PARTNERSHIP_FIRM' THEN 'PARTNERSHIP'
           WHEN 'TRUST'            THEN 'TRUST'
           ELSE 'INDIVIDUAL'
       END,
       COALESCE(NULLIF(TRIM(CONCAT_WS(' ', u.first_name, u.last_name)), ''), 'Customer ' || u.id),
       u.email,
       u.mobile_number,
       NULL,
       COALESCE(cp.created_at, now()),
       COALESCE(cp.updated_at, now())
FROM customer_profiles cp
JOIN users u ON u.id = cp.user_id
JOIN customer_party_map m ON m.profile_id = cp.id;

UPDATE customer_profiles cp
SET party_id = m.party_id
FROM customer_party_map m
WHERE cp.id = m.profile_id;

ALTER TABLE customer_profiles DROP CONSTRAINT IF EXISTS fk_customer_profiles_party;
ALTER TABLE customer_profiles ADD CONSTRAINT fk_customer_profiles_party
    FOREIGN KEY (party_id) REFERENCES parties (id);
ALTER TABLE customer_profiles ALTER COLUMN party_id SET NOT NULL;

COMMIT;

-- Verification, after running:
--
--   -- every client and profile points at a party
--   SELECT count(*) FROM clients c LEFT JOIN parties p ON p.id = c.party_id WHERE p.id IS NULL;              -- expect 0
--   SELECT count(*) FROM customer_profiles cp LEFT JOIN parties p ON p.id = cp.party_id WHERE p.id IS NULL;  -- expect 0
--
--   -- at most one primary per party
--   SELECT party_id, count(*) FROM bank_accounts WHERE is_primary GROUP BY party_id HAVING count(*) > 1;    -- expect 0 rows
--
--   -- PAN is ciphertext, never a 10-character PAN
--   SELECT count(*) FROM pan_details WHERE pan_number ~ '^[A-Z]{5}[0-9]{4}[A-Z]$';                          -- expect 0
