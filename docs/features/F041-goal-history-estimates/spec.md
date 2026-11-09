# F041 — Goal History Estimates

## Summary
Adds two estimates to goals (F039) that come from what actually happened instead of from typed assumptions. A **current-pace line** shows when the goal would be reached if its value kept rising at the average monthly increase of the last 12 months, drawn as a fourth line beside the pessimistic, base and optimistic scenarios and labeled "based on history". A **"Suggest from history"** button beside the planned monthly contribution fills it with the 12-month average of what was actually put into the goal's products. Neither stores anything and neither derives a return.

See [ADR 0035](../../adr/0035-goal-estimates-from-history.md) for the full rationale (it amends [ADR 0034](../../adr/0034-goals-as-allocation-and-deterministic-projection.md)), and PRD §3, §5.16 and §6.17. Product surface: one extra field on the projection response, one new read endpoint, one extra chart line and table row, one extra form button. No migration.

## Scope
- Backend: `GoalPaceQuery` and `GoalContributionSuggestionQuery` (`application/goal`), a nullable `pace` on `GoalProjection` (computed in `GoalProjectionQuery`), pure pace math in `GoalProjectionMath`, `GET /api/goals/{id}/contribution-suggestion` in `GoalReportController`. No migration.
- Frontend: the pace line, legend entry, note and "not enough history" state in `GoalTimelineChart`, a "Current pace" row in `GoalScenarioTable`, a "Suggest from history" button in `GoalForm`, API types and hook, MSW handlers, i18n (en, pt-BR).
- Docs: ADR 0035, PRD §3/§5.16/§6.17/§9 (written with this spec), `docs/features/README.md`.
- Out of scope: see [Non-goals](#non-goals).

## Decisions
See ADR 0035 for the full rationale. Implementation-level decisions not already in the ADR:

| Decision | Why |
|---|---|
| The pace window is the last 12 months ending the current month, read from `GoalHistoryQuery.history(goalId, current − 11, current)`. Months whose goal value is `0` before the first non-zero month are skipped. | The same history the chart shows, so the line is explainable from it; the history query already clamps to the current month and applies the current split. |
| The window then starts at `max(first non-zero month, latest opening-position as-of month + 1)`, over the opening positions of the holdings of the goal's allocated products. With fewer than 4 points left, `pace` is `null`. | An opening position lands as a value jump in its as-of month (ADR 0033); starting after it measures only what happened later. Four points is three intervals, the least that is not noise. |
| `monthlyPace = (valueNow − valueAtStart) ÷ (pointCount − 1)`, money rounded to two decimals `HALF_UP`, and it may be zero or negative. `valueNow` is the goal's `allocatedValue` (the same starting value the scenarios use), not the last history point. | One "now" for every line, so the pace and the scenarios fork from the same point. A negative pace is shown honestly (the line falls). |
| Pace point `i` (from 0, the current month) is `allocatedValue + monthlyPace × i`, for `i = 0..horizon`, where the horizon is the scenarios' own: a dated goal's months to the target month, a dateless goal's pessimistic-scenario horizon (capped at 600). | The pace never stretches the chart past the scenarios (ADR 0035). |
| `projectedValueAtTargetDate` and `shortfallAtTargetDate` are set for a dated goal and `monthsToTarget` for a dateless one, like a scenario: `monthsToTarget` is `0` when already reached, `null` when `monthlyPace ≤ 0` or the target is not reached within 600 months, else `ceil((target − allocatedValue) ÷ monthlyPace)`. | Same contract the frontend already renders for a scenario; a flat or falling pace never reaches the target. |
| The suggestion uses the **last 12 full months before the current one** (the pace window includes the current month; the suggestion does not). | A partial current month would lower an average of monthly buys, the same reason the emergency-fund suggestion (F039) averages full months. The pace needs "now". |
| Suggestion per month `m` = `Σ over allocated products p: percentage(p) ÷ 100 × (contributed(p, m) − openingCost(p, m))`, where `openingCost(p, m)` is `quantity × unitPrice` of each opening position of `p`'s holdings whose as-of month is `m`. The average runs from the first month with a non-zero `m` to the end of the window; `monthsAveraged` is that count; a negative average is returned as `0`; no allocations or no contribution at all returns `0` and `0`. | `contributed` counts an opening position like a buy in its as-of month (F038), which is not a monthly saving. Months before the first contribution would dilute the average the way leading no-expense months would for the emergency fund. |
| The suggestion reads `InvestmentValueSeriesQuery.series(from, to, productId)` once per allocated product. | The same bounded loop `GoalHistoryQuery` already has (F039); a bulk variant is a separate refactor. |
| The pace is an extra `pace` field on the projection response, not a fourth entry of `scenarios`; the suggestion is its own endpoint. | `scenarios` is three, ordered and named (pessimistic, base, optimistic) in the API and the UI; a fourth entry would break that contract and the dashboard's base-scenario reads. The suggestion is only fetched on demand from the form, so it should not cost every projection request. |
| The "Suggest from history" button is shown only when editing a goal that has allocations; it fills the field and does not save. | On create nothing is allocated yet; filling without saving matches the target helpers. |

## Backend

### Domain
No change.

### Application
- `application/goal/GoalProjectionMath`: pure functions `monthlyPace(List<BigDecimal> values)` and `paceMonthsToTarget(allocatedValue, monthlyPace, target)`; the 4-point minimum and the rounding live here.
- `application/goal/PaceProjection.java`: record `PaceProjection(BigDecimal monthlyPace, List<ProjectionPoint> points, BigDecimal projectedValueAtTargetDate, BigDecimal shortfallAtTargetDate, Integer monthsToTarget)`.
- `application/goal/GoalPaceQuery.pace(Goal goal, BigDecimal allocatedValue, int horizonMonths, LocalDate today)`: returns `Optional<PaceProjection>` per the Decisions table. It reads `GoalHistoryQuery`, the goal's allocations, and the holdings and opening positions of the allocated products through the existing `InvestmentHoldingRepository` and `InvestmentOpeningPositionRepository` ports (add a read by product ids if one is missing).
- `application/goal/GoalProjection`: gains a nullable `PaceProjection pace`; `GoalProjectionQuery.projection(goalId)` fills it from `GoalPaceQuery` using the scenarios' horizon. Time from the injected `Clock`.
- `application/goal/ContributionSuggestion.java`: record `(BigDecimal averageMonthlyContribution, int monthsAveraged)`. `application/goal/GoalContributionSuggestionQuery.suggest(goalId)`: `404` `GoalNotFoundException`; the rest per the Decisions table. Read-only, so no audit entry.
- Log lines carry goal and product ids and counts only (ADR 0011); no new exception types.
- Not touched: `GoalService`, `GoalDashboardQuery`, `GoalListQuery` (the dashboard and the list keep the base scenario), snapshots, holdings, opening positions.

### Persistence
No migration and no new table.

### API
- `GET /api/goals/{id}/projection`: the response gains `pace` (`{monthlyPace, points[{month, value}], projectedValueAtTargetDate, shortfallAtTargetDate, monthsToTarget}` or `null`). Existing fields unchanged.
- `GET /api/goals/{id}/contribution-suggestion`: `200` `{averageMonthlyContribution, monthsAveraged}`; `404`.
- Regenerate `frontend/src/api/generated/schema.ts` (`npm run generate-api-types -- --offline`).

## Frontend
- `src/api/goals/goals.ts` and `goalsQueries.ts`: the `pace` type on the projection, `getGoalContributionSuggestion`, and `useGoalContributionSuggestion(id, enabled)` (fetched only on demand, like `useEmergencyFundSuggestion`). MSW handlers and fixtures under `src/mocks` (`projectionFor` gets a `pace`; one fixture without it).
- `src/features/goals/components/GoalTimelineChart.tsx`: a fourth grey line, drawn apart from the scenarios (a dash-dot pattern, `data-pace`), whose points start on the "Today" marker and follow the horizon selector; a legend entry "Pace (based on history): +N a month"; a note "Pace is based on recorded values, not adjusted for inflation" when the line is drawn; "Not enough history for a pace yet." when `pace` is `null` and the goal has allocations.
- `src/features/goals/components/GoalScenarioTable.tsx`: a fourth row "Current pace" (value at the date and surplus or shortfall for a dated goal, time to target for a dateless one, "–" for the real return and the monthly contribution needed); a card on a phone like the other rows.
- `src/features/goals/components/GoalForm.tsx`: a "Suggest from history" button beside "Planned monthly contribution", shown when editing a goal with allocations; it fetches the suggestion, fills the field, and shows "Average of the last N months: X" (and a hint when `monthsAveraged` is `0`). It does not save.
- i18n namespace `goals` in `en` and `pt-BR`: `pace.*` and `contribution.*` keys; the locale parity test covers both.
- Date logic and tests pin a non-UTC zone (`test.env.TZ`).

## Docs
- `docs/adr/0035-goal-estimates-from-history.md` and its index row.
- `docs/PRD.md`: §3 (one sentence), §5.16 (the pace and the suggestion), §6.17 (the pace row, line and note, the button) and §9 (reworded goal refinements).
- `docs/features/README.md` row and "Status" list.
- CHANGELOG: entry ships with the implementing PR (root CLAUDE.md).

## Non-goals
- Deriving a return from history, a compounding or regression pace: a computed gain/loss (PRD §3, §9).
- A pace or the suggestion on the dashboard card: the card keeps the base scenario.
- An inflation adjustment of the pace: the app has no inflation data (ADR 0035 labels it instead).
- A stored actual contribution per goal, and per-goal contribution tracking: only a computed suggestion.
- A different window length: 12 months, fixed.
- Monte Carlo and the other F039 deferrals (PRD §9).

## Open questions
- Whether `InvestmentHoldingRepository` and `InvestmentOpeningPositionRepository` already offer a read by product ids; add one in the same style if not (a bounded read, with a statement-count pin if the goal queries pin one).
- Whether the pace for a goal with a very large first-month jump from something other than an opening position (a first trade confirmation into a product with an existing snapshot) needs a second exclusion rule; v1 treats only opening positions as the known case.

## Dependencies
F039 (goals, `GoalProjectionQuery`, `GoalHistoryQuery`, `GoalTimelineChart`, `GoalScenarioTable`, `GoalForm`; must be merged first), F038 (opening positions, ADR 0033), F009 (`InvestmentValueSeriesQuery` and its `contributed` series), F031/F032 (Problem Details), F033 (i18n). Touches without changing behavior: the dashboard card (F039), F026 (the FII `AllocationPlan`).
