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
- `AllocationService` (F009): add `groupBy=ACCOUNT`. Rows: `{accountId, accountName, totalValue, needsSnapshot}` — sum of the latest snapshots of holdings in that account; `needsSnapshot` true if any holding there is stale. An account with no holdings, or all-zero/absent snapshots and nothing stale, is omitted — same rule as the existing groupings.
- New paginated/filtered product list query (extends `InvestmentProductService.findAll`): `PagedModel` (matching Transactions' pattern), filtered by `categoryId`, `subcategoryId`, `accountId` (has a holding there), `name` (contains, case-insensitive), `status` (`OPEN`/`CLOSED`/`ALL`, default `OPEN`, derived from the product's holdings).

### API
- `GET /api/investments/allocation?groupBy=CATEGORY|SUBCATEGORY|ACCOUNT` (extends F009's endpoint).
- `GET /api/investment-products?page=&size=&categoryId=&subcategoryId=&accountId=&name=&status=` returns `PagedModel<InvestmentProductResponse>` (F022's plain list becomes paginated — a breaking shape change, fine pre-1.0/local app).
- Regenerate `frontend/src/api/generated/schema.ts`.

## Frontend
- `/investments` (`InvestmentsPage`): three-chart row (`InvestmentAllocationChart` for Category, unchanged; new `InvestmentSubcategoryAllocationChart`, flat, no drill-down; new `InvestmentAccountAllocationChart`) above `Tabs`: "Accounts" (today's table, unchanged) and "Products" (new).
- New products list (a section or page under "Products"): `ResponsiveTable` (columns: name, category, sub-category, status; `tabletPriority` per the F021 pattern), `ResponsiveFilterBar` (category/sub-category/account selects, name text field, status select), `PaginationControls`, page size 20 — mirrors `TransactionsPage`'s structure. Row click navigates to the product detail page (F022).
- `src/api/investmentAllocation.ts`: add the `ACCOUNT` grouping; `investmentProducts.ts`: paginated list call with filters.
- Responsive: the three-chart row stacks per F021's existing breakpoint conventions on tablet/phone; Playwright geometry checks extended for the new layout (F020).

## Dependencies
F001, F003, F008, F009, F015, F019 (TanStack Query), F020 (Playwright), F021 (responsive primitives), F022 (holdings — this feature's account allocation and product list depend on it).
