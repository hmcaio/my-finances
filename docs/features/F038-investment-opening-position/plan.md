# F038 — Action Plan

**Depends on**: F008, F009, F022, F025, F026, F027, F030, F031, F033 (all built). See [ADR 0033](../../adr/0033-opening-position-as-cashless-holding-start.md). Touches F028/F029 (not built, spec amended).

Branch `feature/f038-investment-opening-position`. Backend first: the request/response shapes and the new problem types must exist before the frontend has anything to consume or map. The date-order guard and the read sites are rules-heavy, so each has a test item before its implementation item.

## Phase 0 — Decision record
The ADR and the PRD edits were written by `design-feature` on the docs branch.
- [ ] Re-read [ADR 0033](../../adr/0033-opening-position-as-cashless-holding-start.md) and PRD §5.15, §6.6, §6.9 against what was built and amend them if the implementation diverged (the "Specified, not built (F038)" line in PRD §5.15 is removed in Phase 3).

## Phase 1 — Backend: opening position
- [x] Test first: `InvestmentOpeningPosition` rejects null fields, `quantity <= 0` and `unitPrice <= 0`; `amountContributed()` is `quantity × unitPrice` unrounded; `edit(...)` replaces the three values and keeps the holding; `toAuditSnapshot()` carries `holdingId`, `asOfDate`, `quantity`, `unitPrice`.
- [x] Implement `domain/investmentopeningposition/InvestmentOpeningPosition.java` and `InvestmentOpeningPositionRepository.java`; add `TransferTradeLineRepository.findEarliestDateByHolding(productId, investmentAccountId)`.
- [x] Flyway migration `V27__investment_opening_positions.sql` (table, unique on `holding_id`, `CHECK`s, audit columns), the JPA entity, repository and adapter, the `findEarliestDateByHolding` query on `TransferTradeLineRepositoryAdapter`, plus the migration test (second position for a holding rejected; zero/negative quantity and price rejected).
- [x] Test first: `InvestmentOpeningPositionService.upsert`: creates (audit `recordCreate`) and replaces (audit `recordUpdate`, no-op update skipped); `404` unknown holding; `409` closed holding; `400` future `asOfDate` and each domain violation; `409` when `asOfDate` is after the holding's earliest trade, `200`/accepted on the same day; with `currentValue` it records a snapshot dated `asOfDate` (replacing a same-day one) and without it writes or changes no snapshot, including when the date moves; delete: `404` unknown holding, `404` no position, `409` closed holding, snapshots untouched, audit `recordDelete`. Then implement the service, the four exceptions and the `AuditEntityType.INVESTMENT_OPENING_POSITION` value.
- [x] Test first: `TransferService` create and edit of a trade confirmation rejects (`TradeBeforeOpeningPositionException`, `409`) a line whose holding has a position dated after the transfer date, accepts the same day and any later date, and ignores holdings with no position. Then implement in `requireValidHoldings`.
- [x] Test first: `InvestmentProductService.create` with `openingPosition` creates product, holding, position and snapshot in one transaction, and a failing position (for example a future date) leaves no product and no holding behind. Then implement.
- [ ] Test first: `FiiPortfolioQuery` adds a position's `quantity` and `quantity × unitPrice` to `cotasHeld`/`amountContributed` only when `asOfDate <= asOf`, together with trade lines and across several holdings; a product with only a position shows non-zero totals. Then update.
- [ ] Test first: `InvestmentValueSeriesQuery`: `units` counts the position from its as-of month on, its cost lands in `contributed` for that month only, the months sum to `FiiPortfolioQuery.amountContributed`, and `units` is `null` only when there are neither lines nor positions. Then update.
- [ ] Test first: `InvestmentSnapshotFreshnessQuery`: a position with no snapshot is stale, one dated after the latest snapshot is stale, a snapshot on `asOfDate` is fresh, and a position dated after the evaluation date is ignored. Then update.
- [ ] Test first: `RealHasHoldingHistoryChecker` reports history for a holding that only has a position, and a hard delete of that holding is `409` (not a foreign-key `500`). Then update.
- [ ] Test first: `InvestmentsReportGenerator` writes the `OPENING POSITIONS` section (columns, `dateFrom`/`dateTo` filter on `as_of_date`, `current_value` empty without a same-day snapshot, nullable cells typed). Then update (`docs/conventions/csv-output.md`).
- [ ] REST: `InvestmentOpeningPositionController` (`PUT`/`GET`/`DELETE /api/investment-holdings/{holdingId}/opening-position`), `OpeningPositionRequest`/`OpeningPositionResponse`, the optional `@Valid openingPosition` on `CreateInvestmentProductRequest`, `@ApiResponse` for every status, controller tests for `200`/`201`/`204`/`400`/`404`/`409` including the new `409` on `POST`/`PUT /api/transfers`; regenerate `frontend/src/api/generated/schema.ts` (`npm run generate-api-types -- --offline`).
- [ ] `./gradlew spotlessApply` then `scripts/verify.sh backend` (Docker running; includes the ArchUnit audit rule).

