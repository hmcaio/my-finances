# F026 — Action Plan

**Depends on**: F001, F002, F004, F005, F008, F009, F013, F015, F022, F023 (all built). See [ADR 0023](../../adr/0023-fii-allocation-plan-and-dividends.md).

## Backend
- [x] Write tests first for `InvestmentSegment` (name required/capped, rename), then implement.
- [x] Write tests for `InvestmentProduct` gaining `ticker`/`segmentId` (optional, capped, carried through `create`/`edit`/`reconstitute`), then update the domain class.
- [x] Write tests for `AllocationPlanVersion`/`AllocationPlanEntry` (at least one entry, no duplicate product, each percentage positive, entries sum to exactly 100, `resolveEffective` picks the latest version `<=` the target month, `updateEntries` replaces in place), then implement, plus the trivial `AllocationPlan` marker aggregate.
- [x] Write tests for `Category` gaining `dividendCategory` (immutable post-creation like `builtIn`/`fuelCategory`), then update.
- [x] Write tests for `Transaction` gaining `investmentHoldingId` (create/edit carry it through unchanged otherwise), then update.
- [x] Write tests for `InvestmentSegmentService` (create, rename, delete blocked while referenced by a product), then implement.
- [x] Write tests for `InvestmentProductService`'s `ticker`/`segmentId` handling (unknown `segmentId` is 404), then update.
- [x] Write tests for `AllocationPlanService` (`setAllocation` creates a new version going forward, same-month replace, rejects a sum != 100, rejects a non-FII product, rejects an unknown product, rejects a duplicate product within one call; `getCurrent` resolves correctly; creates the implicit `AllocationPlan` row on first use), then implement.
- [x] Write tests for `CategoryService`'s new `dividend_category` delete **and** rename guards (409, independent of `built_in`/`fuel_category`), then implement.
- [x] Write tests for `TransactionService`'s dividend invariant — dividend-category transaction without `investmentHoldingId` rejected (400), non-dividend-category transaction with `investmentHoldingId` rejected (400), unknown `investmentHoldingId` rejected (404) — on create and edit, then implement.
- [x] Write tests for `FiiPortfolioQuery` (cotasHeld/amountContributed match hand-computed sums across multiple holdings/accounts for one product, sells reduce both correctly, status filter open/closed/all, currentValue/needsSnapshot match the existing holding-level computations rolled up per product), then implement.
- [x] Write tests for `FiiAllocationQuery` (actual-by-ticker/segment percentages sum to 100 across FII products only, unsegmented product groups under "No segment", planned-by-ticker mirrors the current plan version, planned-by-segment sums correctly), then implement.
- [x] Write tests for `DividendHistoryQuery` (filters by product/date range, totals by ticker and by month), then implement.
- [x] JPA entities/repositories/adapters for `InvestmentSegment`, `AllocationPlan`, `AllocationPlanVersion` (+ `AllocationPlanEntry` as an embedded/child table); extend `InvestmentProductJpaEntity`, `CategoryJpaEntity`, `TransactionJpaEntity` and their adapters for the new columns.
- [x] Flyway migration (next free `V` number): `investment_segments`; `investment_products.ticker`/`segment_id`; `categories.dividend_category` + partial unique index + seed (fresh insert, not adopt-existing); `transactions.investment_holding_id`; `allocation_plans`/`allocation_plan_versions`/`allocation_plan_entries`. Migration test against pre-existing data confirming existing rows are unaffected.
- [x] REST: investment-segment endpoints; investment-product DTOs gaining `ticker`/`segmentId`; transaction DTOs gaining `investmentHoldingId`; `/api/fii/portfolio`, `/api/fii/allocation`, `/api/fii/allocation-plan`(+`/versions`), `/api/fii/dividends`(+`/totals`); controller tests; regenerate `frontend/src/api/generated/schema.ts`.
- [x] Update root `CLAUDE.md`: `target_percentage` (`numeric(5,2)`) as a new exception to the `numeric(19,2)` rule; add `InvestmentSegment` to the flat-taxonomy `MAX_NAME_LENGTH` list.

## Frontend
- [x] `src/api/investments/investmentSegments.ts`, MSW handlers; extend `investmentProducts.ts` for `ticker`/`segmentId`; new `allocationPlan.ts`, `fiiPortfolio.ts`, `fiiDividends.ts`; extend `transactions.ts` for `investmentHoldingId`.
- [x] Extend the investment product form: optional ticker field, segment select.
- [x] Investment segment CRUD screen under Settings.
- [x] New FII page: portfolio list with status filter, allocation-plan editor (live running-total validation), four pie charts reusing `FlatAllocationDonutChart`, "Register Dividend" dialog, dividend history list with filters and totals.
- [x] Nav entry for the FII page.

