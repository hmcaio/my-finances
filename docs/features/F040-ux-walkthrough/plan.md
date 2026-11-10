# F040 — Action Plan

**Depends on**: F037, F025, F009, F022, F021, F032, F039 (all built). No ADR; the PRD bullets are already in `docs/PRD.md` from the docs PR that added this folder.

Each phase below is one PR on its own branch off `main` (`feature/f040-<slug>`, ADR 0032), with its CHANGELOG bullet in the same commit; phases 1 to 3 and 6 are independent and can ship in any order, phase 5 needs 2 and 3, phase 8 needs 7. The order below puts the cheap fixes first, then the shared mechanisms (toast, dialog shell), then the two big flows. Backend before frontend inside a flow ("the batch endpoint's body shape must exist before the sheet has anything to call").

## Phase 1 — Quick fixes (frontend)
- [x] Replace "(or the Transfers page)" in `i18n/locales/en/investments.json` and the `pt-BR` twin with the Ledger wording; update the test that asserts the old text, if any.
- [x] Restyle the Settings `ListSubheader` in `components/layout/Layout.tsx` as a section heading (light and dark).
- [x] Test first: a jsdom test that the skip link is the first tab stop and moves focus to `#main-content`. Implement the skip link in `Layout.tsx` (`common:nav.skipToContent`, English and Brazilian Portuguese); give the `main` region `id` and `tabIndex={-1}`.
- [x] `components/layout/PageHeader.tsx`: `flexWrap: 'wrap'` by default; drop the redundant `sx` in `features/fuel/FuelPage.tsx`; a Playwright geometry check (new `e2e/fii.spec.ts`; no FII spec existed to extend) that the FII header at 360px does not squeeze the title.
- [x] `scripts/verify.sh frontend`.

## Phase 2 — Success toast (frontend)
- [x] Test first: the notification store (publish, subscribe, reset between tests in `src/test/setup.ts`) and `NotificationHost` (renders the message with `role="status"`, hides after the timeout, a second message replaces the first).
- [x] Implement `providers/notifications/notificationStore.ts` and `NotificationHost.tsx`; render the host in `components/layout/Layout.tsx`.
- [x] Test first: `createQueryClient` publishes `i18n.t(meta.successMessage, options)` on success, still publishes when `skipInvalidate` is set, publishes nothing on error and nothing without the meta. Implement the two `mutationMeta` keys and the `onSuccess` branch in `api/core/queryClient.ts`.
- [x] Add `meta.successMessage` (English and Brazilian Portuguese keys) to every create, update and delete mutation in `api/**/*Queries.ts`; allowlist login, logout and other non-write mutations. Includes the five goals mutations in `api/goals/goalsQueries.ts` (create, edit, show on dashboard, delete, replace allocations), with their keys in both `goals.json` files.
- [x] `src/test/contracts/mutationMessages.test.ts`: fails for a mutation hook with neither a message nor an allowlist entry.
- [ ] `docs/conventions/frontend-data-loading.md` and `frontend-components.md`: the toast rule.
- [ ] `scripts/verify.sh frontend`.

## Phase 3 — Form dialog shell (frontend)
- [ ] Test first for `components/feedback/FormDialog.tsx`: Enter in a text field submits when valid; Enter does nothing while `canSubmit` is false or `busy`; required fields render the asterisk and the hint; the submit button is `type="submit"`; `noValidate` suppresses the native bubble; Esc and Cancel close.
- [ ] Implement `FormDialog.tsx`; rebuild `AddFormDialog.tsx` on it; mark required fields and `autoFocus` the first field on the five pages that use it (Institutions, Categories, Vehicles, Budgets add, Recurring add), dropping their hand-coded Enter handlers where the shell now covers them.
- [ ] Move `features/investments/products/components/SnapshotDialogs.tsx` (Record snapshot) onto `FormDialog`, with Date and Balance marked required and Balance focused first.
- [ ] `docs/conventions/frontend-components.md` and `frontend-responsive.md`: the shell and the autofocus and required rules.
- [ ] `scripts/verify.sh frontend`.

## Phase 4 — Same-day Ledger order (backend)
- [ ] Test first (logic tier, fakes): `LedgerQuery` orders three same-day entries (a transaction, a transfer, a transaction) by `createdAt` newest first across both types; an entry with a null `createdAt` sorts last; the date still dominates.
- [ ] Test first (persistence tier): the transaction and transfer adapters expose `createdAt` on the domain object and page by `date DESC, createdAt DESC, id DESC`.
- [ ] Implement `getCreatedAt()` on `Transaction` and `Transfer` (mapped in the adapters, excluded from `toAuditSnapshot()` and its guard test), `LedgerEntry.createdAt()`, the `CAP_PLUS_ONE` sort and `NEWEST_FIRST` in `application/ledger/LedgerQuery.java`.
- [ ] `./gradlew spotlessApply` then `scripts/verify.sh backend` (Docker running).

