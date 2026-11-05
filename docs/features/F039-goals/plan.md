# F039 — Action Plan

**Depends on**: F008, F009, F012, F022, F025, F031, F033. See [ADR 0034](../../adr/0034-goals-as-allocation-and-deterministic-projection.md). The ADR and the PRD edits (§3, §5.16, §6.8, §6.17, §9) are written with the spec, so there is no decision-record phase.

Branch `feature/f039-goals`. Backend first (the projection and the response shapes must exist before the frontend has anything to consume), allocation before projection, queries before the dashboard.

## Phase 1 — Backend: goal and allocation
- [x] Test first: `Goal` rejects blank/over-long name, over-long notes, a zero or negative target, a negative contribution, a return outside `-50..100`, unordered returns; `archive()` clears `showOnDashboard`; `setShowOnDashboard(true)` on an archived goal throws. `GoalAllocation` rejects `0`, negative and `> 100` percentages.
- [x] Implement `domain/goal/Goal.java`, `GoalStatus.java`, `GoalAllocation.java` and the two repository ports.
- [ ] Flyway migration `V28__goals.sql` (`goals`, `goal_allocations`, checks and unique constraint per the spec), plus its migration test (each check rejects its bad row).
- [ ] JPA entities, Spring Data repositories and adapters under `infrastructure/persistence/goal/`, including `sumPercentageByProductExcludingGoal` (non-archived only) and `existsByProductId`.
- [ ] Test first (`GoalService`, fakes): create/edit/delete audited; `targetDate` not after today is `400`, an unchanged passed date is accepted; replace allocations rejects a duplicate product, an unknown product (`404`), an archived goal (`409`) and a product over 100% across other non-archived goals (`409`); archiving frees percentages; restoring re-checks them (`409`); `setShowOnDashboard(true)` on an archived goal is `409`.
- [ ] Implement `application/goal/GoalService`, the exceptions (`GoalNotFoundException`, `InvalidGoalException`, `GoalArchivedException`, `GoalAllocationExceedsProductException`, `InvestmentProductAllocatedToGoalException`), `AuditEntityType.GOAL`, and the product hard-delete guard (`409`).
- [ ] REST: `GoalController` (`GET/POST /api/goals`, `GET/PUT/DELETE /api/goals/{id}`, `PATCH .../show-on-dashboard`), `PUT /api/goals/{id}/allocations`; DTOs with `@Size`/`@Positive`/`@PositiveOrZero`/`@DecimalMin`/`@DecimalMax`; controller tests for `200/201/204/400/404/409`.
- [ ] `./gradlew spotlessApply` then `scripts/verify.sh backend` (Docker running).

## Phase 2 — Backend: projection, history, dashboard, helper
- [ ] Test first (`GoalProjectionQuery`, fixed `Clock`, non-UTC zone): zero return and zero contribution leaves the value flat; zero return equals `V0 + N × C`; a known 12-month case matches the closed form to the cent; required contribution is `0` when `V0` already reaches the target; required contribution reproduces the target when fed back into the loop; a past `targetDate` projects over zero months; a dateless goal reports `monthsToTarget` `0` when already reached, the right month for a known case, and `null` at the 600-month cap; the three scenarios are ordered.
- [ ] Implement `GoalProjectionQuery` and `GET /api/goals/{id}/projection` (controller test `200/404`).
- [ ] Test first (`GoalHistoryQuery`): sums `percentage / 100 × product series value` per month over several products; an invalid range is `400`; a goal with no allocations yields zero-valued points.
- [ ] Implement `GoalHistoryQuery` and `GET /api/goals/{id}/history`.
- [ ] Test first (`GoalDashboardQuery`, `GoalListQuery`): only flagged goals appear, an archived goal never does, standard order (dated by date, then dateless by name ignoring case), `progressPercent` may exceed 100, `targetReached` flag.
- [ ] Implement `GoalDashboardQuery`, `GoalListQuery`, `GET /api/goals/dashboard` and the list's allocation fields.
- [ ] Test first (`EmergencyFundSuggestionQuery`): averages the last 12 full months, skips leading months with no expense, returns `0` with no expenses, ignores transfers, `months` outside `1..120` is `400`.
- [ ] Implement `EmergencyFundSuggestionQuery` and `GET /api/goals/emergency-fund-suggestion`, plus `GoalAllocationForProductQuery` and `GET /api/investment-products/{id}/goal-allocations`.
- [ ] Regenerate `frontend/src/api/generated/schema.ts` (`npm run generate-api-types -- --offline`).
- [ ] `./gradlew spotlessApply` then `scripts/verify.sh backend` (Docker running).

