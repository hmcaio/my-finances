-- F038: Investment opening positions (ADR 0033).
--
-- A holding's starting quantity and average unit price, recorded without moving cash. At most one
-- per holding (UNIQUE on holding_id). It counts like a BUY line dated as_of_date in every figure
-- derived from trade lines. The current value is deliberately not stored here: it is an optional
-- input that records the holding's investment_snapshots row for as_of_date, so the snapshot stays
-- the sole source of value (ADR 0012).
--
-- quantity and unit_price are numeric(19,8), the same deliberate exception to the numeric(19,2)
-- money rule as transfer_trade_lines (fractional units, sub-cent prices); both are backed by a
-- CHECK > 0 as well as the DTO and the domain constructor.
--
-- No seed, no backfill, and no ON DELETE CASCADE: RealHasHoldingHistoryChecker reports a holding
-- with a position as having history, so a hard delete is a clean 409 rather than an FK violation.

CREATE TABLE investment_opening_positions (
    id                uuid PRIMARY KEY,
    holding_id        uuid NOT NULL REFERENCES investment_holdings (id),
    as_of_date        date NOT NULL,
    quantity          numeric(19,8) NOT NULL,
    unit_price        numeric(19,8) NOT NULL,
    created_at        timestamptz NOT NULL,
    last_modified_at  timestamptz NOT NULL,
    CONSTRAINT uq_investment_opening_positions_holding UNIQUE (holding_id),
    CONSTRAINT chk_investment_opening_positions_quantity_positive CHECK (quantity > 0),
    CONSTRAINT chk_investment_opening_positions_unit_price_positive CHECK (unit_price > 0)
);
