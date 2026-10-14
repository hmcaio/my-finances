# F033 — Action Plan

**Depends on**: F001.

One feature branch (`feature/f033-i18n-pt-br-en`), one commit per phase (per area in Phase 3). Old `.toFixed()`/hardcoded-English call sites are replaced area by area so the app stays fully functional (and fully translated for whatever's already migrated) after every commit.

## Phase 0 — Decision record
- [ ] `docs/adr/0027-frontend-i18n-react-i18next.md` and its row in `docs/adr/README.md`.
- [ ] `docs/features/F033-i18n-pt-br-en/{spec,plan}.md` and the `docs/features/README.md` row.
- [ ] `docs/PRD.md`: three new §3 non-goals, new §6.14, two new §9 future directions.

## Phase 1 — Infrastructure
- [x] Add `react-i18next` + `i18next`.
- [x] `src/i18n/i18n.ts`: `i18next.init()` with static-imported `en`/`pt-BR` resources, `common` + `errors` namespaces first (others added as each area migrates in Phase 3); default/fallback language `pt-BR`.
- [x] `src/i18n/i18next.d.ts`: module augmentation typing `t()` against the `en` resources.
- [x] Test first: `src/i18n/localeParity.test.ts` (fails until every namespace exists with matching keys — starts passing trivially with just `common`/`errors`, grows as later phases add namespaces).
- [x] `src/hooks/locale/{LocaleProvider.tsx,useLocale.ts}` mirroring `ColorModeProvider`/`useColorMode`: `localStorage` key `my-finances:locale`, `navigator.language`-based initial detection (`pt*` → `pt-BR`, else `en`, default `pt-BR`), calls `i18next.changeLanguage` on change. Test first (`useLocale.test.tsx`, mirroring `useColorMode.test.tsx`).
- [x] Wrap `<LocaleProvider>` in `App.tsx` alongside `<ColorModeProvider>`.
- [x] `Layout.tsx`: second header `IconButton` next to the dark/light toggle, calling `useLocale().toggleLocale()`; icon/`aria-label` reflect current language. Update `Layout.test.tsx`.
- [x] `src/i18n/format.ts` (`formatMoney`, `formatNumber`, `formatDate`) with its own test file covering both locales at 2/3/1-decimal precision and date formatting.

## Phase 2 — Error messages
- [x] `src/api/core/apiError.ts`: convert `PROBLEM_TYPE_MESSAGES`/`VALIDATION_CODE_MESSAGES` to translation-key maps under the new `errors` namespace; `toApiError`/`summaryFor` resolve via the `i18next` singleton's `t()`. Existing `apiError.test.ts` assertions updated to the (still-English-by-default) resolved text; add a test proving the same `type`/`code` resolves differently when `i18next.changeLanguage('pt-BR')` is active.
- [x] `npm run lint && npm test`.

## Phase 3 — Seed-name translation + per-area string/formatting migration
Each area: add its `common`/`<area>` namespace entries (`en` + `pt-BR`), swap hardcoded JSX strings for `t('<namespace>.key')`, swap `.toFixed()`/hardcoded-`'en-US'` calls for `src/i18n/format.ts`, update that area's existing tests for any amount fixture ≥ 1000 (new thousands separator), then commit.
- [x] `src/i18n/seedNames.ts` + categories/payment methods (`CategoriesPage`, `PaymentMethodsPage`): build the seed-name lookups from `V2`'s literal strings, wire `translateSeedName` into both pages' list rendering and anywhere else a category/payment-method name renders (transaction/transfer/budget/recurring-template lists, dashboard widgets).
- [x] Institutions (`InstitutionsPage`, `InstitutionSelect`): seed-name lookup from `V12`'s `'No institution'`.
- [x] Accounts (`AccountsPage`, `AccountDetailPage`): strings + formatting.
- [x] Investment taxonomy (`InvestmentCategoriesPage` or equivalent) + investment products (`InvestmentProductDetailPage`, `InvestmentProductsSection`, `ValueSeriesChart`): seed-name lookups from `V13`'s categories/sub-categories, plus strings/formatting.
- [x] Investments dashboard (`InvestmentsPage`, `InvestmentAllocationChart`, `FlatAllocationDonutChart`).
- [x] Budgets (`BudgetsPage`, `BudgetVsActualReport`).
- [x] Transactions (`TransactionsPage`, `AccountTransactionList`) and Transfers (`TransfersPage`, `TransferForm`, `AccountTransferList`).
- [x] Recurring templates (`RecurringTemplatesPage`, `PendingOccurrencesWidget`).
- [x] Fuel (`FuelPage`, `FuelTimeSeriesChart`) — fuel-category seed name from `V18`.
- [ ] FII (`FiiPage`, `AllocationPlanEditor`, `DividendHistorySection`, `FiiNestedAllocationDonutChart`) — dividend-category seed name from `V20`.
- [ ] Net worth (`NetWorthTrendChart`) and dashboard widgets (`AccountBalancesWidget`, `SpendByCategoryWidget`).
- [ ] Activity log (`groupByDay.ts`, `ActivityPage`) — replace the hardcoded `'en-US'` with the active locale.
- [ ] Shared chrome: `Layout` nav labels, `common` buttons (`ConfirmDialog`, `InlineEditActions`, `PaginationControls`, `ResponsiveFilterBar`), `BackupStatusBanner`, onboarding.
- [ ] After each area: `npm run lint && npm test`.

## Phase 4 — Cleanup & verification
- [ ] Grep for any remaining `.toFixed(` or a literal hardcoded English string in `src/features`/`src/components` outside test files.
- [ ] `src/i18n/localeParity.test.ts` passes for every namespace now in use.
- [ ] `npm run lint && npm test && npm run build`.

## Phase 5 — Docs
- [ ] `frontend/CLAUDE.md`: new "Internationalization" section (see spec's Docs list).
- [ ] Root `README.md` "Project status" entry.
- [ ] `CHANGELOG.md` `[Unreleased]` entry (`**F033 — Internationalization**`); the PR link is added in a follow-up commit once the PR is open.
- [ ] Tick this plan.

## Verification
- [ ] `npm run lint && npm test && npm run build` green.
- [ ] Manual, dev: toggle the header language control — every page's labels, buttons, nav, and currently-visible numbers/dates switch immediately with no reload; toggling back restores English.
- [ ] Manual: a fresh/seeded install in `pt-BR` shows translated starter category/payment-method/institution/investment-taxonomy names; renaming one of them (e.g. "Groceries" → "Mercado") shows the renamed text unchanged after toggling language either way.
- [ ] Manual: trigger a 409 (duplicate name) and a validation 400 in each locale — the error banner text is in the active language.
- [ ] Manual: reload the browser after toggling to `pt-BR` — the choice persists (localStorage); clearing storage and reloading falls back to the browser-language guess.
- [ ] Manual: amounts and dates in `pt-BR` use `1.234,56` / `16 de março de 2026` formatting with no currency symbol; `en` uses `1,234.56` / `March 16, 2026`.
