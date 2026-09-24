# F012 — Action Plan

**Depends on**: F003, F004, F006, F007, F009, F010, F015.

## Backend
- [x] (Optional but recommended) `GET /api/dashboard` composing the underlying feature endpoints into one response. **Decided against, not built**: every widget is a reused, self-fetching component from its owning feature, so a composed response would have no consumer (see the spec's Decisions).
- [x] Monthly-spend-by-category query (current month, per category), if not already covered by an existing F004/F006 endpoint. Not covered (F006's report lists budgeted categories only): `MonthlySpendByCategoryQuery`, exposed as `GET /api/transactions/spend-by-category?month=YYYY-MM` (F004's controller).

## Frontend
- [x] `src/features/dashboard` page, grid layout.
- [x] Monthly spend by category widget.
- [x] Embed F006's budget-vs-actual component.
- [x] Account balances overview widget (from F003's account list).
- [x] Embed F010's net worth trend chart.
- [x] Embed F009's allocation chart.
- [x] Embed F007's pending-occurrences widget with working confirm/dismiss.

## Verification
- [x] With a populated test dataset, confirm every widget renders correct data matching its owning feature's own views. Verified by `DashboardPage.test.tsx` (every widget against the shared MSW seed, the same data its owning feature's own tests use) and the backend endpoint by `MonthlySpendByCategoryQueryTest` and `TransactionControllerTest` (real Postgres); the endpoint was also called against a running backend. Not checked in a browser.
- [x] Confirm confirming/dismissing a pending recurring occurrence from the dashboard updates the widget without a full page reload (or with one, if that's the simpler v1 approach — either is acceptable). Done without a page reload: dismiss drops the row in the widget; confirm also remounts the transaction-dependent widgets (spend, budget, balances, allocation, net worth) through `PendingOccurrencesWidget`'s optional `onConfirmed`. Covered by `DashboardPage.test.tsx`, not checked in a browser.
