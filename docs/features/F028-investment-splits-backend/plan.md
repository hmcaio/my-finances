# F028 — Action Plan

**Depends on**: F008, F009, F013, F026, F027. See [ADR 0025](../../adr/0025-investment-split-as-read-side-adjustment.md). [F029](../F029-investment-splits-frontend/plan.md) (frontend) starts after this ships.

## Backend
- [ ] Write tests for `InvestmentSplit` (constructor rejects non-positive `beforeUnits`/`afterUnits` and `beforeUnits == afterUnits`; `additionalNotes` bounded, optional), then implement.
- [ ] Write tests for `InvestmentSplitAdjustment.adjustedQuantity` (no splits → unchanged; one split after the trade date and on/before `asOf` → multiplied; a split on/before the trade date or after `asOf` → ignored; multiple splits compound; a reverse split's ratio `< 1` divides correctly), then implement.
- [ ] Write tests for `InvestmentSplitService` (`create` 404s on an unknown product, 400s via `InvalidInvestmentSplitException` on a domain invariant violation and on a future `effectiveDate`; `findByProductId`; `delete` 404s on an unknown id, otherwise unconditional), then implement.
- [ ] Write tests for `FiiPortfolioQuery.cotasHeld` and `InvestmentValueSeriesQuery`'s `units` against a product with a split: a month before the split shows the true pre-split count, a month at/after shows the true post-split count, the current total matches the series' latest point, then update both queries to use `InvestmentSplitAdjustment`.
- [ ] JPA entity, repository, adapter for `InvestmentSplit`; Flyway migration `V23__investment_splits.sql` (table + `CHECK` constraints + `ON DELETE CASCADE`).
- [ ] REST: `InvestmentSplitController` (`POST`/`GET ?productId=`/`DELETE /{id}`), request/response DTOs, controller tests for the 201/200/204/400/404 cases. Regenerate `frontend/src/api/generated/schema.ts` against a locally run backend.
- [ ] `DataExportService.writeInvestmentSplits` → `investment_splits.csv`; update `DataExportServiceTest`'s fixed file list/count and `DataExportControllerTest`.
- [ ] Update root `CLAUDE.md`'s "money columns" bullet if `before_units`/`after_units` need calling out (they're plain integers, not money — confirm no new exception needed) and backend `CLAUDE.md` with the "any future read site summing trade-line quantities must call `InvestmentSplitAdjustment`" risk note (ADR 0025's Consequences).

## Docs
- [ ] `docs/features/README.md` row for F028 (and F029 once it exists).
- [ ] Root `README.md` "Project status" entry.
- [ ] `CHANGELOG.md` `[Unreleased]` entry (`**F028 — Investment Splits (backend)**`, user-visible: cotas-held/portfolio totals now correct after a split); PR link added once the PR is open.
- [ ] Tick this plan.

## Verification
- [ ] Record a 1:10 split on a product with existing buy trades before the split date: `cotasHeld` and the latest `units` series point both reflect the ×10 count; the product's `TradeConfirmationLine` rows are unchanged (query them directly).
- [ ] Record a 10:1 reverse split: totals divide correctly.
- [ ] Record two splits on the same product at different dates: cumulative ratio compounds correctly for a trade before both.
- [ ] A trade recorded *after* the split's effective date is not multiplied (already in post-split terms).
- [ ] Attempt a 1:1 split, a zero/negative unit count, and a future `effectiveDate`: all rejected with `400`.
- [ ] Delete a split: totals revert to their pre-split (unadjusted) values for any point after the deleted split's date.
- [ ] Delete the product's last holding, then hard-delete the product while a split still exists on it: the split row is gone too (cascade), no FK error.
- [ ] Export a ZIP: `investment_splits.csv` present and correct.
