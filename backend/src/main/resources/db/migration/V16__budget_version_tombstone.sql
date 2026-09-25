-- Issue #61: stopping a budget while keeping its history.
--
-- A budget could never be removed (F006 has no delete), so a category could not stop being
-- budgeted. "Stop" is a tombstone version: a budget_versions row with a NULL monthly_cap meaning
-- "no budget from this month onward". Earlier months keep the cap of their own version and a later
-- cap edit resumes the budget as a normal new forward version (a nullable `ended_from` on budgets
-- was rejected: it cannot represent stop-then-resume, the gap months would fall back to the old
-- cap through BudgetVersion.resolveEffective).
--
-- Deliberate exception to the "positivity backed at DTO + domain + DB" rule (V10): the cap is now
-- nullable, and the CHECK becomes `monthly_cap IS NULL OR monthly_cap > 0`. Only the tombstone
-- factory / stop use case produce NULL; the request DTOs and BudgetVersion.create still require a
-- positive cap.
--
-- Existing rows are unaffected (every one has a positive cap).

ALTER TABLE budget_versions ALTER COLUMN monthly_cap DROP NOT NULL;

ALTER TABLE budget_versions DROP CONSTRAINT chk_budget_versions_monthly_cap_positive;
ALTER TABLE budget_versions ADD CONSTRAINT chk_budget_versions_monthly_cap_positive
    CHECK (monthly_cap IS NULL OR monthly_cap > 0);
