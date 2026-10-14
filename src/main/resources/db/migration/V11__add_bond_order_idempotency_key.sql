-- Record the client-supplied key that makes an order creation idempotent.
--
-- The admin order endpoint accepts an Idempotency-Key header so that a retried
-- request -- a double click, a flaky connection, a replayed curl -- answers with
-- the order the first request created instead of creating a second one for the
-- same bond and quantity.
--
-- The column is what a retry is looked up by; the unique index is what actually
-- guarantees it. Two copies of the same request that arrive at the same moment
-- both miss the lookup, and only one of them can then insert, so the pair is the
-- same belt-and-braces arrangement deal_confirmations already uses for its own
-- idempotency key.
--
-- NULLABLE, AND NULL FOR EVERY EXISTING ROW. A caller that sends no key is not
-- asking for idempotency, and PostgreSQL does not compare NULLs in a unique
-- index, so any number of keyless orders may exist for one customer while no two
-- may share a key. Back-filling a value would invent a key nobody sent and could
-- collide across rows.
--
-- Scoped to (customer_id, idempotency_key) rather than the key alone: two
-- admins may legitimately choose the same key, and one must never be handed the
-- other's order.
--
-- Applied as a single transaction, like the migrations before it. PostgreSQL's
-- DDL is transactional, so a failure leaves the schema as it was.
--
-- Why ddl-auto cannot do this: application-prod.yaml sets
-- spring.jpa.hibernate.ddl-auto=validate, so Hibernate checks the schema and
-- refuses to start rather than creating anything. This statement has to be
-- applied by hand before the new code is deployed.

BEGIN;

ALTER TABLE bond_orders
    ADD COLUMN idempotency_key varchar(255);

ALTER TABLE bond_orders
    ADD CONSTRAINT uk_order_customer_idempotency_key
    UNIQUE (customer_id, idempotency_key);

COMMIT;

-- ---------------------------------------------------------------------------
-- Verification, after running:
--
--   SELECT column_name, data_type, is_nullable, character_maximum_length
--   FROM information_schema.columns
--   WHERE table_name = 'bond_orders'
--     AND column_name = 'idempotency_key';
--   -- expect: idempotency_key | character varying | YES | 255
--
--   SELECT conname, pg_get_constraintdef(oid)
--   FROM pg_constraint
--   WHERE conname = 'uk_order_customer_idempotency_key';
--   -- expect: UNIQUE (customer_id, idempotency_key)
--
--   -- Every pre-existing order is keyless, not wrongly keyed:
--   SELECT count(*) FROM bond_orders WHERE idempotency_key IS NOT NULL;
--   -- expect 0
-- ---------------------------------------------------------------------------