## Phase 3 — Frontend
- [ ] Test first: API modules and hooks (list, detail, mutations invalidating the goals and dashboard keys, projection, history, suggestion, per-product allocations) against MSW.
- [ ] Implement `src/api/goals/goals.ts`, `goalsQueries.ts`, MSW handlers and fixtures.
- [ ] Test first: `GoalsPage` (loading, empty prompt, list with progress and badge, dashboard switch calls the PATCH, archived filter); `GoalForm` (validation, `maxLength`, status only on edit); `TargetHelperDialog` (emergency fund fills `months × average`, retirement fills `income × 12 ÷ rate`, nothing is stored).
- [ ] Implement `GoalsPage`, `GoalForm`, `TargetHelperDialog` and the `/goals` route.
- [ ] Test first: `GoalAllocationEditor` (remaining capacity per product, the unassigned remainder, an over-100 save shows the mapped `409` message); `GoalProjectionChart` and `GoalHistoryChart` (three lines and a target line, the "today's money" and "as of the current allocation" captions, a dateless "not reached within 50 years" state).
- [ ] Implement `GoalDetailPage`, `GoalAllocationEditor`, `GoalProjectionChart`, `GoalHistoryChart` on `ChartSvg` and the `/goals/:id` route.
- [ ] Test first: `GoalsWidget` (only flagged goals, hidden when none, status line for a dated and a dateless goal, "View all" link) and the "Allocated to" chips on the product detail page.
- [ ] Implement `GoalsWidget` in `DashboardPage`, the product detail chips, the nav entry, i18n (`en`, `pt-BR`), `PROBLEM_TYPE_MESSAGES` entries and the Activity-page `GOAL` label.
- [ ] `scripts/verify.sh frontend`.

## Phase 4 — Docs
- [ ] `docs/conventions/data-constraints.md`: the contribution `>= 0` exception and the `numeric(5,2)` return percentages.
- [ ] Root/backend/frontend `CLAUDE.md`: no new rule expected; add one only if implementation surfaces a gotcha the code cannot show.
- [ ] `docs/features/README.md` row verified against what was built, and F039 removed from its "Status" list; `CHANGELOG.md` entry under `## [Unreleased]` in the same commit as the code.

## Verification
- [ ] Create a goal "Car" (target 80,000, target date in 2 years, contribution 2,000, returns 2/4/6) -> it appears on `/goals` and on the dashboard card.
- [ ] Allocate 60% of a product to "Car" and 40% to a second goal -> the product's detail page lists both chips; allocating another 10% to a third goal is rejected with the exceeds-100% message.
- [ ] Open the goal -> the projection chart shows three scenario lines and the target line; the table shows value at the date, shortfall or surplus, the required monthly contribution, and the caption "values in today's money".
- [ ] Create "Emergency fund" without a date, use "Suggest a target" with 6 months -> the target field fills with 6 × the average expense of the last 12 full months; the projection says "reached in N months" or "not reached within 50 years".
- [ ] Use the retirement helper (income 8,000, rate 4%) -> the target field fills with 2,400,000.
- [ ] Turn off "Show on dashboard" on one goal from the list -> its dashboard row disappears; turn it off on all of them -> the card disappears.
- [ ] Archive an allocated goal -> it leaves the dashboard and the list, its percentages free up on the product, restoring it keeps the dashboard flag off, and restoring when the percentages no longer fit is rejected.
- [ ] Try to delete a product allocated to a goal -> `409` with the mapped message; close it instead -> the goal's allocated value drops by its share.
- [ ] Open the history chart -> it is labeled "as of the current allocation" and matches the product value series times the percentages.
- [ ] Switch the language to pt-BR -> every goals string is translated and amounts keep the symbol-less format.
- [ ] `scripts/verify.sh backend frontend` (Docker running) all green.
