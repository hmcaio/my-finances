-- F022: Investment holdings as the many-to-many link between a product and its account(s), ADR 0020.
--
-- 1. `investment_holdings`: id, product_id, account_id (both indexed), closed_date, additional_notes,
--    unique on (product_id, account_id) - a product has at most one holding per account.
-- 2. Backfill: one investment_holdings row per existing investment_products row, copying its
--    account_id/closed_date - unambiguous today, since the model is currently 1:1.
-- 3. investment_snapshots: add holding_id, backfill it via a join to the holding just created for
--    that product, make it NOT NULL, drop UNIQUE (product_id, date), add UNIQUE (holding_id, date),
--    drop product_id.
-- 4. investment_products: add additional_notes, drop UNIQUE (account_id, name), add UNIQUE (name),
--    drop closed_date, drop account_id (its FK/index first).
--
-- Numbered V17: V16 (budget tombstone) was the highest when this was written.

-- 1. investment_holdings

CREATE TABLE investment_holdings (
    id                uuid PRIMARY KEY,
    product_id        uuid NOT NULL REFERENCES investment_products (id),
    account_id        uuid NOT NULL REFERENCES accounts (id),
    closed_date       date,
    additional_notes  varchar(500),
    created_at        timestamptz NOT NULL,
    last_modified_at  timestamptz NOT NULL,
    CONSTRAINT uq_investment_holdings_product_account UNIQUE (product_id, account_id)
);

CREATE INDEX idx_investment_holdings_product_id ON investment_holdings (product_id);
CREATE INDEX idx_investment_holdings_account_id ON investment_holdings (account_id);

-- 2. Backfill: one holding per existing product, carrying its account_id/closed_date.

INSERT INTO investment_holdings (id, product_id, account_id, closed_date, created_at, last_modified_at)
SELECT gen_random_uuid(), p.id, p.account_id, p.closed_date, now(), now()
FROM investment_products p;

-- 3. investment_snapshots: rekey product_id -> holding_id.

ALTER TABLE investment_snapshots ADD COLUMN holding_id uuid REFERENCES investment_holdings (id);

UPDATE investment_snapshots s
SET holding_id = h.id
FROM investment_holdings h
WHERE h.product_id = s.product_id;

ALTER TABLE investment_snapshots ALTER COLUMN holding_id SET NOT NULL;

ALTER TABLE investment_snapshots DROP CONSTRAINT uq_investment_snapshots_product_date;
ALTER TABLE investment_snapshots ADD CONSTRAINT uq_investment_snapshots_holding_date
    UNIQUE (holding_id, date);

ALTER TABLE investment_snapshots DROP COLUMN product_id;

CREATE INDEX idx_investment_snapshots_holding_id ON investment_snapshots (holding_id);

-- 4. investment_products: drop account_id/closed_date, add additional_notes, name goes global.

ALTER TABLE investment_products ADD COLUMN additional_notes varchar(500);

ALTER TABLE investment_products DROP CONSTRAINT uq_investment_products_account_name;
ALTER TABLE investment_products ADD CONSTRAINT uq_investment_products_name UNIQUE (name);

ALTER TABLE investment_products DROP COLUMN closed_date;

DROP INDEX idx_investment_products_account_id;
ALTER TABLE investment_products DROP COLUMN account_id;
