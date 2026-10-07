# F026 — Action Plan

**Depends on**: F001, F002, F004, F005, F008, F009, F013, F015, F022, F023 (all built). See [ADR 0023](../../adr/0023-fii-allocation-plan-and-dividends.md).

## Backend
- [ ] Write tests first for `InvestmentSegment` (name required/capped, rename), then implement.
- [ ] Write tests for `InvestmentProduct` gaining `ticker`/`segmentId` (optional, capped, carried through `create`/`edit`/`reconstitute`), then update the domain class.
- [ ] Write tests for `AllocationPlanVersion`/`AllocationPlanEntry` (at least one entry, no duplicate product, each percentage positive, entries sum to exactly 100, `resolveEffective` picks the latest version `<=` the target month, `updateEntries` replaces in place), then implement, plus the trivial `AllocationPlan` marker aggregate.
- [ ] Write tests for `Category` gaining `dividendCategory` (immutable post-creation like `builtIn`/`fuelCategory`), then update.
- [ ] Write tests for `Transaction` gaining `investmentHoldingId` (create/edit carry it through unchanged otherwise), then update.
- [ ] Write tests for `InvestmentSegmentService` (create, rename, delete blocked while referenced by a product), then implement.
- [ ] Write tests for `InvestmentProductService`'s `ticker`/`segmentId` handling (unknown `segmentId` is 404), then update.
- [ ] Write tests for `AllocationPlanService` (`setAllocation` creates a new version going forward, same-month replace, rejects a sum != 100, rejects a non-FII product, rejects an unknown product, rejects a duplicate product within one call; `getCurrent` resolves correctly; creates the implicit `AllocationPlan` row on first use), then implement.
- [ ] Write tests for `CategoryService`'s new `dividend_category` delete **and** rename guards (409, independent of `built_in`/`fuel_category`), then implement.
- [ ] Write tests for `TransactionService`'s dividend invariant — dividend-category transaction without `investmentHoldingId` rejected (400), non-dividend-category transaction with `investmentHoldingId` rejected (400), unknown `investmentHoldingId` rejected (404) — on create and edit, then implement.
- [ ] Write tests for `FiiPortfolioQuery` (cotasHeld/amountContributed match hand-computed sums across multiple holdings/accounts for one product, sells reduce both correctly, status filter open/closed/all, currentValue/needsSnapshot match the existing holding-level computations rolled up per product), then implement.
- [ ] Write tests for `FiiAllocationQuery` (actual-by-ticker/segment percentages sum to 100 across FII products only, unsegmented product groups under "No segment", planned-by-ticker mirrors the current plan version, planned-by-segment sums correctly), then implement.
- [ ] Write tests for `DividendHistoryQuery` (filters by product/date range, totals by ticker and by month), then implement.
- [ ] JPA entities/repositories/adapters for `InvestmentSegment`, `AllocationPlan`, `AllocationPlanVersion` (+ `AllocationPlanEntry` as an embedded/child table); extend `InvestmentProductJpaEntity`, `CategoryJpaEntity`, `TransactionJpaEntity` and their adapters for the new columns.
- [ ] Flyway migration (next free `V` number): `investment_segments`; `investment_products.ticker`/`segment_id`; `categories.dividend_category` + partial unique index + seed (fresh insert, not adopt-existing); `transactions.investment_holding_id`; `allocation_plans`/`allocation_plan_versions`/`allocation_plan_entries`. Migration test against pre-existing data confirming existing rows are unaffected.
- [ ] REST: investment-segment endpoints; investment-product DTOs gaining `ticker`/`segmentId`; transaction DTOs gaining `investmentHoldingId`; `/api/fii/portfolio`, `/api/fii/allocation`, `/api/fii/allocation-plan`(+`/versions`), `/api/fii/dividends`(+`/totals`); controller tests; regenerate `frontend/src/api/generated/schema.ts`.
- [ ] Update root `CLAUDE.md`: `target_percentage` (`numeric(5,2)`) as a new exception to the `numeric(19,2)` rule; add `InvestmentSegment` to the flat-taxonomy `MAX_NAME_LENGTH` list.

## Frontend
- [ ] `src/api/investments/investmentSegments.ts`, MSW handlers; extend `investmentProducts.ts` for `ticker`/`segmentId`; new `allocationPlan.ts`, `fiiPortfolio.ts`, `fiiDividends.ts`; extend `transactions.ts` for `investmentHoldingId`.
- [ ] Extend the investment product form: optional ticker field, segment select.
- [ ] Investment segment CRUD screen under Settings.
- [ ] New FII page: portfolio list with status filter, allocation-plan editor (live running-total validation), four pie charts reusing `FlatAllocationDonutChart`, "Register Dividend" dialog, dividend history list with filters and totals.
- [ ] Nav entry for the FII page.

## Verification
- [ ] Add a ticker/segment to an existing FII product: portfolio list shows it, product form round-trips both fields.
- [ ] Record buy/sell trades for a product across two accounts: portfolio list's cotas held and amount contributed match the hand-computed net; selling the entire position drops cotas held to zero without affecting other products.
- [ ] Set an allocation plan summing to 100%: accepted. Attempt a plan summing to 99% or including a non-FII product: rejected.
- [ ] Edit the plan for the current month twice: second edit replaces the same version (one row in plan history), not two.
- [ ] Edit the plan for a future month: a new version; the current month's percentages are unaffected until that month arrives.
- [ ] All four pie charts render and sum to 100% (or to the FII-only total for the actual charts); an unsegmented product's value appears under "No segment" in both segment charts.
- [ ] Register a dividend via the dedicated form: creates a transaction with the dividend category and the holding reference; appears in dividend history grouped by ticker and month.
- [ ] Attempt to save a dividend-category transaction with no holding reference, or a non-dividend transaction with one: both rejected.
- [ ] Attempt to delete or rename the dividend category, and to delete a segment referenced by a product: all rejected (409).
- [ ] Export a ZIP: `investment_segments.csv`, `allocation_plan_entries.csv` present; `investment_products.csv` carries ticker/segment; `transactions.csv` carries `investment_holding_id` for dividend rows, empty for others.
