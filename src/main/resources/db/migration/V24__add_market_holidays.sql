-- Exchange holiday calendar used when rolling T+1/T+2 value dates. A weekday on
-- this list settles no trade, so the settlement date moves to the next working
-- day, just as it does over a weekend.
--
-- Seeded with the 2026 calendar supplied by the business. Later years are added
-- as rows, not as migrations.
--
-- Run by hand, like V1-V23; see V12 for why Flyway does not apply these.
-- ddl-auto=validate in prod stops the application if the shape disagrees with
-- MarketHoliday.java.

BEGIN;

CREATE TABLE IF NOT EXISTS market_holidays (
    id uuid PRIMARY KEY,
    holiday_year integer NOT NULL,
    holiday_date date NOT NULL,
    name varchar(255) NOT NULL,
    CONSTRAINT uk_market_holidays_date UNIQUE (holiday_date)
);

INSERT INTO market_holidays (id, holiday_year, holiday_date, name) VALUES
    (gen_random_uuid(), 2026, DATE '2026-01-15', 'MCGM Election day'),
    (gen_random_uuid(), 2026, DATE '2026-01-26', 'Republic Day'),
    (gen_random_uuid(), 2026, DATE '2026-02-19', 'Chatrapati Shivaji Maharaj Jayanti'),
    (gen_random_uuid(), 2026, DATE '2026-03-03', 'Holi'),
    (gen_random_uuid(), 2026, DATE '2026-03-19', 'Gudhipadwa'),
    (gen_random_uuid(), 2026, DATE '2026-03-26', 'Shri Ram Navami'),
    (gen_random_uuid(), 2026, DATE '2026-03-31', 'Shri Mahavir Jayanti'),
    (gen_random_uuid(), 2026, DATE '2026-04-01', 'Annual Bank Closing'),
    (gen_random_uuid(), 2026, DATE '2026-04-03', 'Good Friday'),
    (gen_random_uuid(), 2026, DATE '2026-04-14', 'Dr. Baba Saheb Ambedkar Jayanti'),
    (gen_random_uuid(), 2026, DATE '2026-05-01', 'Maharashtra Day / Buddha Pournima'),
    (gen_random_uuid(), 2026, DATE '2026-05-28', 'Bakri Id'),
    (gen_random_uuid(), 2026, DATE '2026-06-26', 'Muharram'),
    (gen_random_uuid(), 2026, DATE '2026-08-26', 'Id-E-Milad'),
    (gen_random_uuid(), 2026, DATE '2026-09-14', 'Ganesh Chaturthi'),
    (gen_random_uuid(), 2026, DATE '2026-10-02', 'Mahatma Gandhi Jayanti'),
    (gen_random_uuid(), 2026, DATE '2026-10-20', 'Dussehra'),
    (gen_random_uuid(), 2026, DATE '2026-11-10', 'Diwali Balipratipada'),
    (gen_random_uuid(), 2026, DATE '2026-11-24', 'Prakash Gurpurb Sri Guru Nanak Dev'),
    (gen_random_uuid(), 2026, DATE '2026-12-25', 'Christmas')
ON CONFLICT (holiday_date) DO NOTHING;

COMMIT;

-- Verification, after running:
--
--   SELECT holiday_year, count(*) FROM market_holidays GROUP BY holiday_year;
--   -- expect 2026 | 20
