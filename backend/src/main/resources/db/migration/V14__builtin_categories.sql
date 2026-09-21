-- Built-in categories: "Other Expense" (EXPENSE) and "Other Income" (INCOME) can be renamed but
-- never deleted (issue #29), the same idea as F017's "No institution" (V12).
--
-- Unlike V12, the rows already exist: V2 seeded `Other` and `Other Income` as ordinary rows, and
-- the user may since have renamed, deleted or shadowed them. So this migration ADOPTS rows rather
-- than only inserting, and must never fail on the global `UNIQUE (name)` (V10, shared by both types).
--
-- Steps, in order:
--   1. add `categories.built_in` and a partial unique index: at most one built-in row PER TYPE
--      (V12 has one for the whole table; here the constant is the type);
--   2. EXPENSE:
--        a. a row already named "Other Expense" is flagged;
--        b. otherwise the seeded `Other` is flagged and renamed to "Other Expense" - unless an
--           INCOME row holds that name, in which case it keeps `Other`;
--        c. otherwise (the seed was renamed or deleted) a fresh built-in row is inserted, named
--           "Other Expense (built-in)" if an INCOME row holds "Other Expense";
--   3. INCOME: a row named "Other Income" is flagged, else a fresh one is inserted (named
--      "Other Income (built-in)" if an EXPENSE row holds the name).
--
-- A seed row the user renamed can't be told apart from any other category, so it is left alone as
-- an ordinary row and a fresh built-in row is created next to it. Ids are `gen_random_uuid()` as in
-- V2/V12 (a one-time migration id, not an application id - ADR 0005).
--
-- Not reversible by Flyway in the strict sense (step 2b renames a row), but nothing else is
-- dropped or rewritten: the column and index can simply be removed.

ALTER TABLE categories ADD COLUMN built_in boolean NOT NULL DEFAULT false;

CREATE UNIQUE INDEX uq_categories_single_built_in_per_type ON categories (type) WHERE built_in;

-- EXPENSE, a: the user (or a previous run) already has a row with the final name.
UPDATE categories
SET built_in = true, last_modified_at = now()
WHERE type = 'EXPENSE' AND name = 'Other Expense';

-- EXPENSE, b: adopt the seeded `Other`, renaming it when the name is free.
UPDATE categories
SET built_in = true,
    name = CASE
               WHEN EXISTS (SELECT 1 FROM categories WHERE name = 'Other Expense') THEN name
               ELSE 'Other Expense'
           END,
    last_modified_at = now()
WHERE type = 'EXPENSE' AND name = 'Other'
  AND NOT EXISTS (SELECT 1 FROM categories WHERE type = 'EXPENSE' AND built_in);

-- EXPENSE, c: nothing to adopt.
INSERT INTO categories (id, name, type, built_in, created_at, last_modified_at)
SELECT gen_random_uuid(),
       CASE
           WHEN EXISTS (SELECT 1 FROM categories WHERE name = 'Other Expense')
               THEN 'Other Expense (built-in)'
           ELSE 'Other Expense'
       END,
       'EXPENSE', true, now(), now()
WHERE NOT EXISTS (SELECT 1 FROM categories WHERE type = 'EXPENSE' AND built_in);

-- INCOME: adopt "Other Income" if it is still an INCOME row.
UPDATE categories
SET built_in = true, last_modified_at = now()
WHERE type = 'INCOME' AND name = 'Other Income';

INSERT INTO categories (id, name, type, built_in, created_at, last_modified_at)
SELECT gen_random_uuid(),
       CASE
           WHEN EXISTS (SELECT 1 FROM categories WHERE name = 'Other Income')
               THEN 'Other Income (built-in)'
           ELSE 'Other Income'
       END,
       'INCOME', true, now(), now()
WHERE NOT EXISTS (SELECT 1 FROM categories WHERE type = 'INCOME' AND built_in);