## Phase 5 — New entry dialog (frontend)
- [ ] Test first for `features/ledger/hooks/useEntryDraft.ts`: switching Expense to Transfer carries date and account to From; Transfer to Income carries From to account; Expense to Income clears a mismatched category; "Add and new" carry rules for a plain transaction, a fuel transaction (category, vehicle, fuel type kept; liters, price, km, odometer cleared) and a plain transfer; edit mode locks the type.
- [ ] Test first for `features/ledger/components/EntryDialog.tsx`: transaction, then transfer, then transaction in one open dialog with three saves; categories filtered by type; "Add and new" hidden for trade confirmations and in edit mode; focus lands on the first cleared field; Enter submits "Add" and closes.
- [ ] Implement `EntryDialog.tsx`, `useEntryDraft.ts`, the `type` filter in `features/transactions/TransactionFormFields.tsx`, `onAddAnother` and the carry seed in `features/transfers/components/TransferForm.tsx`; wire `features/ledger/LedgerPage.tsx` (menu entries preselect a type, the Fuel page preset opens Expense) and remove the replaced create paths of `TransactionFormDialog.tsx` and the inline transfer dialog; required marks on Date, Amount, Category, Account, Payment Method, Description and the transfer equivalents.
- [ ] Update the MSW handlers and the page tests that open the old dialogs.
- [ ] `scripts/verify.sh frontend`.

## Phase 6 — Investments tab in the URL (frontend)
- [ ] Test first: `features/investments/overview/lib/investmentsSearchParams.ts` (default omitted, `products` kept, an unknown value falls back to `accounts`) and `InvestmentsPage` (a reload with `?tab=products` opens Products; clicking a tab writes the URL with `replace`).
- [ ] Implement the parse/serialize helpers and wire `InvestmentsPage.tsx`.
- [ ] `scripts/verify.sh frontend`.

## Phase 7 — Snapshot sheet (backend)
- [ ] Test first (logic tier, fakes): `InvestmentSnapshotService.recordBatch` creates new snapshots and replaces same-day ones in one call and reports `created` and `replaced`; a batch with an unknown holding writes nothing and reports every offending entry index; a failure after the first row leaves no snapshot and no audit entry; each recorded row writes its own `INVESTMENT_SNAPSHOT` audit entry and an unchanged same-day balance writes none.
- [ ] Test first (logic tier): `OpenHoldingSnapshotSheetQuery` returns only open holdings with their latest snapshot (or none), ordered by account name then product name, case-insensitive.
- [ ] Implement `recordBatch`, `InvestmentSnapshotSheetHoldingsNotFoundException` (`@ResponseStatus(NOT_FOUND)`), `InvestmentHoldingRepository.findAllByIds` and its adapter, and `OpenHoldingSnapshotSheetQuery`.
- [ ] Controller tests for `GET` and `POST /api/investment-snapshot-sheet`: `200` with counts, `400` for a negative balance, more than two decimals, an empty list, more than 200 entries and a duplicate holding (each with the indexed `errors[].field`), `404` with `errors[]` naming every missing holding, `409` for a concurrent same-day write; the new `404` body shape in `GlobalExceptionHandlerTest`. Check the 409 case against the unverified commit-time behavior in the spec.
- [ ] `infrastructure/web/investmentsnapshot/InvestmentSnapshotSheetController.java`, request and response DTOs, `@ApiResponse` annotations; regenerate `frontend/src/api/generated/schema.ts`; the ArchUnit rule (every write use case depends on the audit port) stays green.
- [ ] `docs/conventions/errors.md`: the 404 that carries an `errors` member.
- [ ] `./gradlew spotlessApply` then `scripts/verify.sh backend` (Docker running).

