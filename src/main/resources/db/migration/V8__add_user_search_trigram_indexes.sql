-- Make the admin customer search able to use an index.
--
-- WHAT IT FIXES. GET /api/admin/users?search= turns into
--
--   WHERE lower(u.first_name) LIKE lower('%term%')
--      OR lower(u.last_name)  LIKE lower('%term%')
--      OR lower(u.email)      LIKE lower('%term%')
--
-- (UserRepository.searchUsers). A pattern that begins with a wildcard cannot be
-- answered by a B-tree: there is no prefix to descend. None of the indexes the
-- users table already carries can help either — idx_user_email and
-- idx_user_mobile_number are on the raw columns, and the query wraps every
-- column in lower() before comparing, so the indexed expression is not the one
-- being matched. PostgreSQL therefore reads every row, three comparisons per
-- row, and Spring Data then runs the count query for the page and does it a
-- second time. Trigrams are what makes this shape of query indexable: a GIN
-- index over the trigrams of lower(column) can answer a %term% LIKE directly.
--
-- WHAT IT DOES NOT DO. No table is rewritten, no column changes type, and no
-- row is read or written. Only three indexes are added, plus the extension that
-- supplies the operator class. The result set of any search is identical to
-- before; this changes how the planner reaches it, not what it finds.
--
-- THIS NEEDS pg_trgm. It is not installed on this database yet (it is available
-- — `SELECT * FROM pg_available_extensions WHERE name = 'pg_trgm'` lists it at
-- 1.6 with installed_version null). CREATE EXTENSION normally needs a role with
-- rights on the extension, which on a managed PostgreSQL means the database
-- owner — the same credentials the application connects with, in this setup.
--
-- THE LIMITS, SO NOBODY EXPECTS MORE THAN THIS GIVES. Trigram matching needs at
-- least three characters to have a trigram to look up, so searches of one or
-- two characters still fall back to reading the table. That is inherent to the
-- technique, not a misconfiguration, and one- or two-character searches are not
-- a useful thing for an administrator to type. Below roughly a thousand rows the
-- planner will keep choosing a sequential scan anyway, because with so few pages
-- it is genuinely cheaper — this migration buys nothing until the table grows,
-- and it costs three indexes to maintain on every write until then.
--
-- A NOTE ON HOW THIS REACHES A DATABASE, because right now it does not. The
-- build declares org.flywaydb:flyway-core but not Spring Boot's own
-- spring-boot-flyway module. Boot 4 moved Flyway auto-configuration into that
-- module, so with flyway-core alone no FlywayAutoConfiguration is contributed:
-- Flyway never starts, and there is no flyway_schema_history table in the
-- schema. Every file in this directory, this one included, is therefore
-- currently inert — the dev schema is maintained by spring.jpa.hibernate.ddl-auto
-- (update in dev, validate in prod), not by migrations. Until spring-boot-flyway
-- is added and the existing history is reconciled, run the statements below by
-- hand. The IF NOT EXISTS clauses make that safe to repeat, and make it a no-op
-- if Flyway is later switched on and replays this file.
--
-- Applied as a single transaction, like V1 through V7. PostgreSQL's DDL is
-- transactional, so a failure leaves the schema exactly as it was. CREATE INDEX
-- without CONCURRENTLY takes an ACCESS EXCLUSIVE lock on users for the duration,
-- which blocks reads as well as writes; at the size this table is now that is
-- momentary, but on a production table that has grown, run the three CREATE
-- INDEX statements there outside this transaction with CONCURRENTLY instead.
-- CREATE EXTENSION cannot be run CONCURRENTLY.

BEGIN;

-- Supplies gin_trgm_ops, the operator class the indexes below are built with.
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- One index per column searchUsers matches on, over the same expression the
-- query uses. The expression must match lower(<column>) exactly: an index on
-- first_name, or on lower(first_name) with a different name, would not be
-- eligible for the lower(...) LIKE lower(...) predicate above.
CREATE INDEX IF NOT EXISTS idx_users_first_name_trgm
    ON users USING gin (lower(first_name) gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_users_last_name_trgm
    ON users USING gin (lower(last_name) gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_users_email_trgm
    ON users USING gin (lower(email) gin_trgm_ops);

COMMIT;

-- ---------------------------------------------------------------------------
-- Verification, after running:
--
--   -- the extension is installed:
--   SELECT extname, extversion FROM pg_extension WHERE extname = 'pg_trgm';
--   -- expect: pg_trgm | 1.6
--
--   -- all three indexes exist:
--   SELECT indexname FROM pg_indexes
--   WHERE tablename = 'users' AND indexname LIKE '%_trgm';
--   -- expect: idx_users_email_trgm, idx_users_first_name_trgm,
--   --         idx_users_last_name_trgm
--
--   -- and the planner can actually use one. On a small table it will still
--   -- choose a sequential scan, which is correct at that size; the index only
--   -- pays off once the table is large enough for the scan to hurt:
--   EXPLAIN ANALYZE
--   SELECT * FROM users WHERE lower(first_name) LIKE '%karan%';
-- ---------------------------------------------------------------------------