## Phase 2 — Frontend: form section and card
- [ ] Test first: the `InvestmentProductForm` "Opening position" section is collapsed and optional; once started it requires cotas and average price but not current value; the as-of date defaults to today; the payload carries `openingPosition` only when filled. Then implement in `InvestmentProductForm.tsx` and `InvestmentProductsSection.tsx`.
- [ ] Test first: `OpeningPositionCard`/`OpeningPositionDialog` show the position (as-of date, cotas, average price, contributed), "Add opening position" when none (`useOpeningPosition` maps the not-found problem type to `null`), edit, delete with confirmation, actions hidden on a closed holding, and the server's `409`s rendered through `PROBLEM_TYPE_MESSAGES`. Then implement `src/api/investments/investmentOpeningPositions.ts`, `investmentOpeningPositionsQueries.ts`, the MSW handler and fixture, the card and dialog, and wire the card into `InvestmentProductDetailPage.tsx`.
- [ ] `apiError.ts` `PROBLEM_TYPE_MESSAGES` entries and `errors.json`, `investmentProducts.json` and `activity.json` strings in both locales (`localeParity.test.ts`); Activity page type list, `activityEnumLabels.ts`, `fieldLabels.ts`.
- [ ] `scripts/verify.sh frontend`.

## Phase 3 — Docs
- [ ] Root `CLAUDE.md`/backend `CLAUDE.md`: only if a rule easy to violate appears (for example "a new table with an FK to `investment_holdings` is added to `RealHasHoldingHistoryChecker`", if not already stated); `docs/conventions/backend-structure.md` and `docs/conventions/accounts-and-investments.md` as listed in the spec.
- [ ] Remove the "Specified, not built (F038)" line from PRD §5.15, verify the `docs/features/README.md` row against what was built and remove F038 from its "Status" list.
- [ ] `CHANGELOG.md` `[Unreleased]` entry (user-visible: opening positions, `docs/conventions/release.md` format), in the implementing commit.
- [ ] If F028 has shipped, wire `InvestmentSplitAdjustment` into the position (as-of date as the trade date) in `FiiPortfolioQuery` and `InvestmentValueSeriesQuery`, with a test (a split after `asOfDate` multiplies the position, one on or before it does not). If F028 has not shipped, leave a note in F028's plan (already added) and tick this item as deferred.

## Verification
- [ ] Create a product from the form with an opening position (as-of today, 40 cotas, 150.00, current value 6,400): the FII page shows cotas 40, contributed 6,000.00, value 6,400.00, no "needs snapshot" flag, and every cash account's balance is unchanged.
- [ ] Repeat with the current value left empty: the holding shows as needing a snapshot.
- [ ] On an existing holding with no position, add one from the product detail card; edit its quantity; delete it: the FII totals follow each step and the snapshot recorded by the first save is still there after the delete.
- [ ] Record a trade dated before the position's as-of date: `409` with the mapped message; the same date and a later date are accepted.
- [ ] On a holding that already has a trade dated 2026-06-01, add a position dated 2026-07-01: `409`; dated 2026-06-01 or earlier: accepted.
- [ ] Try to hard-delete a holding that only has a position: `409`; delete the position, then the holding is deletable.
- [ ] Add a position to a closed holding, and edit/delete one on a closed holding: `409`, and the card hides the actions.
- [ ] Open the Investments report CSV for a range containing the as-of date: the `OPENING POSITIONS` section lists it; outside the range it does not.
- [ ] The monthly value series chart: `units` is non-null from the position's month on, and the sum of the monthly `contributed` equals the FII page's amount contributed.
- [ ] The Activity page lists the create, update and delete entries of a position with readable labels, in both languages.
- [ ] `scripts/verify.sh backend frontend` (Docker running) all green.
