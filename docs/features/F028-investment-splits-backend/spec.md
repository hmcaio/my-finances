# F028 — Investment Splits (backend)

## Summary
Records a stock/FII split or reverse split (desdobramento/grupamento de cotas) as a new, product-level `InvestmentSplit` event, and corrects the two existing places that compute a product's held quantity (`FiiPortfolioQuery.cotasHeld`, `InvestmentValueSeriesQuery`'s monthly `units`) so they stay right after one happens. See [ADR 0025](../../adr/0025-investment-split-as-read-side-adjustment.md), which this spec implements, and PRD §5.14/§6.6. Backend only — the product-detail-page UI is [F029](../F029-investment-splits-frontend/spec.md).

## Scope
- `InvestmentSplit`: `id`, `investmentProductId`, `effectiveDate`, `beforeUnits`/`afterUnits` (positive integers), optional `additionalNotes`. Append-only — create and delete, no edit.
- A shared read-side adjustment: multiply a trade line's quantity by the cumulative ratio of every split for its product that's effective after the trade's date and on/before the point being computed.
- Wiring that adjustment into `FiiPortfolioQuery` and `InvestmentValueSeriesQuery`, the only two places that currently sum trade-line quantities into a reported total.
- CRUD endpoint, CSV export row.
- Out of scope: any frontend; cash-in-lieu for a fractional remainder (ADR 0025); edit (delete + recreate covers correction); splits on anything other than an `InvestmentProduct` (no per-holding override — ADR 0025).

