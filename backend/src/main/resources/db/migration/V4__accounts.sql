-- F003: Accounts.
--
-- Creates the `accounts` table (PRD S5.4, F003 spec). No seed data - unlike F002's predefined
-- categories/payment methods, accounts are entirely user-created (PRD S6.7's onboarding flow has
-- the user create their first one).
--
-- name/institution are bounded varchar(100) from the start (NameConstraints.MAX_NAME_LENGTH),
-- following the pattern V3 retrofitted onto categories/payment_methods, rather than needing a
-- later narrowing migration of their own.
--
-- opening_balance uses numeric(19,2): PRD S8 leaves exact currency precision/rounding as an
-- open implementation detail - 2 decimal places matches standard currency-minor-unit precision.

CREATE TABLE accounts (
    id                    uuid PRIMARY KEY,
    name                  varchar(100) NOT NULL,
    institution           varchar(100),
    type                  text NOT NULL CHECK (type IN ('CHECKING', 'SAVINGS', 'CASH_WALLET', 'CREDIT_CARD')),
    opening_balance       numeric(19,2) NOT NULL,
    opening_balance_date  date NOT NULL,
    closed_date           date,
    created_at            timestamptz NOT NULL,
    last_modified_at      timestamptz NOT NULL
);