## Verification
- [x] Add a ticker/segment to an existing FII product: portfolio list shows it, product form round-trips both fields.
- [x] Record buy/sell trades for a product across two accounts: portfolio list's cotas held and amount contributed match the hand-computed net; selling the entire position drops cotas held to zero without affecting other products.
- [x] Set an allocation plan summing to 100%: accepted. Attempt a plan summing to 99% or including a non-FII product: rejected.
- [x] Edit the plan for the current month twice: second edit replaces the same version (one row in plan history), not two.
- [x] Edit the plan for a future month: a new version; the current month's percentages are unaffected until that month arrives.
- [x] All four pie charts render and sum to 100% (or to the FII-only total for the actual charts); an unsegmented product's value appears under "No segment" in both segment charts.
- [x] Register a dividend via the dedicated form: creates a transaction with the dividend category and the holding reference; appears in dividend history grouped by ticker and month.
- [x] Attempt to save a dividend-category transaction with no holding reference, or a non-dividend transaction with one: both rejected.
- [x] Attempt to delete or rename the dividend category, and to delete a segment referenced by a product: all rejected (409).
- [x] Export a ZIP: `investment_segments.csv`, `allocation_plan_entries.csv` present; `investment_products.csv` carries ticker/segment; `transactions.csv` carries `investment_holding_id` for dividend rows, empty for others.

## Addendum — Month Selector

A month picker on the FII page so the portfolio list and all four allocation charts can show a past month's situation, not just today's. `FiiPortfolioQuery`/`FiiAllocationQuery` are currently hardcoded to `Clock.now`; most of the underlying as-of machinery already exists elsewhere (`LatestInvestmentSnapshotQuery.latestOf(holdingId, asOfDate)`, `InvestmentSnapshotFreshnessQuery.staleHoldingIds(date)`, `AllocationPlanService.getCurrent(month)`) and just needs threading through instead of being built from scratch. `needsSnapshot` stays a "today" concept — suppressed whenever a past month is selected, not recomputed as-of that month.

### Backend
- [x] Write tests for `FiiPortfolioQuery.portfolio(status, asOf)` (new `asOf` param, default today): trades dated after `asOf` excluded from `cotasHeld`/`amountContributed`; `currentValue`/`latestSnapshotDate` use `latestSnapshotQuery.latestOf(holdingId, asOf)` instead of the no-arg overload; `needsSnapshot` always `false` when `asOf` isn't today (never computed against a past date); then implement.
- [x] Write tests for `FiiAllocationQuery.allocation(basis, groupBy, month)` (new `month` param, `YearMonth`, default current month): `ACTUAL` converts `month` to an as-of date using the existing F009/F010 convention (month-end, except the current month evaluated at today) and threads it into `FiiPortfolioQuery`; `PLANNED` passes `month` straight to `allocationPlanService.getCurrent(month)` instead of always `YearMonth.now(clock)`; then implement.
- [x] Controller: add `?month=` (optional, `YearMonth`, default current month) to `GET /api/fii/portfolio` and `GET /api/fii/allocation`, converting to the as-of date server-side; controller tests for a past month, the current month, and an omitted param (defaults to current). Regenerate `frontend/src/api/generated/schema.ts`.

### Frontend
- [x] Add a month-picker control to the FII page (reuse whichever existing component the Budget or net-worth-trend page already uses for this), defaulting to the current month.
- [x] Wire the picker into the portfolio-list query hook and all four allocation-chart query hooks (`fiiPortfolio.ts`/`fiiAllocation.ts` + their `*Queries.ts`), forwarding `month`; suppress/hide the `needsSnapshot` badge on the portfolio list whenever a past month is selected.
- [x] Disable or clamp selecting a future month (consistent with how other monthly views in the app treat the current month as the latest selectable point).

