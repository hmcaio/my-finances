-- F017: Institutions.
--
-- Creates the `institutions` table (PRD S5.10, F017 spec), seeds the single built-in "No
-- institution" row, and replaces `accounts.institution` (optional free text, V4) with a required
-- `accounts.institution_id` foreign key. F008's `investment_accounts` table doesn't exist yet and
-- will declare its own `institution_id uuid NOT NULL REFERENCES institutions (id)` from day one.
--
-- Steps, in order:
--   1. create `institutions` - name bounded and UNIQUE from the start (the V3/V10 lessons), plus a
--      partial unique index so there can never be two built-in rows;
--   2. seed the built-in row (`gen_random_uuid()` as V2 does for its seeds: a one-time migration
--      id, not an application id - ids created through the app come from IdGenerator, ADR 0005);
--   3. backfill one institution per distinct existing `accounts.institution` value;
--   4. add `accounts.institution_id` (nullable for now) and point every account at its institution,
--      or at the built-in row when it had none;
--   5. make it NOT NULL and index it (FK lookups for the delete guard, group-by later);
--   6. drop `accounts.institution`.
--
-- NOT REVERSIBLE by Flyway: step 6 drops the free-text column. The data is preserved (it lives on
-- as the institution names and each account's foreign key), but nothing turns it back into text.
--
-- Backfill normalization is applied once, here. The ongoing uniqueness rule stays exact and
-- case-sensitive, like every other named entity:
--   * surrounding whitespace is trimmed; NULL and blank values create nothing;
--   * a value equal to "No institution" ignoring case creates nothing - it maps to the seeded row
--     instead, so the UNIQUE name cannot collide with it;
--   * values that differ only by case become ONE institution, spelled with the smallest variant
--     under the "C" collation (a deterministic choice that doesn't depend on the database's locale);
--   * accent variants (Itau with and without its accent) are NOT merged - there is no safe rule, and
--     the user can fix it in the UI by re-pointing accounts and deleting the extra institution.

CREATE TABLE institutions (
    id                uuid PRIMARY KEY,
    name              varchar(100) NOT NULL,
    built_in          boolean NOT NULL DEFAULT false,
    created_at        timestamptz NOT NULL,
    last_modified_at  timestamptz NOT NULL,
    CONSTRAINT uq_institutions_name UNIQUE (name)
);

-- At most one built-in row: every row indexes the same constant, and only built-in rows are indexed.
CREATE UNIQUE INDEX uq_institutions_single_built_in ON institutions ((true)) WHERE built_in;

INSERT INTO institutions (id, name, built_in, created_at, last_modified_at)
VALUES (gen_random_uuid(), 'No institution', true, now(), now());

INSERT INTO institutions (id, name, built_in, created_at, last_modified_at)
SELECT gen_random_uuid(), spelling, false, now(), now()
FROM (
    SELECT min(btrim(institution, E' \t\r\n') COLLATE "C") AS spelling
    FROM accounts
    WHERE btrim(institution, E' \t\r\n') <> ''
      AND lower(btrim(institution, E' \t\r\n')) <> 'no institution'
    GROUP BY lower(btrim(institution, E' \t\r\n'))
) distinct_institutions;

ALTER TABLE accounts ADD COLUMN institution_id uuid REFERENCES institutions (id);

UPDATE accounts
SET institution_id = matching.id
FROM institutions matching
WHERE NOT matching.built_in
  AND lower(matching.name) = lower(btrim(accounts.institution, E' \t\r\n'));

-- Everything still unassigned (NULL, blank, or "No institution" in any case) goes to the built-in row.
UPDATE accounts
SET institution_id = (SELECT id FROM institutions WHERE built_in)
WHERE institution_id IS NULL;

ALTER TABLE accounts ALTER COLUMN institution_id SET NOT NULL;

CREATE INDEX idx_accounts_institution_id ON accounts (institution_id);

ALTER TABLE accounts DROP COLUMN institution;
