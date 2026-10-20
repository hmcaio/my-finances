-- F009: Investment snapshots and buy/sell trades (ADR 0012).
--
-- 1. `investment_snapshots`: the manually entered value of a product on a date, the sole source of
--    a product's current value. One per product per date (UNIQUE), so "latest snapshot" is never
--    ambiguous when a trade-time snapshot and a manual one share a day. balance is `>= 0`, not
--    `> 0` like the other amount columns: 0 is a legitimate value for a liquidated position (and
--    is what lets the product be closed).
-- 2. `transfers` becomes able to be a buy/sell: a nullable `investment_product_id` tags it with the
--    product, and `quantity`/`unit_price`/`taxes` record the trade (record-only, never used in a
--    balance - `amount` stays the cash that moved). quantity and unit_price are numeric(19,8), a
--    deliberate exception to the numeric(19,2) money rule (fractional units, crypto, sub-cent
--    prices); taxes and balance follow the rule. That an endpoint is an INVESTMENT account and that
--    the product belongs to it cannot be a CHECK (it spans tables) and stays in TransferService.
--    Existing rows stay valid: every new column is NULL.
--
-- Numbered V15: V13 is F008's migration and V14 the built-in categories.
--
-- The trade CHECKs mirror InvestmentTradeDetails/Transfer: positive quantity and unit price, both
-- present or both absent, non-negative taxes, and no trade data without a product. Every amount
-- rule is backed at all three layers (DTO, domain, DB) from the start - the V10 audit lesson.

CREATE TABLE investment_snapshots (
    id                uuid PRIMARY KEY,
    product_id        uuid NOT NULL REFERENCES investment_products (id),
    date              date NOT NULL,
    balance           numeric(19,2) NOT NULL,
    created_at        timestamptz NOT NULL,
    last_modified_at  timestamptz NOT NULL,
    CONSTRAINT uq_investment_snapshots_product_date UNIQUE (product_id, date),
    CONSTRAINT chk_investment_snapshots_balance_non_negative CHECK (balance >= 0)
);

ALTER TABLE transfers
    ADD COLUMN investment_product_id uuid REFERENCES investment_products (id),
    ADD COLUMN quantity numeric(19,8),
    ADD COLUMN unit_price numeric(19,8),
    ADD COLUMN taxes numeric(19,2);

ALTER TABLE transfers ADD CONSTRAINT chk_transfers_quantity_positive
    CHECK (quantity IS NULL OR quantity > 0);
ALTER TABLE transfers ADD CONSTRAINT chk_transfers_unit_price_positive
    CHECK (unit_price IS NULL OR unit_price > 0);
ALTER TABLE transfers ADD CONSTRAINT chk_transfers_taxes_non_negative
    CHECK (taxes IS NULL OR taxes >= 0);
ALTER TABLE transfers ADD CONSTRAINT chk_transfers_quantity_with_unit_price
    CHECK ((quantity IS NULL) = (unit_price IS NULL));
ALTER TABLE transfers ADD CONSTRAINT chk_transfers_trade_details_need_product
    CHECK (investment_product_id IS NOT NULL
           OR (quantity IS NULL AND unit_price IS NULL AND taxes IS NULL));

CREATE INDEX idx_transfers_investment_product_id ON transfers (investment_product_id);
