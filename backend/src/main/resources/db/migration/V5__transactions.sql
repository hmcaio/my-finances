-- F004: Transactions.
--
-- Creates the `transactions` table (PRD S5.3, F004 spec) - the core ledger entry. `amount` is
-- always stored positive; whether it increases or decreases an account's balance is derived from
-- `type` and the account's own `type` (F003) at read time (AccountBalanceQuery), not stored here.
--
-- Numbered V5, not V4: F004's spec/plan originally said `V4__transactions.sql`, but F003 already
-- claimed `V4__accounts.sql` (after F002's own out-of-band `V3__bound_name_column_lengths.sql`
-- claimed `V3`) by the time this feature was built - same renumbering story as F003's own V3->V4
-- note in its spec.md.
--
-- amount uses numeric(19,2), matching the currency-minor-unit precision established by
-- V4__accounts.sql's opening_balance column.
--
-- recurring_template_version_id has no FK yet - F007 (RecurringTemplate) doesn't exist yet, so
-- there's no `recurring_template_versions` table to reference. The column is added now, nullable
-- and unpopulated, so F007 doesn't need its own migration just to add it (F004 spec).
--
-- Indexes on account_id, category_id, and date: all three are filter/aggregation dimensions used
-- by this feature's own filtered list endpoint and by F006 (budgets)/F010/F013's reporting.

CREATE TABLE transactions (
    id                             uuid PRIMARY KEY,
    date                           date NOT NULL,
    amount                         numeric(19,2) NOT NULL,
    category_id                    uuid NOT NULL REFERENCES categories(id),
    type                           text NOT NULL CHECK (type IN ('INCOME', 'EXPENSE')),
    account_id                     uuid NOT NULL REFERENCES accounts(id),
    payment_method_id              uuid NOT NULL REFERENCES payment_methods(id),
    recurring_template_version_id  uuid,
    note                           text,
    created_at                     timestamptz NOT NULL,
    last_modified_at               timestamptz NOT NULL
);

CREATE INDEX idx_transactions_account_id ON transactions(account_id);
CREATE INDEX idx_transactions_category_id ON transactions(category_id);
CREATE INDEX idx_transactions_date ON transactions(date);
