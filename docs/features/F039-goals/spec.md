# F039 — Goals

## Summary
Lets the user manage financial **goals** (retirement, a car, a PC upgrade, an emergency fund) and **allocate investment products to them**, with estimates and projections. A `Goal` has a target amount, an optional target date, a planned monthly contribution and three real annual return assumptions. A product's value is split between goals by percentage; the goal's current value is the sum of its shares. The backend projects each goal deterministically under a pessimistic, base and optimistic scenario and answers: will I reach the target by the date, by how much do I fall short, how much must I contribute per month, and when will a dateless goal (an emergency fund) be reached.

Allocation and goals are one concept here, not two features; see [ADR 0034](../../adr/0034-goals-as-allocation-and-deterministic-projection.md) for the full rationale, and PRD §5.16 and §6.17. The FII `AllocationPlan` (F026) is a different concept and is untouched. Product surface: a new `/goals` area, a dashboard card, an "Allocated to" list on the product detail page, a new migration.

## Scope
- Backend: `Goal` and `GoalAllocation` aggregates (`domain/goal`), `GoalService`, `GoalProjectionQuery`, `GoalHistoryQuery`, `GoalDashboardQuery`, `EmergencyFundSuggestionQuery` (`application/goal`), migration `V28__goals.sql`, `GoalController` (under `/api/goals`), `GoalAllocationController` (maps `/api`, so it also hosts `GET /api/investment-products/{id}/goal-allocations`) and `GoalReportController` (projection, history, dashboard, suggestion), a hard-delete guard on products with allocations (`InvestmentProductService.delete`).
- Frontend: `/goals` list, `/goals/:id` detail (allocation editor, projection chart, history chart, target helpers), a `GoalsWidget` dashboard card, an "Allocated to" chip list on the product detail page, API modules, query hooks, MSW handlers, i18n (en, pt-BR).
- Docs: ADR 0034, PRD §3/§5.16/§6.8/§6.17/§9 (written with this spec), `docs/features/README.md`.
- Out of scope: see [Non-goals](#non-goals).

## Decisions
See ADR 0034 for the full rationale. Implementation-level decisions not already in the ADR:

| Decision | Why |
|---|---|
| `Goal` fields: `name` (`MAX_NAME_LENGTH`), optional `notes` (`MAX_ADDITIONAL_NOTES_LENGTH`), `targetAmount` (> 0), optional `targetDate`, `plannedMonthlyContribution` (≥ 0), `returnPessimistic`/`returnBase`/`returnOptimistic` (real annual %, `-50.00`–`100.00`, pessimistic ≤ base ≤ optimistic), `status`, `showOnDashboard`. | Zero contribution is a valid plan (let the existing balance grow), so the contribution is the one amount with `>= 0` instead of `> 0`; documented in `docs/conventions/data-constraints.md`. Returns are percentages, not money, so `numeric(5,2)` like `target_percentage` (F026). |
| `status` is `ACTIVE`, `ACHIEVED` or `ARCHIVED`, set manually; an automatic "target reached" badge shows when allocated value ≥ target. Archiving sets `showOnDashboard` to false and its allocations stop counting toward a product's 100%; restoring a goal does not turn the flag back on. Restoring needs the percentages to fit again (`409` otherwise). | Manual status keeps "I decided this is done" separate from "the number crossed the line". Clearing the flag in the domain (`Goal.archive()`) means a query never has to filter archived goals out of the dashboard. |
| `targetDate` must be after today on create and whenever it changes (`400`); an unchanged date that has since passed is allowed. | Rejecting a passed date on every edit would block editing the notes of an overdue goal. A goal whose date has passed projects over zero months and reads as a plain shortfall. |
| Replacing allocations is one call, `PUT /api/goals/{id}/allocations` with the full list. | The editor shows the whole split; per-row endpoints would need cross-request sum checks. |
| A product's allocation sum is checked in `GoalService` inside the transaction, not by a database constraint. | The rule spans rows of different goals and ignores archived ones; a constraint cannot express it. Single-user, so no concurrent writers. |
| The goal's current value uses `LatestInvestmentSnapshotQuery.totalValueOfProduct(productId, today)` per allocated product, times `percentage / 100`. | The existing definition of a product's value; no new valuation. A product with no snapshot counts as zero. |
| Rate math runs in `double` (a fractional power), then each money result is rounded to two decimals `HALF_UP` with `BigDecimal`. | `BigDecimal` has no fractional `pow` (verified, see "Resolved during implementation"). Rounding only at the edges keeps the series internally consistent. |
| Projection month `m` (from 1): `V[m] = V[m-1] × (1 + i) + C`, `i = (1 + r/100)^(1/12) − 1`, `V[0]` = allocated value now, `C` = planned contribution. For a dated goal `N` = whole months from the current month to the target month (0 if not in the future). Required contribution: `max(0, (T − V0 × (1+i)^N) / s)`, `s = ((1+i)^N − 1) / i`, or `N` when `i = 0`. | Contributions at month end make the closed form match the loop. All values are in today's money (real returns). |
| A dateless goal is simulated up to 600 months. `monthsToTarget` is the first `m` with `V[m] ≥ T` (0 if already reached), or `null` ("not reached within 50 years"). | The 50-year cap is ADR 0034's. |
| The history chart sums, per month, `percentage / 100 × ProductSeries point value` over the allocated products, using the **current** split. | Splits are not versioned; the label "as of the current allocation" is part of the UI. |
| Emergency-fund suggestion: `months × average monthly expense` over the last 12 *full* months before the current one; leading months with no expense are skipped, and with no expense at all the average is `0`. | A new user with 3 months of data should not have their average divided by 12. Built from `MonthlySpendByCategoryQuery.forMonth` (transfers are not expenses, PRD §5.5), 12 calls. |
| The retirement helper (desired monthly income × 12 ÷ withdrawal rate %, default 4) is frontend-only arithmetic. | Pure arithmetic with no data; a round trip would add an endpoint to test for nothing. |
| Ordering everywhere (list, dashboard): dated goals first by `targetDate` ascending, then dateless goals by `name` ignoring case; ties by `id`. The list shows non-archived goals unless `includeArchived=true`. | No priority field (ADR 0034): the manual split percentages already express what matters most. |
| The dashboard card is one `GET /api/goals/dashboard` returning a summary per flagged goal. | An unlimited selection must not mean N projection calls from the dashboard. |

## Backend

### Domain
- `domain/goal/Goal.java`: aggregate (`id`, `name`, `notes`, `targetAmount`, `targetDate`, `plannedMonthlyContribution`, `returnPessimistic`, `returnBase`, `returnOptimistic`, `status`, `showOnDashboard`), factory `create(id, name, notes, targetAmount, targetDate, plannedMonthlyContribution, returnPessimistic, returnBase, returnOptimistic, showOnDashboard)` (status `ACTIVE`), `reconstitute(...)`, `edit(...)` (same fields plus `status`, all-or-nothing), `archive()`, `setShowOnDashboard(boolean)`. Invariants (`IllegalArgumentException`, translated to `InvalidGoalException` in the service): `name` non-blank and ≤ `MAX_NAME_LENGTH`; `notes` ≤ `MAX_ADDITIONAL_NOTES_LENGTH`; `targetAmount` > 0; `plannedMonthlyContribution` ≥ 0; each return within `-50.00`..`100.00` and `pessimistic ≤ base ≤ optimistic`; money and percentages have at most two decimals and are padded to scale 2; `showOnDashboard` is false whenever `status` is `ARCHIVED` (`archive()` and `edit` clear it; `edit` to a non-archived status uses the flag it is given, so restoring with the archived goal's `false` keeps it off; `setShowOnDashboard(true)` on an archived goal throws `IllegalStateException`). The `targetDate` future check is in the service (the domain has no clock).
- `domain/goal/GoalStatus.java`: enum `ACTIVE`, `ACHIEVED`, `ARCHIVED`.
- `domain/goal/GoalAllocation.java`: aggregate (`id`, `goalId`, `productId`, `percentage`), factory `create(id, goalId, productId, percentage)`. Invariant: `percentage` in `(0, 100]`, two decimals.
- `domain/goal/GoalRepository.java` (`save`, `findById`, `findAll`, `deleteById`) and `domain/goal/GoalAllocationRepository.java` (`findByGoalId`, `findByProductId`, `findByGoalIds`, `replaceForGoal(goalId, List)`, `findAll`, `sumPercentageByProductExcludingGoal(productIds, goalId)` returning a `Map` (one query for all the products, counting non-archived goals only; a product nobody else allocates is absent), `existsByProductId`).

### Application
- `application/goal/GoalService`:
  - `@Transactional create(GoalInput)/edit(id, GoalInput, status)/setShowOnDashboard/delete` (archiving is an `edit` to `ARCHIVED`, there is no separate `archive` use case: the endpoint is the status field of the `PATCH`): `404` `GoalNotFoundException`; `400` `InvalidGoalException` (domain violations, a `targetDate` not after today via the injected `Clock`); `409` `GoalArchivedException` for `setShowOnDashboard(true)` on an archived goal. Restoring (`edit` from `ARCHIVED` to another status) re-checks every allocation's product sum (`409` `GoalAllocationExceedsProductException`). Each write calls the `AuditLog` port in the same transaction (`AuditEntityType.GOAL`).
  - `@Transactional replaceAllocations(goalId, List<AllocationInput>)`: `404` `GoalNotFoundException`; `404` existing `InvestmentProductNotFoundException` for an unknown product; `400` `InvalidGoalException` for a duplicate product or an invalid percentage; `409` `GoalArchivedException` on an archived goal; `409` `GoalAllocationExceedsProductException` when, for any product, the sum over other non-archived goals plus this one exceeds 100. Audited as an update of the goal (the allocation set is in the diff).
- `application/goal/GoalProjectionQuery.projection(goalId)`: `404` `GoalNotFoundException`. Returns `GoalProjection(allocatedValue, latestSnapshotDate, scenarios)`; each `ScenarioProjection(name, annualReturn, points, projectedValueAtTargetDate, shortfallAtTargetDate, requiredMonthlyContribution, monthsToTarget)` per the Decisions table. `points` is monthly `(month, value)`: through the target month for a dated goal; for a dateless one until the *pessimistic* scenario reaches the target, capped at 600 months. Time from the injected `Clock`.
- `application/goal/GoalHistoryQuery.history(goalId, from, to)`: `404`; `400` existing `InvalidValueSeriesRangeException`. Uses `InvestmentValueSeriesQuery.series(from, to, productId)` per allocated product.
- `application/goal/GoalDashboardQuery.dashboard()`: for each goal with `showOnDashboard`, a `GoalSummary(id, name, targetAmount, targetDate, allocatedValue, progressPercent, targetReached, projectedShortfall, monthsToTarget)` using the base scenario (`projectedShortfall` ≤ 0 means on track; `monthsToTarget` is null for a dated goal).
- `application/goal/GoalListQuery.list(includeArchived)`: each goal with its allocations (product name, percentage), `allocatedValue` and `progressPercent`, in the standard order.
- `application/goal/EmergencyFundSuggestionQuery.suggest(months)`: `400` for `months` outside `1..120`; returns `(months, averageMonthlyExpense, suggestedTarget, monthsAveraged)`.
- `application/goal/GoalAllocationForProductQuery.forProduct(productId)`: `404` existing product exception; active goal name + percentage pairs (non-archived).
- Product hard delete: `InvestmentProductService` refuses with `409` `InvestmentProductAllocatedToGoalException` when `GoalAllocationRepository.existsByProductId`; closing is unaffected.
- `application/auditlog/AuditEntityType`: new `GOAL` (the `audit_log.entity_type` column is `varchar(40)`, no migration for the enum).
- Log lines carry goal and product ids and counts only, never names, amounts or percentages (ADR 0011).
- Not touched: net worth, allocation by category (`InvestmentAllocationQuery`), the FII allocation plan, snapshots, holdings.

### Persistence
- Migration `V28__goals.sql`:
  - `goals`: `id uuid primary key`, `name varchar(100) not null`, `notes varchar(500)`, `target_amount numeric(19,2) not null check (target_amount > 0)`, `target_date date`, `planned_monthly_contribution numeric(19,2) not null check (planned_monthly_contribution >= 0)`, `return_pessimistic`/`return_base`/`return_optimistic numeric(5,2) not null` each `check (... between -50 and 100)`, `check (return_pessimistic <= return_base and return_base <= return_optimistic)`, `status varchar(20) not null check (status in ('ACTIVE','ACHIEVED','ARCHIVED'))`, `show_on_dashboard boolean not null default true`, `check (status <> 'ARCHIVED' or show_on_dashboard = false)`, plus the audit columns of `AuditableEntity`.
  - `goal_allocations`: `id uuid primary key`, `goal_id uuid not null references goals (id) on delete cascade`, `product_id uuid not null references investment_products (id)`, `percentage numeric(5,2) not null check (percentage > 0 and percentage <= 100)`, `constraint uq_goal_allocations_goal_product unique (goal_id, product_id)`, audit columns; index on `product_id`.
  - Migration test per the backend `CLAUDE.md` pattern: the checks reject a zero target, a negative contribution, unordered returns, an out-of-range percentage and a duplicate `(goal, product)`.
- `infrastructure/persistence/goal/`: `GoalJpaEntity`, `GoalAllocationJpaEntity` (both extend `AuditableEntity`), package-private Spring Data repositories, `GoalRepositoryAdapter`, `GoalAllocationRepositoryAdapter`.

### API
All endpoints require the login (no `permitAll` change).
- `GET /api/goals?includeArchived=false`: `200` list (`GoalListQuery`).
- `POST /api/goals`: body `name` (`@NotBlank @Size(max=100)`), `notes` (`@Size(max=500)`), `targetAmount` (`@Positive`), `targetDate`, `plannedMonthlyContribution` (`@PositiveOrZero`), the three returns (`@DecimalMin("-50") @DecimalMax("100")`), `showOnDashboard` (default true); `201`; `400` validation/`InvalidGoalException`.
- `GET /api/goals/{id}`: `200`; `404`.
- `PATCH /api/goals/{id}`: full replace, same fields plus `status` (an update endpoint is a `PATCH`, docs/conventions/backend-structure.md); `200`; `400`, `404`, `409`.
- `PATCH /api/goals/{id}/show-on-dashboard`: body `{showOnDashboard}`; `200`; `404`; `409` when turning on for an archived goal.
- `DELETE /api/goals/{id}`: `204`; `404`. Allocations cascade.
- `PUT /api/goals/{id}/allocations`: body `{allocations: [{productId, percentage}]}` (`@Valid`, `@Positive`, `@DecimalMax("100")`); `200` with the stored list; `400`, `404`, `409`.
- `GET /api/goals/{id}/projection`: `200`; `404`.
- `GET /api/goals/{id}/history?from=YYYY-MM&to=YYYY-MM`: `200` monthly `(month, value)` points; `400` invalid range; `404`.
- `GET /api/goals/dashboard`: `200` list of `GoalSummary`.
- `GET /api/goals/emergency-fund-suggestion?months=6`: `200`; `400` (`@Min(1) @Max(120)`).
- `GET /api/investment-products/{id}/goal-allocations`: `200`; `404`.
- Regenerate `frontend/src/api/generated/schema.ts` (`npm run generate-api-types -- --offline`).

## Frontend
- `src/api/goals/goals.ts` and `goalsQueries.ts`: HTTP functions and hooks (list, detail, create, edit, delete, toggle dashboard, replace allocations, projection, history, dashboard, emergency-fund suggestion, per-product allocations) with a key factory; every goal mutation invalidates the goals keys and the dashboard key; allocation changes also invalidate the per-product key. MSW handlers and fixtures under `src/mocks`.
- `src/features/goals/GoalsPage.tsx` (`/goals`): list with a progress bar (`allocatedValue / targetAmount`), a "target reached" badge, base-scenario status line, a quick "Show on dashboard" switch, an archived filter; loading, empty (a "Create your first goal" prompt) and error states.
- `src/features/goals/GoalForm.tsx`: create/edit dialog (name, notes, target amount, target date, planned contribution, three returns, status on edit, show on dashboard), `maxLength` on name and notes. A "Suggest a target" button opens `TargetHelperDialog.tsx` (emergency fund: months, default 6, shows the average expense from the suggestion endpoint; retirement: desired monthly income and withdrawal rate, default 4%); it only fills the target field.
- `src/features/goals/GoalDetailPage.tsx` (`/goals/:id`): summary, `GoalAllocationEditor.tsx` (product picker, percentage per row, per-product remaining capacity, the unassigned remainder, save), `GoalProjectionChart.tsx` (three scenario lines plus a target line, on `ChartSvg`, no chart library), the scenario results table (value at the date, shortfall, required monthly contribution, months to target), `GoalHistoryChart.tsx` labeled "as of the current allocation". Every value carries the caption "values in today's money" and the latest snapshot date.
- `src/features/dashboard/GoalsWidget.tsx`: a card in `DashboardPage` listing flagged goals (name, progress bar, one status line, "View all" link to `/goals`); hidden when there is none.
- `src/features/investments/products/InvestmentProductDetailPage.tsx`: a read-only "Allocated to" chip list (goal name + percentage, linking to the goal).
- Routes `/goals` and `/goals/:id` and a nav entry in `App.tsx`, following the existing lazy-route pattern; i18n namespace `goals` in `en` and `pt-BR`; `PROBLEM_TYPE_MESSAGES` entries in `apiError.ts` for the new exceptions; an Activity-page label for `GOAL`.
- Date logic and tests pin a non-UTC zone (`test.env.TZ`).

## Docs
- `docs/adr/0034-goals-as-allocation-and-deterministic-projection.md` and its index row.
- `docs/PRD.md`: §3 (one sentence: projections use only user-entered assumptions), new §5.16 and §6.17, §6.8 (the dashboard card), §9 (deferred items), and the table of contents (§5.15 was missing; §5.16 and §6.17 added).
- `docs/conventions/data-constraints.md`: the contribution `>= 0` exception and the `numeric(5,2)` return percentages.
- `docs/features/README.md` row and "Status" list.
- CHANGELOG: entry ships with the implementing PR (root CLAUDE.md).

## Non-goals
- Monte Carlo or any stochastic simulation: it needs a volatility the app has no data for (PRD §9).
- Contribution step-up over time, and actual-contribution tracking from trades: the planned contribution is constant in today's money (PRD §9).
- Linking whole accounts (savings, checking) to a goal: only investment products (PRD §9).
- Stored allocation history: the history chart applies the current split retroactively (PRD §9).
- Market data, inflation or index rates (CDI, IPCA): returns are real rates the user types.
- Goal priority and automatic funding order across goals.
- Rebalancing or buy/sell suggestions to reach a goal.
- Goal-level notifications.

## Resolved during implementation
- Verified (JDK 25 `java.math.BigDecimal`): its only powers are `pow(int)`, `pow(int, MathContext)` and `sqrt(MathContext)`, so there is no fractional-exponent `pow`. `(1 + r)^(1/12)` is computed in `double` (`Math.pow`) and money results are rounded to two decimals `HALF_UP`.
- The product hard-delete guard lives in `InvestmentProductService.delete`, which already refuses a product with any holding (`holdingRepository.existsByProductId`, `InvestmentProductHasHoldingsException`); it now also refuses one that `GoalAllocationRepository.existsByProductId` finds (`InvestmentProductAllocatedToGoalException`, `409`), so a delete never reaches the foreign key as a `500`. There is no `HasHistoryChecker` for products (that port is the holding's).

## Dependencies
F008/F022 (investment products, holdings, `InvestmentProductNotFoundException`), F009 (snapshots, `LatestInvestmentSnapshotQuery`, `InvestmentValueSeriesQuery`), F012 (the dashboard it adds a card to), F025 (`AuditLog` port), F031/F032 (Problem Details and validation errors), F033 (i18n), `docs/conventions/data-constraints.md`. Touches without changing behavior: F026 (the FII `AllocationPlan`), F010 (net worth), F030 (reports).
