-- F005: Transfers.
--
-- Creates the `transfers` table (PRD S5.5/S6.2, F005 spec): movement of money between two of the
-- user's own accounts - most commonly paying a credit card statement from checking. Unlike
-- `transactions`, there's no category/type column - a transfer is never "categorized" and has no
-- budget impact (PRD S5.5).
--
-- Numbered V7, not V5: F005's spec/plan originally said `V5__transfers.sql`, but F004
-- (transactions), which landed after this spec was written, already claimed both `V5` (its main
-- table) and `V6` (its description/additional_notes follow-up) - same renumbering story as F003's
-- V3->V4 and F004's own V4->V5 notes in their spec.md files.
--
-- amount uses numeric(19,2), matching the currency-minor-unit precision established by
-- V4__accounts.sql's opening_balance / V5__transactions.sql's amount columns.
--
-- description/additional_notes are bounded varchar(150)/varchar(500) from the start
-- (TextFieldConstraints.MAX_DESCRIPTION_LENGTH/MAX_ADDITIONAL_NOTES_LENGTH), unlike transactions
-- which needed a later narrowing migration (V6) - transfers land after that convention was already
-- established.
--
-- Check constraint from_account_id <> to_account_id backs Transfer's domain-level invariant
-- (defense in depth, same spirit as the domain constructor's own check).
--
-- Indexes on from_account_id, to_account_id, and date: from/to are both queried independently by
-- AccountBalanceQuery's/the list endpoint's "accountId matches either side" filter (PRD S6.9), and
-- date is a filter dimension on the list endpoint, same as transactions.

CREATE TABLE transfers (
    id                 uuid PRIMARY KEY,
    date               date NOT NULL,
    from_account_id    uuid NOT NULL REFERENCES accounts(id),
    to_account_id      uuid NOT NULL REFERENCES accounts(id),
    amount             numeric(19,2) NOT NULL,
    description        varchar(150) NOT NULL,
    additional_notes   varchar(500),
    created_at         timestamptz NOT NULL,
    last_modified_at   timestamptz NOT NULL,
    CONSTRAINT chk_transfers_different_accounts CHECK (from_account_id <> to_account_id)
);

CREATE INDEX idx_transfers_from_account_id ON transfers(from_account_id);
CREATE INDEX idx_transfers_to_account_id ON transfers(to_account_id);
CREATE INDEX idx_transfers_date ON transfers(date);