## Decisions
See ADR 0025 for the full rationale. Implementation-level decisions not already in the ADR:
- **Validation split between domain and application layers**, matching the `TradeConfirmation`/F027 precedent: the domain constructor enforces `beforeUnits > 0`, `afterUnits > 0`, `beforeUnits != afterUnits` (a 1:1 "split" is a no-op, almost certainly a data-entry mistake) via a bare `IllegalArgumentException`; `InvestmentSplitService.create` catches it and rethrows the new `InvalidInvestmentSplitException` (`400`), since a bare `IllegalArgumentException` would otherwise reach `GlobalExceptionHandler`'s catch-all as a `500`. `effectiveDate` in the future is checked at the application layer (needs `Clock`, which a domain constructor doesn't take) and throws the same `InvalidInvestmentSplitException` — both cases are malformed-regardless-of-state, so `400`, not `409` (backend `CLAUDE.md`'s status-choice rule).
- **`investmentProductId` existence**: `404` `InvestmentProductNotFoundException` (existing exception, reused) if the product doesn't exist — no new exception needed.
- **Delete has no guard.** Nothing references an `InvestmentSplit` row (it isn't a trade line, isn't a holding), so `InvestmentSplitService.delete` is a plain repository delete, `404` if the id is unknown, no `AccountUsageChecker`-style check and no `409` case.
- **Product hard-delete cascades.** `investment_splits.investment_product_id` is `ON DELETE CASCADE`. A product can only be hard-deleted while it has zero holdings (PRD §6.6); a split recorded on a product that was never actually held (no holding ever created) would otherwise be orphaned when the product is deleted — cascading removes it instead of blocking the delete or leaving a dangling row.
- **Adjustment helper shape**: `InvestmentSplitAdjustment` (application/investmentreport, plain static utility — same "no repository, pure function over data the caller already fetched" shape as `FuelRatiosQuery`) exposes `BigDecimal adjustedQuantity(List<InvestmentSplit> productSplits, BigDecimal rawQuantity, LocalDate tradeDate, LocalDate asOf)`: multiplies `rawQuantity` by `afterUnits/beforeUnits` for every split in `productSplits` with `effectiveDate.isAfter(tradeDate) && !effectiveDate.isAfter(asOf)`. Each caller fetches `InvestmentSplitRepository.findByProductId` once per product (same granularity as the existing trade-line fetch) and reuses the list across every trade line / month point for that product — no N+1.
- **Reference point per caller**: `FiiPortfolioQuery` calls it once per trade line with `asOf = today` (the `Clock`-sourced date already in scope). `InvestmentValueSeriesQuery` calls it per trade line *per month point* with `asOf` = that point's own date (already computed as `point` in the existing loop) — so a month before a split shows the true pre-split count and a month at/after it shows the true post-split count, exactly ADR 0025's "real step, not a smoothing artifact."
- **Not touched**: `InvestmentSnapshot.balance`, the series' `value`/`contributed` fields, `TradeConfirmationLine`/`transfer_trade_lines` rows (quantity/unitPrice stay exactly as recorded), `FiiAllocationQuery` (keys off snapshot value, not quantity).

## Backend

### Domain
- `domain/investmentsplit/InvestmentSplit.java`: entity/value object — `id`, `investmentProductId`, `effectiveDate`, `beforeUnits` (`int > 0`), `afterUnits` (`int > 0`, `!= beforeUnits`), `additionalNotes` (optional, `TextFieldConstraints.MAX_ADDITIONAL_NOTES_LENGTH`, standalone — same deliberate exception as `InvestmentProduct`/`InvestmentHolding` notes, no paired mandatory description). No domain reference to `TradeConfirmationLine` or any other aggregate (ArchitectureTest's no-cross-aggregate-import rule).
- `domain/investmentsplit/InvestmentSplitRepository.java`: port — `save`, `findById`, `findByProductId` (ordered by `effectiveDate`), `deleteById`, `existsById`.

### Application
- `application/investmentsplit/InvestmentSplitService.java`: `create(productId, effectiveDate, beforeUnits, afterUnits, additionalNotes)` (checks product exists, delegates to domain constructor, catches `IllegalArgumentException` → `InvalidInvestmentSplitException`), `findByProductId`, `delete` (404 if unknown, otherwise unconditional).
- `application/investmentsplit/InvestmentSplitNotFoundException.java` (`404`), `application/investmentsplit/InvalidInvestmentSplitException.java` (`400`).
- `application/investmentreport/InvestmentSplitAdjustment.java`: the shared static multiplier (see Decisions).
- `FiiPortfolioQuery`/`InvestmentValueSeriesQuery`: inject `InvestmentSplitRepository`; wrap each trade line's `quantity` through `InvestmentSplitAdjustment.adjustedQuantity(...)` before accumulating into `cotasHeld`/`units`.
- `DataExportService`: new `writeInvestmentSplits` → `investment_splits.csv` (`id`, `investment_product_id`, `product_name`, `effective_date`, `before_units`, `after_units`, `additional_notes`), following the existing one-port-per-entity export pattern.
- No audit-log wiring: F025 (the `AuditLog` port) is documented but not yet built (root `README.md`'s Project status), same as every other aggregate added since — nothing to wire into. If F025 ships before this does, add `InvestmentSplitService.create`/`delete` to whatever port it introduces; don't block this feature on that order.

### Persistence
- New migration `V23__investment_splits.sql`: `investment_splits` (`id uuid pk`, `investment_product_id uuid not null references investment_products on delete cascade` (indexed), `effective_date date not null`, `before_units integer not null check (before_units > 0)`, `after_units integer not null check (after_units > 0 and after_units != before_units)`, `additional_notes varchar(500)`, audit columns). No seed data, no backfill (brand-new concept, nothing to migrate).
- JPA entity `InvestmentSplitJpaEntity` (extends `AuditableEntity`, Lombok `@Getter`/`@Setter`/`@NoArgsConstructor`), package-private Spring Data repository, `InvestmentSplitRepositoryAdapter` implementing the domain port via `reconstitute(...)`.

### API
- `POST /api/investment-splits`: `{investmentProductId, effectiveDate, beforeUnits, afterUnits, additionalNotes}` → `201` with the created representation. `@Positive` on both unit fields, `@Size(max = 500)` on notes (DTO layer, mirroring the domain check as defense in depth per backend `CLAUDE.md`).
- `GET /api/investment-splits?productId=`: plain list (not paged — inherently small per product, same convention as budgets/templates), ordered by `effectiveDate`. `404` if `productId` doesn't exist.
- `DELETE /api/investment-splits/{id}`: `204`, `404` if unknown.
- Regenerate `frontend/src/api/generated/schema.ts`.

## Dependencies
F008 (`InvestmentProduct`), F009/F026/F027 (`FiiPortfolioQuery`/`InvestmentValueSeriesQuery`, the two read sites this corrects), F013 (data export). [F029](../F029-investment-splits-frontend/spec.md) (frontend) depends on this.
