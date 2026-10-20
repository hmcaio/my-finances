-- F026: FII portfolio - segments, ticker/segment on investment_products, the dividend category,
-- investment_holding_id on transactions, and the allocation plan (ADR 0023).
--
-- 1. `investment_segments`: a new flat taxonomy, same shape as `investment_categories` - name
--    bounded and UNIQUE from the start (the V3/V10 lesson).
-- 2. `investment_products`: add ticker (reuses MAX_NAME_LENGTH, same flat-taxonomy-style name
--    field convention) and segment_id (nullable FK, indexed) - both optional and generalized,
--    usable by any product even though only the FII page manages them for v1.
-- 3. `categories.dividend_category`: a third, independent flag alongside `built_in` (V14) and
--    `fuel_category` (V18) - at most one row (unscoped by type, same as fuel_category). Seed:
--    insert a fresh INCOME row named "Dividends" - no adopt-existing step like V18's fuel-category
--    seed, since silently repurposing a category the user may already use for something else is
--    worse than a harmless name collision. Unlike V18's "Fuel" seed, this can't just insert
--    unconditionally: `categories.name` is globally UNIQUE (V10), so a pre-existing "Dividends"
--    row (of any type) would make a second insert of that exact name fail the whole migration,
--    not just collide semantically. Falls back to "Dividends (FII)" only in that case - still a
--    fresh row, never adopting/renaming whatever already owns the plain name.
-- 4. `transactions.investment_holding_id`: nullable FK to investment_holdings. No CHECK enforcing
--    the XOR with the dividend category - unlike FuelDetails's checks, that invariant needs a
--    cross-table lookup (categories.dividend_category) a plain CHECK can't express; enforced at
--    the application layer only (TransactionService), same as every other cross-aggregate
--    invariant in this codebase.
-- 5-7. `allocation_plans`/`allocation_plan_versions`/`allocation_plan_entries`: versioned like
--    budgets/budget_versions (V8, ADR 0002). allocation_plans is a singleton marker row (no other
--    columns). allocation_plan_entries.target_percentage is numeric(5,2) - a deliberate new
--    exception to the root CLAUDE.md numeric(19,2) money-column rule (it isn't money), bounding a
--    percentage to 0.01-999.99, generous beyond the valid 0-100 range (the request DTOs enforce
--    the real bound with @DecimalMin/@DecimalMax). The exactly-100%-sum invariant is an
--    application-layer check (a DB CHECK can't aggregate across sibling rows).
--
-- Numbered V20: V19 (ledger composite indexes) was the highest when this was written.

-- 1. investment_segments

CREATE TABLE investment_segments (
    id                uuid PRIMARY KEY,
    name              varchar(100) NOT NULL,
    created_at        timestamptz NOT NULL,
    last_modified_at  timestamptz NOT NULL,
    CONSTRAINT uq_investment_segments_name UNIQUE (name)
);

-- 2. investment_products: ticker/segment_id

ALTER TABLE investment_products
    ADD COLUMN ticker varchar(100),
    ADD COLUMN segment_id uuid REFERENCES investment_segments (id);

CREATE INDEX idx_investment_products_segment_id ON investment_products (segment_id);

-- 3. categories.dividend_category + seed

ALTER TABLE categories ADD COLUMN dividend_category boolean NOT NULL DEFAULT false;

CREATE UNIQUE INDEX uq_categories_single_dividend_category ON categories (dividend_category)
    WHERE dividend_category;

INSERT INTO categories (id, name, type, dividend_category, created_at, last_modified_at)
SELECT gen_random_uuid(), 'Dividends', 'INCOME', true, now(), now()
WHERE NOT EXISTS (SELECT 1 FROM categories WHERE name = 'Dividends');

-- Fallback only when the plain name is already taken by an unrelated row (categories.name is
-- globally UNIQUE, V10) - still a fresh insert, never adopting the existing row.
INSERT INTO categories (id, name, type, dividend_category, created_at, last_modified_at)
SELECT gen_random_uuid(), 'Dividends (FII)', 'INCOME', true, now(), now()
WHERE NOT EXISTS (SELECT 1 FROM categories WHERE dividend_category);

-- 4. transactions.investment_holding_id

ALTER TABLE transactions
    ADD COLUMN investment_holding_id uuid REFERENCES investment_holdings (id);

CREATE INDEX idx_transactions_investment_holding_id ON transactions (investment_holding_id);

-- 5. allocation_plans (singleton marker row)

CREATE TABLE allocation_plans (
    id                uuid PRIMARY KEY,
    created_at        timestamptz NOT NULL,
    last_modified_at  timestamptz NOT NULL
);

-- 6. allocation_plan_versions

CREATE TABLE allocation_plan_versions (
    id                uuid PRIMARY KEY,
    plan_id           uuid NOT NULL REFERENCES allocation_plans (id),
    effective_from    date NOT NULL,
    created_at        timestamptz NOT NULL,
    last_modified_at  timestamptz NOT NULL,
    CONSTRAINT uq_allocation_plan_versions_plan_effective_from UNIQUE (plan_id, effective_from)
);

CREATE INDEX idx_allocation_plan_versions_plan_id ON allocation_plan_versions (plan_id);

-- 7. allocation_plan_entries

CREATE TABLE allocation_plan_entries (
    id                     uuid PRIMARY KEY,
    version_id             uuid NOT NULL REFERENCES allocation_plan_versions (id),
    investment_product_id  uuid NOT NULL REFERENCES investment_products (id),
    target_percentage      numeric(5,2) NOT NULL,
    created_at             timestamptz NOT NULL,
    last_modified_at       timestamptz NOT NULL,
    CONSTRAINT uq_allocation_plan_entries_version_product UNIQUE (version_id, investment_product_id),
    CONSTRAINT chk_allocation_plan_entries_target_percentage_positive CHECK (target_percentage > 0)
);

CREATE INDEX idx_allocation_plan_entries_version_id ON allocation_plan_entries (version_id);
