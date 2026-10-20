-- F008: Investment accounts, products and taxonomy (ADR 0012).
--
-- 1. `accounts` becomes able to hold an INVESTMENT account: the `type` CHECK also allows it, and
--    `opening_balance`/`opening_balance_date` become nullable - an investment account has neither,
--    its value comes only from snapshots (F009). A new CHECK ties the two together: they are NULL
--    exactly when type = 'INVESTMENT' and both present for every other type. Existing rows stay
--    valid (none is INVESTMENT, all have both values).
-- 2. `investment_categories` (top level) and `investment_subcategories` (under one category), both
--    seeded with Brazilian defaults, editable afterwards.
-- 3. `investment_products`: a holding inside an INVESTMENT account, classified by a category and an
--    optional sub-category. A composite foreign key guarantees that a sub-category, when set,
--    belongs to the product's own category. Postgres skips a composite foreign key when any of its
--    columns is NULL (default MATCH SIMPLE), so a product without a sub-category is valid. That only
--    an INVESTMENT account may own products is enforced in the service: a CHECK can't look across
--    tables.
--
-- Names are bounded varchar(100) (TextFieldConstraints.MAX_NAME_LENGTH) and unique in their scope
-- from the start - globally for categories, per category for sub-categories ("ETFs" exists under
-- both Variable Income and International), per account for products (the same instrument held at
-- two brokers is normal) - the V3/V10 lessons, applied up front.
--
-- The seed uses gen_random_uuid() (built into Postgres 13+) as V2 and V12 do: a one-time migration
-- id, not an application id - ids created through the app come from IdGenerator (ADR 0005).
--
-- The CHECK on accounts is written out as two branches rather than
-- `(type = 'INVESTMENT') = (opening_balance IS NULL AND opening_balance_date IS NULL)`: the compact
-- form lets a non-INVESTMENT row through when exactly one of the two values is NULL (false = false).
--
-- `accounts.type` was declared inline and unnamed in V4, so Postgres named its CHECK
-- `accounts_type_check`.

-- 1. accounts

ALTER TABLE accounts DROP CONSTRAINT accounts_type_check;
ALTER TABLE accounts ADD CONSTRAINT chk_accounts_type
    CHECK (type IN ('CHECKING', 'SAVINGS', 'CASH_WALLET', 'CREDIT_CARD', 'INVESTMENT'));

ALTER TABLE accounts ALTER COLUMN opening_balance DROP NOT NULL;
ALTER TABLE accounts ALTER COLUMN opening_balance_date DROP NOT NULL;

ALTER TABLE accounts ADD CONSTRAINT chk_accounts_opening_values_by_type CHECK (
    (type = 'INVESTMENT' AND opening_balance IS NULL AND opening_balance_date IS NULL)
    OR (type <> 'INVESTMENT' AND opening_balance IS NOT NULL AND opening_balance_date IS NOT NULL)
);

-- 2. taxonomy

CREATE TABLE investment_categories (
    id                uuid PRIMARY KEY,
    name              varchar(100) NOT NULL,
    created_at        timestamptz NOT NULL,
    last_modified_at  timestamptz NOT NULL,
    CONSTRAINT uq_investment_categories_name UNIQUE (name)
);

CREATE TABLE investment_subcategories (
    id                      uuid PRIMARY KEY,
    investment_category_id  uuid NOT NULL REFERENCES investment_categories (id),
    name                    varchar(100) NOT NULL,
    created_at              timestamptz NOT NULL,
    last_modified_at        timestamptz NOT NULL,
    CONSTRAINT uq_investment_subcategories_category_name UNIQUE (investment_category_id, name),
    -- Target of investment_products' composite foreign key.
    CONSTRAINT uq_investment_subcategories_id_category UNIQUE (id, investment_category_id)
);

-- 3. products

CREATE TABLE investment_products (
    id                         uuid PRIMARY KEY,
    account_id                 uuid NOT NULL REFERENCES accounts (id),
    investment_category_id     uuid NOT NULL REFERENCES investment_categories (id),
    investment_subcategory_id  uuid,
    name                       varchar(100) NOT NULL,
    closed_date                date,
    created_at                 timestamptz NOT NULL,
    last_modified_at           timestamptz NOT NULL,
    CONSTRAINT uq_investment_products_account_name UNIQUE (account_id, name),
    CONSTRAINT fk_investment_products_subcategory_of_category
        FOREIGN KEY (investment_subcategory_id, investment_category_id)
        REFERENCES investment_subcategories (id, investment_category_id)
);

CREATE INDEX idx_investment_products_account_id ON investment_products (account_id);
CREATE INDEX idx_investment_products_category_id ON investment_products (investment_category_id);
CREATE INDEX idx_investment_products_subcategory_id ON investment_products (investment_subcategory_id);

-- Seed: Brazilian defaults, English labels with the Portuguese instrument names kept.

INSERT INTO investment_categories (id, name, created_at, last_modified_at) VALUES
    (gen_random_uuid(), 'Fixed Income',          now(), now()),
    (gen_random_uuid(), 'Variable Income',       now(), now()),
    (gen_random_uuid(), 'Funds',                 now(), now()),
    (gen_random_uuid(), 'Pension (Previdência)', now(), now()),
    (gen_random_uuid(), 'International',         now(), now()),
    (gen_random_uuid(), 'Crypto',                now(), now()),
    (gen_random_uuid(), 'Other',                 now(), now());

INSERT INTO investment_subcategories (id, investment_category_id, name, created_at, last_modified_at)
SELECT gen_random_uuid(), category.id, seed.name, now(), now()
FROM (VALUES
    ('Fixed Income',          'Tesouro Selic'),
    ('Fixed Income',          'Tesouro IPCA+'),
    ('Fixed Income',          'Tesouro Prefixado'),
    ('Fixed Income',          'CDB'),
    ('Fixed Income',          'LCI'),
    ('Fixed Income',          'LCA'),
    ('Fixed Income',          'CRI'),
    ('Fixed Income',          'CRA'),
    ('Fixed Income',          'Debentures'),
    ('Fixed Income',          'Savings (Poupança)'),
    ('Variable Income',       'Stocks (Ações)'),
    ('Variable Income',       'REITs (FIIs)'),
    ('Variable Income',       'ETFs'),
    ('Variable Income',       'BDRs'),
    ('Funds',                 'Fixed Income Funds'),
    ('Funds',                 'Multimercado'),
    ('Funds',                 'Equity Funds'),
    ('Pension (Previdência)', 'PGBL'),
    ('Pension (Previdência)', 'VGBL'),
    ('International',         'Stocks'),
    ('International',         'ETFs'),
    ('International',         'Bonds')
) AS seed (category_name, name)
JOIN investment_categories category ON category.name = seed.category_name;
