# F023 — Investments Page Refactor

## Summary
Rebuilds `/investments` as a dashboard: the existing category allocation chart (kept, with its click-to-drill into sub-categories) alongside two new pie charts — sub-category allocation as a flat share of the whole portfolio, and allocation by account (enabled by F022's holdings) — with the existing accounts-value table and a new global, filtered, paginated product list as two sub-tabs below. Builds on F022; does not change the data model further.

## Scope
- Page layout: a row of three pie charts (Category with drill-down, Subcategory flat, Account) above two sub-tabs (Accounts, Products).
- New `GET /api/investments/allocation?groupBy=ACCOUNT` grouping (F009's endpoint gains a third `groupBy` value).
- New global product list: server-paginated, filters by category, sub-category, name, account (has a holding there), status (derived Open/Closed/All from the product's holdings).
- Product list rows are read-only, linking to the product detail page (F022) for management.
- Out of scope: any further domain model change (F022 covers all of that); per-holding UI beyond what F022 already built for the product detail page.

## Decisions
- **Three charts, not a groupBy toggle on one.** The category chart's drill-down already answers "this category's sub-categories"; the new sub-category chart answers a different question ("each sub-category's share of the whole portfolio, regardless of category") and is kept as its own always-visible chart rather than folded into the drill-down.
- **The global product list is read-only.** Create/edit/close/add-holding stay on the product detail page (F022) — the list's job is filtering and navigation, not management, to avoid duplicating that flow in two places.
- **Status filter is derived, not stored**: "Open" = the product has at least one open holding; "Closed" = every holding is closed (or it has none). Computed on read, same pattern as `needsSnapshot`. Default `Open`.
- **Allocation by account sums a holding's latest snapshot into its account** — the same "latest snapshot per holding" query F022 introduced, just a new grouping key, no new value computation.

## Backend

### Application
- `InvestmentAllocationQuery` (F009's real class name — the spec originally said "AllocationService"): add `groupBy=ACCOUNT`. Rows: `{accountId, accountName, totalValue, needsSnapshot}` (the existing `AllocationRow`/`AllocationRowResponse` shape gains `accountId`/`accountName` alongside the existing `categoryId`/`categoryName`/`subcategoryId`/`subcategoryName`, all `null` for this grouping — one row shape shared by every grouping, as it already was for CATEGORY/SUBCATEGORY) — sum of the latest snapshots of holdings in that account; `needsSnapshot` true if any holding there is stale. An account with no holdings, or all-zero/absent snapshots and nothing stale, is omitted — same rule as the existing groupings.
- New paginated/filtered product list query (`InvestmentProductService.findAll(InvestmentProductFilter, Pageable)`, an overload alongside the existing no-arg `findAll()`): computed in memory over the existing repositories (same "computed on read" style as `InvestmentAllocationQuery`) rather than a DB-level query, since `accountId`/`status` both read through `InvestmentHoldingRepository`, not columns on the product. `PagedModel` (matching Transactions' pattern), filtered by `categoryId`, `subcategoryId`, `accountId` (has a holding there), `name` (contains, case-insensitive), `status` (`OPEN`/`CLOSED`/`ALL`, default `OPEN`, derived from the product's holdings). `InvestmentProductService.isClosed(productId)` exposes the same derivation for a single product.
- `InvestmentProductResponse` gains a derived, not-stored `closed` boolean (every holding closed, or none at all) so the global product list can show each row's own status even under `status=ALL`, where rows of both statuses appear together and the active filter alone can't tell them apart.

### API
- `GET /api/investments/allocation?groupBy=CATEGORY|SUBCATEGORY|ACCOUNT` (extends F009's endpoint).
- `GET /api/investment-products?page=&size=&categoryId=&subcategoryId=&accountId=&name=&status=` returns `PagedModel<InvestmentProductResponse>` (F022's plain list becomes paginated — a breaking shape change, fine pre-1.0/local app). Existing callers of the plain list (`InvestmentProductsSection`, the transfer form) keep calling `getInvestmentProducts()`, whose frontend implementation now loops every page with `status=ALL` to preserve "every product" behavior.
- Regenerate `frontend/src/api/generated/schema.ts`.

## Frontend
- `/investments` (`InvestmentsPage`): three-chart row (`InvestmentAllocationChart` for Category, unchanged; new `InvestmentSubcategoryAllocationChart`, flat, no drill-down; new `InvestmentAccountAllocationChart`) above `Tabs`: "Accounts" (today's table, unchanged) and "Products" (new). The two new charts share a `FlatAllocationDonutChart` presentational component and `donutGeometry.ts`'s pure SVG-math helpers rather than each re-implementing the donut/legend; `InvestmentAllocationChart` (category, with its drill-down) is untouched and keeps its own copy, so its existing tests/behavior can't be affected by the refactor.
- New products list, `InvestmentProductsListSection` (under `features/investments/`, the "Products" tab's content): `ResponsiveTable` (columns: name, category, sub-category, status; `tabletPriority` per the F021 pattern), `ResponsiveFilterBar` (category/sub-category/account selects, name text field, status select), `PaginationControls`, page size 20 — mirrors `TransactionsPage`'s structure. A row's name links to the product detail page (F022); `ResponsiveTable` has no built-in row-click, so the link is the name cell/card title, the same pattern `InvestmentProductsSection` already uses.
- `src/api/investments/investmentAllocation.ts`: add the `ACCOUNT` grouping and the row's `accountId`/`accountName` fields (the seven investment API modules share `src/api/investments/`, not a top-level `src/api/investmentAllocation.ts`); `investmentProducts.ts`: `getInvestmentProductsPage` (filters + pagination) and a `closed` field on `InvestmentProduct`, with `getInvestmentProducts()` (the existing unfiltered callers) reimplemented to page through `status=ALL` internally.
- Responsive: the three-chart row is a CSS grid, one column below `lg` and three across at `lg`+ (matching `Layout`'s own nav breakpoint, not a `sm`/`md` split) so two 300+px charts never have to fight for space on a tablet; Playwright geometry checks extended for the new layout (F020).

## Dependencies
F001, F003, F008, F009, F015, F019 (TanStack Query), F020 (Playwright), F021 (responsive primitives), F022 (holdings — this feature's account allocation and product list depend on it).
