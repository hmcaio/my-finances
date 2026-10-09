-- F027 follow-up (ADR 0024): V21's backfill gave a pre-existing trade a `transfer_trade_lines` row
-- whenever it had a product tag, quantity and unit price - but didn't touch `transfers.taxes`. A
-- trade recorded without any tax figure (legal under the old all-optional InvestmentTradeDetails)
-- kept `taxes IS NULL`, so it now has a non-empty TradeConfirmation with null taxes - violating
-- `Transfer`'s "a tradeConfirmation requires taxes" invariant and crashing with a NullPointerException
-- the moment that row is loaded (`Transfer.reconstitute`). Defaulting to 0 matches "no tax was
-- recorded for this trade", the same meaning `null` had before this feature.
UPDATE transfers
SET taxes = 0
WHERE taxes IS NULL
  AND id IN (SELECT DISTINCT transfer_id FROM transfer_trade_lines);