### Verification
- [x] Select a past month on the FII page: portfolio list's cotas/contributed/current value reflect only trades/snapshots on or before that month's cutoff; `needsSnapshot` badges don't show.
- [x] Select a month before any allocation-plan version existed: planned charts render empty/zero rather than erroring.
- [x] Select a month between two plan versions: planned charts show the version effective for that month, not the current one.
- [x] Switch back to the current month: behavior matches what shipped before this addendum (today's trades/snapshots, `needsSnapshot` badges restored).

## Addendum — Nested Allocation Charts

Merges the FII page's four flat donuts into two nested (two-ring) donuts: Actual (inner ring = segment, outer ring = ticker, each ticker arc sitting under its segment's arc) and Planned, the same way. `FiiAllocationQuery` already returns both groupings (`TICKER`/`SEGMENT`) from the same underlying data — a ticker row's `totalValue` (`ACTUAL`) or `percentage` (`PLANNED`) already sums exactly into its segment's own row, with no independent rounding between the two (segment totals are a literal `BigDecimal::add` of the same ticker values/percentages). So the two rings can be drawn with mathematically exact matching boundaries by computing both rings' arc angles from the same raw fraction-of-total (`totalValue` for `ACTUAL`, `percentage` for `PLANNED`, never the already-rounded display `percentage` string for `ACTUAL`) — no backend rounding/alignment trick needed, just one new field so the frontend knows which ticker belongs to which segment.

### Backend
- [x] Write a test for `FiiAllocationRow` gaining `segmentId` (nullable `UUID`): populated on `TICKER`-groupBy rows (the ticker's own segment, `null` for an unsegmented product), always `null` on `SEGMENT`-groupBy rows (redundant with `key` there), then add the field and populate it in `actualByTicker`/`plannedByTicker` (both already compute/look up each row's segment id for the existing sort - reuse it).
- [x] Controller/DTO: `FiiAllocationRowResponse` (or equivalent) gains `segmentId`. Controller test confirming a `TICKER` row's `segmentId` matches its product's segment, and a `SEGMENT` row's is `null`. Regenerate `frontend/src/api/generated/schema.ts`.

### Frontend
- [x] `donutGeometry.ts`: parametrize `segmentPath` with optional `outerRadius`/`innerRadius` arguments defaulting to the existing `RADIUS`/`INNER_RADIUS` constants, so every current caller (`FlatAllocationDonutChart`, `InvestmentAllocationChart`'s own copy) keeps compiling unchanged while the new chart below can draw two different radius bands.
- [x] New `FiiNestedAllocationDonutChart.tsx` (replaces `FiiAllocationDonutChart.tsx`, deleted along with its test): takes `basis` and `month`, fetches both `useFiiAllocation(basis, 'SEGMENT', month)` and `useFiiAllocation(basis, 'TICKER', month)` (same two queries the four old charts already made between them - no new network calls), groups the ticker rows by `segmentId` preserving the order the backend already sorts them in (segment-cluster, then ticker size - no client re-sort needed), and draws:
  - Inner ring: one arc per segment, width = the **sum of that segment's own outer-ring children's percentages** (not the backend's independently-rounded segment-row percentage, and not a `totalValue`/grand-total ratio) - this is what guarantees the two rings tile with zero seam, by construction, for both `ACTUAL` and `PLANNED` alike (no `totalValue` dependency needed, since `PLANNED` has none).
  - Outer ring: one arc per ticker row, using its own `percentage` directly, flattened across all segments in ring order - cumulative-summing the exact same numbers the inner ring's widths were built from.
  - Color: each segment gets a base hue (`SLICE_COLORS`, cycled by its position in the inner ring); its child ticker arcs reuse that same hue at decreasing opacity (`1 - index * 0.22`, floored at `0.35`), so the grouping reads visually without a new color-math dependency.
  - Legend: one combined list - segment name (bold, inner-ring swatch) followed by its indented tickers (outer-ring swatch, shaded), in ring order.
- [x] `FiiPage.tsx`: two chart cards (Actual, Planned) instead of four, same `Grid size={{xs:12, md:6}}` responsive pattern; drop the four old `ariaLabel`/`emptyMessage` chart instances for two.
- [x] Update `frontend/CLAUDE.md`'s Charts section: `FiiNestedAllocationDonutChart` replaces `FiiAllocationDonutChart` in the list of hand-drawn SVG charts, noting it's the one two-ring chart in the app and why (`donutGeometry.ts`'s `segmentPath` gains optional radius params for it, every other caller unaffected).

### Verification
- [x] Both nested charts render: inner ring sums to 100% (or the FII-only total for Actual), each segment's outer-ring child arcs visually tile exactly under its inner arc (no gap/overlap at the boundary).
- [x] An unsegmented ticker's arc appears under the "No segment" inner-ring slice, same as the ticker previously appeared under "No segment" in the old flat segment chart.
- [x] A segment with only one ticker: inner arc and that ticker's outer arc span the exact same angle.
- [x] Switching the month selector updates both rings of both charts together, consistent with the flat charts' prior behavior.
- [x] Legend lists every segment with its tickers indented beneath it, in the same order as the rings.
