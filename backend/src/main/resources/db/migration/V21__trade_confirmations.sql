-- F027: Trade confirmations as multi-line transfers (ADR 0024).
--
-- 1. `transfer_trade_lines`: one row per product line of a trade confirmation (TradeConfirmationLine).
--    side/quantity/unit_price are mandatory - unlike the old all-optional InvestmentTradeDetails,
--    once lines are explicit a line with no quantity/price doesn't make sense. resulting_balance
--    stays nullable (not every line carries one) and close_holding defaults false.
-- 2. Backfill: one line per existing `transfers` row tagged with a product AND carrying both
--    quantity and unit_price - both are mandatory on the new line, so a pre-existing trade tagged
--    with a product but recorded by amount only (quantity/unit_price null, legal under the old
--    all-optional InvestmentTradeDetails) has no valid line representation under the new model and
--    is left as a plain transfer, silently losing its product tag. This matches ADR 0024's "no
--    backfill into the multi-line notes they actually came from" spirit - there is no way to
--    recover a line this app's own domain invariants would accept. `side` is derived the same way
--    `TransferService` already computed direction at request time: destination INVESTMENT account
--    = BUY, source = SELL.
-- 3. Drop the four flat trade columns/checks from `transfers`; `taxes` and its CHECK stay - the
--    confirmation's one aggregate tax figure, never apportioned per line (F027 spec).
--
-- Numbered V21: V20 (FII segments/allocation plan/dividends) was the highest when this was written.

CREATE TABLE transfer_trade_lines (
    id                 uuid PRIMARY KEY,
    transfer_id        uuid NOT NULL REFERENCES transfers (id) ON DELETE CASCADE,
    product_id         uuid NOT NULL REFERENCES investment_products (id),
    side               varchar(4) NOT NULL CHECK (side IN ('BUY', 'SELL')),
    quantity           numeric(19,8) NOT NULL CHECK (quantity > 0),
    unit_price         numeric(19,8) NOT NULL CHECK (unit_price > 0),
    resulting_balance  numeric(19,2) CHECK (resulting_balance >= 0),
    close_holding      boolean NOT NULL DEFAULT false,
    created_at         timestamptz NOT NULL,
    last_modified_at   timestamptz NOT NULL
);

CREATE INDEX idx_transfer_trade_lines_transfer_id ON transfer_trade_lines (transfer_id);
CREATE INDEX idx_transfer_trade_lines_product_id ON transfer_trade_lines (product_id);

INSERT INTO transfer_trade_lines (
    id, transfer_id, product_id, side, quantity, unit_price, resulting_balance, close_holding,
    created_at, last_modified_at)
SELECT
    gen_random_uuid(),
    t.id,
    t.investment_product_id,
    CASE WHEN a_to.type = 'INVESTMENT' THEN 'BUY' ELSE 'SELL' END,
    t.quantity,
    t.unit_price,
    NULL,
    false,
    now(),
    now()
FROM transfers t
JOIN accounts a_to ON a_to.id = t.to_account_id
WHERE t.investment_product_id IS NOT NULL
  AND t.quantity IS NOT NULL
  AND t.unit_price IS NOT NULL;

ALTER TABLE transfers DROP CONSTRAINT chk_transfers_quantity_positive;
ALTER TABLE transfers DROP CONSTRAINT chk_transfers_unit_price_positive;
ALTER TABLE transfers DROP CONSTRAINT chk_transfers_quantity_with_unit_price;
ALTER TABLE transfers DROP CONSTRAINT chk_transfers_trade_details_need_product;

DROP INDEX idx_transfers_investment_product_id;

ALTER TABLE transfers DROP COLUMN investment_product_id;
ALTER TABLE transfers DROP COLUMN quantity;
ALTER TABLE transfers DROP COLUMN unit_price;
