# F023 — Action Plan

**Depends on**: F001, F003, F008, F009, F015, F019, F020, F021, F022.

## Backend
- [ ] Write tests for the allocation `groupBy=ACCOUNT` grouping (sum by account, stale flag, omission rule), then implement.
- [ ] Write tests for the paginated/filtered product list query (each filter alone and combined, default status `OPEN`, derived status correctness), then implement; wire the controller to `PagedModel`.
- [ ] Controller tests for the extended allocation endpoint and the new list query params; regenerate `frontend/src/api/generated/schema.ts`.

## Frontend
- [ ] `InvestmentSubcategoryAllocationChart` (flat, %-of-total, no drill-down) and `InvestmentAccountAllocationChart`; lay out the three-chart row on `InvestmentsPage`.
- [ ] Add the "Accounts"/"Products" tabs; move today's accounts table under "Accounts" unchanged.
- [ ] New products list under "Products": `ResponsiveTable` + `ResponsiveFilterBar` + `PaginationControls`, filters wired to the new query params, row click → product detail page.
- [ ] `src/api/investmentAllocation.ts`/`investmentProducts.ts` updates + MSW handlers.
- [ ] Playwright geometry checks for the new three-chart row and tabs at mobile/tablet/desktop widths.

## Verification
- [ ] Confirm the category chart's drill-down still works unchanged; the sub-category chart shows each sub-category's true share of the whole portfolio (cross-checked against the category chart's totals); the account chart sums to the same portfolio total.
- [ ] Filter the products list by each filter alone and in combination; confirm pagination and the default `Open` status filter.
- [ ] Confirm a product held in two accounts (from F022's test data) appears once in the list and once per account in the account-filtered allocation view.
