-- F006: Budgets.
--
-- Creates `budgets` (one per EXPENSE category) and `budget_versions` (the versioned monthly-cap
-- history) tables (PRD S5.6, F006 spec).
--
-- Numbered V8, not V6: F006's spec/plan originally said `V6__budgets.sql`, but F004 (transactions),
-- which landed after this spec was written, already claimed both `V5` (its main table) and `V6`
-- (its description/additional_notes follow-up), and F005 (transfers) then claimed `V7` - same
-- renumbering story as F003's V3->V4, F004's own V4->V5, and F005's V5->V7 notes in their own
-- spec.md files.
--
-- budgets.category_id is UNIQUE - PRD S5.6: "Budget (one per category)" - and REFERENCES
-- categories(id); the EXPENSE-only restriction can't be expressed as a plain column/check
-- constraint (it depends on another table's row), so it's enforced at the application layer
-- (BudgetService, F006 spec) instead.
--
-- budget_versions.effective_from is a `date`, always stored as the first day of the month (F006
-- spec) - the domain's YearMonth is converted to/from that convention only at the persistence
-- adapter boundary, keeping the domain itself free of persistence-representation detail (ADR 0004).
-- UNIQUE (budget_id, effective_from) backs the "only one version per (budget, month)" invariant
-- (F006 spec) as defense in depth alongside the application layer's own same-month replace check.
--
-- monthly_cap uses numeric(19,2), matching the currency-minor-unit precision convention established
-- by V4/V5/V7's own amount columns.

CREATE TABLE budgets (
    id                 uuid PRIMARY KEY,
    category_id        uuid NOT NULL UNIQUE REFERENCES categories(id),
    created_at         timestamptz NOT NULL,
    last_modified_at   timestamptz NOT NULL
);

CREATE TABLE budget_versions (
    id                 uuid PRIMARY KEY,
    budget_id          uuid NOT NULL REFERENCES budgets(id),
    monthly_cap        numeric(19,2) NOT NULL,
    effective_from     date NOT NULL,
    created_at         timestamptz NOT NULL,
    last_modified_at   timestamptz NOT NULL,
    CONSTRAINT uq_budget_versions_budget_id_effective_from UNIQUE (budget_id, effective_from)
);

CREATE INDEX idx_budget_versions_budget_id ON budget_versions(budget_id);
