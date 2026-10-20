# 0002. Version Budget and RecurringTemplate instead of mutating in place

Status: Accepted
Date: 2026-09-12

## Context
Both a category's monthly budget cap and a recurring bill/income template's amount/day-of-month change over time (rent goes up, a subscription price changes, a budget gets tightened). If the current cap/amount simply overwrote the old value, every past month's "budget vs actual" report and every past transaction's context would be silently rewritten to reflect a value that wasn't actually in effect at the time.

## Decision
Both `Budget` and `RecurringTemplate` are split into a parent entity (identity) and a versioned child (`BudgetVersion`, `RecurringTemplateVersion`) carrying the actual value plus an `effective_from` date. Editing the cap or amount creates a new version rather than mutating the current one. Historical lookups (a past month's budget-vs-actual, a past transaction's originating template version) resolve to whichever version was effective at that point in time, not the current one. `RecurringTemplate` additionally gained a `stop`/`reactivate` (`active` flag) mechanism, since a template can be paused without its version history changing.

## Consequences
- Past months' reports stay accurate after a cap or amount changes — no retroactive rewriting of history.
- Every "current value" read requires a version-resolution query (latest version with `effective_from <= target date`), not a plain field read — implemented once and reused (F006, F007).
- A single past transaction can still be hand-edited directly (e.g. fixing a typo'd amount) without going through template versioning — versioning governs the *template*, not individual generated transactions.
- This pattern was later reused for the same reason wherever "the current value of X changes but history must stay honest" applies.
