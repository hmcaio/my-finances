-- Running-balance queries (AccountBalanceQuery.balanceAsOf, NetWorthQuery) filter on account and
-- date together; the existing single-column indexes make Postgres do a bitmap AND of two separate
-- index scans instead of one composite index scan.
CREATE INDEX idx_transactions_account_id_date ON transactions (account_id, date);
CREATE INDEX idx_transfers_from_account_id_date ON transfers (from_account_id, date);
CREATE INDEX idx_transfers_to_account_id_date ON transfers (to_account_id, date);
