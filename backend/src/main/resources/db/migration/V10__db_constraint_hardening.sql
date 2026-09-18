-- DB constraint hardening (post-F007 schema audit) - closes two gaps found by inspection, no
-- feature of its own.
--
-- 1. UNIQUE on categories.name/payment_methods.name/accounts.name: previously nothing (not the
--    schema, not the application layer) stopped two rows sharing a name - CategoryService/
--    PaymentMethodService/AccountService now reject a duplicate at create/rename time (409,
--    {Aggregate}NameAlreadyExistsException), and this is the backing DB constraint, same
--    defense-in-depth spirit as V8__budgets.sql's `budgets.category_id UNIQUE`. Exact-match,
--    case-sensitive - matches Postgres's own default `UNIQUE` semantics, so the DB constraint and
--    the application-layer check that fronts it never disagree. No backfill needed: F002's seeded
--    categories/payment methods are already unique by name, and accounts are entirely
--    user-created (F003, no seed data).
--
-- 2. CHECK (amount > 0) on transactions.amount/transfers.amount/budget_versions.monthly_cap/
--    recurring_template_versions.amount: positivity was already enforced at both the request DTO
--    layer (`@Positive`) and the domain constructor (`Transaction`/`Transfer`/`BudgetVersion`/
--    `RecurringTemplateVersion`'s own `requireValidAmount`/`requireValidCap`) - this migration adds
--    the third layer, matching every other cross-cutting invariant in this codebase (text length,
--    `recurring_template_versions.day_of_month`, `transfers.from_account_id <> to_account_id`),
--    which are all backed at DTO + domain + DB. No backfill needed: nothing has ever written a
--    non-positive value through the app's own validated paths.

ALTER TABLE categories ADD CONSTRAINT uq_categories_name UNIQUE (name);
ALTER TABLE payment_methods ADD CONSTRAINT uq_payment_methods_name UNIQUE (name);
ALTER TABLE accounts ADD CONSTRAINT uq_accounts_name UNIQUE (name);

ALTER TABLE transactions ADD CONSTRAINT chk_transactions_amount_positive CHECK (amount > 0);
ALTER TABLE transfers ADD CONSTRAINT chk_transfers_amount_positive CHECK (amount > 0);
ALTER TABLE budget_versions ADD CONSTRAINT chk_budget_versions_monthly_cap_positive CHECK (monthly_cap > 0);
ALTER TABLE recurring_template_versions ADD CONSTRAINT chk_recurring_template_versions_amount_positive CHECK (amount > 0);
