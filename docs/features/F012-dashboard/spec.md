# F012 — Dashboard

## Summary
The single aggregated view surfacing spend, budget status, account balances, net worth trend, investment allocation, and upcoming recurring items (PRD §6.8). Primarily a frontend composition of widgets/queries other features already expose — minimal new backend logic.

## Scope
- Monthly spend by category (current month).
- Budget-vs-actual bars.
- Account balances overview.
- Net worth trend chart (change-date/monthly toggle, F010).
- Investment allocation by category chart, drilling into sub-categories (F009).
- Upcoming recurring bills (pending occurrences).
- Out of scope: any new calculation not already specified by another feature — this feature composes, it doesn't compute. A future "allocation by institution" pie chart (PRD §9) would slot in as one more widget once its own feature exists; F017's `institution_id` column on `accounts` (which includes `INVESTMENT` accounts, ADR 0012) is its data foundation.

## Decisions
- **No `GET /api/dashboard`.** The frontend widgets are the components the owning features already ship (`BudgetVsActualReport`, `NetWorthTrendChart`, `InvestmentAllocationChart`, `PendingOccurrencesWidget`), each fetching its own data and reusable elsewhere, so a composed response would have no consumer and would duplicate their contracts. Kept as the optional item the spec called it.
- **Monthly spend by category lives on F004's controller**: `GET /api/transactions/spend-by-category?month=YYYY-MM` -> `[{ categoryId, total }]`, expense transactions only, largest first, only categories with spending (F006's report lists budgeted categories only).
- **Confirming a pending occurrence refreshes the other widgets** (it creates a transaction), by remounting them through a `key` counter; dismissing only drops the row. (F019 replaced the counter: every successful write, a confirm included, invalidates all queries so the widgets refetch on their own, and a dismiss refetches only the pending list.)

## Backend
- Optional convenience endpoint (not built, see Decisions) `GET /api/dashboard` that composes the individual feature endpoints (F004's monthly category spend, F006's budget report, F003's account list, F010's net worth trend, F009's allocation, F007's pending occurrences) into one response, purely to save the frontend N round-trips. Not required — the frontend could instead call each feature's endpoint independently. Recommend the composed endpoint since it also gives one place to reason about "what's on the dashboard" server-side; either approach is compatible with the PRD.
- "Monthly spend by category" itself: a small query (belongs conceptually to F004, exposed here or there) — sum of expense transactions per category for the current month, distinct from F006's budget report but sharing the same underlying data.

## Frontend
- Dashboard page composing widgets, each backed by the component/query built in its owning feature:
  - Monthly spend by category → chart/table (F004 data).
  - Budget-vs-actual bars → reuses F006's component.
  - Account balances overview → list from F003.
  - Net worth trend → reuses F010's chart component (with its change-date/monthly toggle).
  - Investment allocation → reuses F009's chart component (category with drill-down into sub-categories).
  - Upcoming recurring bills → reuses F007's pending-occurrences widget (confirm/dismiss actions work directly from the dashboard).
- Layout: single-page grid of widgets, no configuration/customization specified by the PRD (a fixed layout is sufficient for v1).

## Dependencies
F003, F004, F006, F007, F009, F010 (this feature is last precisely because it composes all of them).
