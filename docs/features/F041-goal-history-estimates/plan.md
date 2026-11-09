# F041 — Action Plan

**Depends on**: F039 (must be merged first), F038, F009, F031, F033. See [ADR 0035](../../adr/0035-goal-estimates-from-history.md). The ADR and the PRD edits (§3, §5.16, §6.17, §9) are written with the spec, so there is no decision-record phase.

Branch `feature/f041-goal-history-estimates`. Backend first (the `pace` field and the endpoint must exist before the frontend has anything to consume); pure math before queries; no migration.

## Phase 1 — Backend: pace
- [ ] Test first (`GoalProjectionMath`): `monthlyPace` of a rising series is `(last − first) ÷ (count − 1)` to the cent; a flat series is `0`; a falling series is negative; fewer than 4 values returns no pace; `paceMonthsToTarget` is `0` when already reached, `null` for a zero or negative pace, `null` beyond 600 months, `ceil((target − value) ÷ pace)` otherwise.
- [ ] Implement the two functions in `application/goal/GoalProjectionMath` and `application/goal/PaceProjection`.
- [ ] Test first (`GoalPaceQuery`, fakes, fixed `Clock`, non-UTC zone): leading zero months are skipped; the window starts after the latest opening position of any allocated product's holding; fewer than 4 points left is empty; a dated goal gets the value at the target date and the shortfall; a dateless goal gets `monthsToTarget` and the scenarios' horizon, never longer; the pace points start at `allocatedValue` in the current month; a negative pace falls and never reaches the target.
- [ ] Implement `application/goal/GoalPaceQuery`, add the nullable `pace` to `GoalProjection` and fill it in `GoalProjectionQuery`; add a read by product ids to the holding and opening-position ports if missing.
- [ ] REST: `pace` on the `GET /api/goals/{id}/projection` response; controller test for a goal with enough history, one without, and `404`.
- [ ] `./gradlew spotlessApply` then `scripts/verify.sh backend` (Docker running).

## Phase 2 — Backend: contribution suggestion
- [ ] Test first (`GoalContributionSuggestionQuery`): averages the last 12 full months, excluding the current one; starts at the first month with a contribution; multiplies each product's contribution by the goal's percentage; removes an opening position's cost in its as-of month; a net-sell average is `0`; no allocations returns `0` and `0`; a goal that does not exist is `404`.
- [ ] Implement `application/goal/GoalContributionSuggestionQuery`, `ContributionSuggestion` and `GET /api/goals/{id}/contribution-suggestion` in `GoalReportController` (controller test `200/404`).
- [ ] Regenerate `frontend/src/api/generated/schema.ts` (`npm run generate-api-types -- --offline`).
- [ ] `./gradlew spotlessApply` then `scripts/verify.sh backend` (Docker running).

## Phase 3 — Frontend
- [ ] Test first: API module and hook (`pace` on the projection, `getGoalContributionSuggestion`, `useGoalContributionSuggestion` only fetching when enabled) against MSW.
- [ ] Implement the API types, hook, MSW handlers and fixtures (a projection with a pace and one without).
- [ ] Test first (`GoalTimelineChart`): a fourth line (`data-pace`) forks from the "Today" marker and follows the horizon selector; its legend entry; the inflation note appears with the line; "Not enough history for a pace yet." when `pace` is `null` and the goal has allocations, nothing when it has none.
- [ ] Implement the pace line, legend entry and notes in `GoalTimelineChart`.
- [ ] Test first (`GoalScenarioTable`): a "Current pace" row for a dated goal (value at the date, surplus or shortfall) and a dateless goal (time to target, "not reached within 50 years"), "–" in the real-return and contribution-needed cells, no row when `pace` is `null`, a card on a phone.
- [ ] Implement the "Current pace" row in `GoalScenarioTable`.
- [ ] Test first (`GoalForm`): "Suggest from history" appears only when editing a goal with allocations; it fills the planned contribution with the average without saving; it shows "Average of the last N months: X"; a `monthsAveraged` of `0` shows the hint and fills `0`.
- [ ] Implement the button in `GoalForm`, the `pace.*` and `contribution.*` i18n keys (`en`, `pt-BR`) and update the e2e goals spec if its selectors change.
- [ ] `scripts/verify.sh frontend`.

## Phase 4 — Docs
- [ ] `docs/features/README.md` row verified against what was built, and F041 removed from its "Status" list; `CHANGELOG.md` entry under `## [Unreleased]` in the same commit as the code.
- [ ] Root/backend/frontend `CLAUDE.md`: no new rule expected; add one only if implementation surfaces a gotcha the code cannot show.

## Verification
- [ ] Open a goal with allocated products and at least 4 months of snapshots (the dev seed) -> the chart shows a fourth grey line labeled "Pace (based on history)" from the "Today" marker, the note about inflation, and the table has a "Current pace" row.
- [ ] Open a goal whose products have no history yet -> "Not enough history for a pace yet." and no fourth line or row.
- [ ] Open a goal allocated to a product that has an opening position -> the pace ignores the jump of the opening position (the line is not steep from it).
- [ ] Choose the 1-year horizon on a long goal -> the pace line is cut with the scenarios and the history stays.
- [ ] Edit a goal with allocations, click "Suggest from history" -> the planned contribution fills with the 12-month average and nothing is saved until Save; create a new goal -> no such button.
- [ ] Switch the language to pt-BR -> every new string is translated.
- [ ] `scripts/verify.sh backend frontend` (Docker running) all green.