## Phase 8 — Snapshot sheet (frontend)
- [ ] Test first for `features/investments/snapshots/lib/snapshotSheet.ts`: blank rows are skipped; a negative or over-precise balance is flagged; the difference is signed; the request carries only filled rows; `errors[]` indexes map back to the right rows when blank rows sit between them.
- [ ] Test first for the page: renders one row per open holding grouped by account then product; Enter moves down the rows and from the last row to Save; Save is disabled with no valid row; a `200` shows one plural toast, clears the inputs and updates the Previous column; a `404` with `errors[]` highlights exactly those rows and keeps every input; a `409` keeps the inputs and offers to retry; the empty state links to Products.
- [ ] Implement `api/investments/products/investmentSnapshotSheet.ts` and `investmentSnapshotSheetQueries.ts` (with `successMessage` and `successMessageOptions`), the MSW handlers, the `apiError.ts` maps with their strings in both `errors.json` files, `InvestmentSnapshotSheetPage.tsx`, `components/SnapshotSheetTable.tsx`, `hooks/useSnapshotSheet.ts`, the route in `routes/lazyPages.ts` and `routes/AuthenticatedRoutes.tsx`, and the "Record snapshots" button in `InvestmentsPage.tsx` (English and Brazilian Portuguese strings).
- [ ] Playwright geometry check: the sheet renders as cards at 360px with a reachable balance input per card.
- [ ] `scripts/verify.sh frontend`.

## Phase 9 — Dashboard and FII layout (frontend)
- [ ] Dashboard: equal-height cards, `BudgetVsActualReport` in a `Paper`, children reordered to spend, budget, balances, allocation, net worth, goals, pending; Net worth spanning two columns at `lg`; `GoalsWidget` keeping its null render with its full-width `gridColumn` on its own root element (no wrapper, so no empty row without a flagged goal); `NetWorthTrendChart` filling its card through `useElementWidth` and `ChartSvg`'s `fillWidth` (`features/dashboard/DashboardPage.tsx`, `GoalsWidget.tsx`, `NetWorthTrendChart`); update the layout test and the Playwright geometry check for the three bands, with and without a flagged goal.
- [ ] Test first for `features/investments/fii/FiiAllocationComparison.tsx` (segments with ticker rows, Actual %, Planned %, difference, color dots matching the donuts, empty and loading states). Implement it and the `compact` prop of `FiiNestedAllocationDonutChart.tsx`; wire `FiiPage.tsx` so only phones (`useIsMobile`) get the two-column donuts plus the table; desktop unchanged.
- [ ] `docs/conventions/frontend-charts.md` if the net-worth chart sizing rule changes.
- [ ] `scripts/verify.sh frontend`.

## Phase 10 — Docs
- [ ] `docs/features/README.md`: the F040 row verified against what was built, and the feature removed from its "Status" list.
- [ ] Re-read `docs/PRD.md` §6.6, §6.8, §6.12, §6.13, §6.16 and §9 against the shipped behavior; fix any bullet that drifted.
- [ ] Root/frontend `CLAUDE.md`: only if a rule was added that the code cannot show and no check enforces (the toast convention is enforced by its contract test, so likely none).

## Verification
- [ ] Open the Ledger "Add" menu, choose "New transaction", fill Date 1 day back, an expense and press "Add and new": the dialog stays open, a toast appears, date, account and payment method are kept, amount, description and category are cleared, focus is in Amount.
- [ ] In the same dialog switch to Transfer, enter a transfer, switch to Income, enter one, then close: all three appear in the Ledger, newest-created first within the same day.
- [ ] Open "New transaction" and leave Payment Method empty: it has an asterisk, the hint is visible and "Add" is disabled; fill everything and press Enter in Description: it saves and closes.
- [ ] Save a fuel fill-up with "Add and new": category, vehicle and fuel type stay, liters, price, km and odometer clear.
- [ ] Open `/investments?tab=products`, reload: the Products tab is selected; switch tabs: the URL updates without adding history entries.
- [ ] Open "Record snapshots" from Investments, fill three of the four rows, press Save: one toast ("3 snapshots saved"), the Previous column updates, the blank row was not recorded; open Activity: three `Investment snapshot` entries.
- [ ] Fill a row, delete that product in another tab, press Save: that row is highlighted with its reason, nothing else is lost, no snapshot of the others was saved; remove the row and Save again: it succeeds.
- [ ] Enter a negative balance: the row is flagged before anything is sent.
- [ ] At 390px: the FII page shows both donuts side by side with the comparison table below, "Register dividend" no longer squeezes the title, and the snapshot sheet rows are cards.
- [ ] At 1366px the Dashboard has three equal cards in row 1, Investment allocation beside a wider Net worth chart in row 2 (the chart fills its card), Upcoming bills below, with no empty cell; flag a goal for the dashboard: its card appears on its own full-width row between Net worth and Upcoming bills and no hole opens; unflag it: no blank row remains; the first Tab stop is "Skip to content".
- [ ] Record a snapshot through the sheet for a product allocated to a goal: the goal's value on its page and on the dashboard updates.
- [ ] `scripts/verify.sh backend frontend` (Docker running) all green.
