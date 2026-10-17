# F037 — Action Plan

**Depends on**: F004, F005, F027, F003, F019, F021, F031, F033, F036. See [ADR 0030](../../adr/0030-postgres-unaccent-for-accent-insensitive-search.md).

Branch `feature/f037-ledger`. Backend first: the `GET /api/ledger` shape (and the regenerated `schema.ts`) must exist before the frontend has anything to consume. Search is its own backend phase and its own frontend phase after the ledger proper, so it can ship as a separate commit or PR if the ledger is ready first.

## Phase 0 — Decision record
ADR 0030 and the PRD changes are already in the docs PR that introduced this plan; the checks below confirm they still match what is built.
- [ ] `docs/adr/0030-postgres-unaccent-for-accent-insensitive-search.md` and its row in `docs/adr/README.md` match the implementation (extension, query shape, no index, fake behaviour).
- [ ] `docs/PRD.md` §6.16, §6.1, §6.2 and the §9 line match the shipped behaviour.

## Phase 1 — Backend: ledger list and summary
- [ ] Test first: `TransactionFilter`/`TransferFilter` compatibility constructors keep every existing caller compiling and `none()` unchanged (compile-level, plus a fake-repository test that `type` filters `INCOME`/`EXPENSE`).
- [ ] Implement the new `type` and `searchText` components on `domain/transaction/TransactionFilter.java`, `searchText` on `domain/transfer/TransferFilter.java`, and `MAX_SEARCH_TEXT_LENGTH` in `domain/shared/TextFieldConstraints.java`.
- [ ] Test first (`FakeTransactionRepository` / `FakeTransferRepository`): `sumAmountByType` totals per type and omits an absent type; type filter; unpaged still works.
- [ ] Implement `TransactionRepository.sumAmountByType` on the port, fakes and `TransactionRepositoryAdapter` (grouped criteria query reusing `toSpecification`), plus the `type` predicate; repository integration test against Postgres for the sum and the filter.
- [ ] Test first (`LedgerQuery` with fakes): interleaving by date desc, then transactions before transfers, then id; `type=TRANSFER` reads no transactions; `type=INCOME|EXPENSE` reads no transfers; a set `categoryId` or `paymentMethodId` excludes transfers; `accountId` matches either transfer side; summary excludes transfers and `net = income - expense`; cap: 1,001 combined rows yield 1,000 entries and `truncated = true`, exactly 1,000 yield `truncated = false`; summary stays correct when truncated; blank `searchText` becomes `null`.
- [ ] Implement `application/ledger/` (`LedgerEntryType`, `LedgerFilter`, `LedgerEntry`, `LedgerSummary`, `Ledger`, `LedgerQuery`).
- [ ] REST: `infrastructure/web/ledger/LedgerController.java`, `LedgerResponse`, `LedgerEntryResponse`, `LedgerSummaryResponse`; controller tests for `200` (each row type, trade lines), `400` for a bad date/enum/UUID, unauthenticated `401`; regenerate `frontend/src/api/generated/schema.ts`.
- [ ] `./gradlew spotlessApply` then `./gradlew spotlessCheck test integrationTest` (Docker running).

## Phase 2 — Backend: accent-insensitive text search
- [ ] Flyway migration `V25__enable_unaccent.sql` plus its migration test (`unaccent('Café açaí')` returns `Cafe acai`); confirm the extension creates inside the migration transaction on the pinned image.
- [ ] Test first (`TextSearchPredicates`): `likePattern` escapes `\`, `%` and `_` and wraps in `%…%`; a search for a literal `%` does not match everything.
- [ ] Implement `infrastructure/persistence/shared/TextSearchPredicates.java` and the search clauses in `TransactionRepositoryAdapter` and `TransferRepositoryAdapter`.
- [ ] Test first (repository integration, real Postgres): "cafe" finds "Café da manhã", "CAFÉ" matches "cafe", matches `additionalNotes` as well as `description`, a null notes column never errors, transfers match too, search combines with date range, account and type filters, and the summary respects it.
- [ ] Test first (fakes): `Normalizer`-based folding mirrors the integration cases that do not involve non-decomposable letters.
- [ ] Controller: `q` parameter with `@Size(max = 100)` and no `@Validated`; controller test for `101` characters → `400` with the problem type; blank `q` ignored.
- [ ] `./gradlew spotlessApply` then `./gradlew spotlessCheck test integrationTest` (Docker running).

## Phase 3 — Frontend: Ledger page
- [ ] Test first: `ledgerSearchParams` parse/serialize (month default and invalid values dropped; range mode with empty bounds is all time; `type` mapping; `focus`), run under a pinned non-UTC zone; `shiftMonth`/`monthRange` across year boundaries and leap February.
- [ ] Implement `src/features/ledger/ledgerSearchParams.ts`, `src/utils/localDate.ts` additions, `src/hooks/useDebouncedValue.ts` (with test).
- [ ] Test first: `MonthNavigator` (prev/next change the value, picker change emits, future months allowed). Implement `src/components/inputs/MonthNavigator.tsx`.
- [ ] Implement `src/api/ledger/ledger.ts`, `ledgerQueries.ts`, `useTransaction` in `transactionsQueries.ts`, the `PROBLEM_TYPE_MESSAGES` entry, and `src/mocks/handlers/ledger.ts` registered in `src/mocks/handlers.ts` (filters, accent-insensitive `q`, cap, summary).
- [ ] Test first: `LedgerTable` per row type (income `+` green up-arrow, expense `-` red down-arrow, transfer unsigned with swap icon, `From → To`, `—` cells, Buy/Sell chips capped at 3), accessible label on the type icon, mobile card layout.
- [ ] Implement `ledgerEntryStyle.ts`, `LedgerTable.tsx`, `LedgerSummary.tsx`.
- [ ] Test first: `LedgerFilters` (Transfer clears and disables category and payment method; closed accounts selectable; scope toggle) and `LedgerPage` (month navigation updates the URL and request; summary shown; truncation notice; empty states; split Add menu opens each dialog; edit fetches by id and opens the right dialog; delete confirms and calls the right mutation; `?focus=` opens the transfer and removes only `focus`; `presetCategoryId` state opens the preset dialog).
- [ ] Implement `LedgerFilters.tsx`, `LedgerPage.tsx`, extract `src/features/transactions/TransactionFormDialog.tsx` from the old page, and the `ledger` i18n namespace in en and pt-BR (register it; parity test green).
- [ ] Route and callers: `src/App.tsx` (`/ledger`, redirects), `LegacyRedirects.tsx` (test: query string kept, `/transfers?focus=ID` → `/ledger?type=transfer&focus=ID`), `Layout.tsx` nav item and `common.json` keys, `FuelPage`, `InvestmentProductDetailPage`.
- [ ] Delete `TransactionsPage`, `TransfersPage` and their tests; delete `getTransactions`/`useTransactions`/`getTransfers`/`useTransfers` only if no caller remains.
- [ ] `npm run lint && npm test && npm run build && npm run format:check`.

## Phase 4 — Frontend: text search and account embed
- [ ] Test first: search field debounces (~300 ms), writes `q` with `replace`, honors `maxLength` 100, clear button; empty result with `q` outside all-time shows "Search all time", which switches to range mode with empty bounds.
- [ ] Implement the search field in `LedgerFilters.tsx` and the empty-state action in `LedgerPage.tsx`.
- [ ] Test first: `AccountLedger` (scoped request carries `accountId`, month navigation in local state, summary, read-only, "View in Ledger" link target); `AccountDetailPage` shows one section for a normal account and products-plus-ledger for an `INVESTMENT` account.
- [ ] Implement `AccountLedger.tsx`, update `AccountDetailPage.tsx` and its test and skeleton, delete `AccountTransactionList` and `AccountTransferList` with their tests, rename the `accounts:detail.*` keys.
- [ ] Playwright: `e2e/ledger.spec.ts` replacing `transactions.spec.ts` and `transfers.spec.ts` (no horizontal overflow at 360px; filter bar and summary fit on all three projects); update `accounts.spec.ts` and `login.spec.ts` references.
- [ ] `npm run lint && npm test && npm run build && npm run format:check`.

## Phase 5 — Docs
- [ ] `docs/conventions/frontend-components.md` (`MonthNavigator`, `LedgerTable`), `frontend-structure.md` (`inputs/`), `errors.md` (parameter-level `@Size` and its problem type), `data-constraints.md` (`MAX_SEARCH_TEXT_LENGTH`).
- [ ] One-line "list UI superseded by F037" note at the top of `docs/features/F004-transactions/spec.md` and `F005-transfers/spec.md`.
- [ ] `CHANGELOG.md` `## [Unreleased]`: `**F037 — Ledger**` bullet with an `Upgrade:` sub-line for the `unaccent` extension; add the PR link in a follow-up commit once the PR exists.
- [ ] `docs/features/README.md` row verified against what was built; root `README.md` "Project status" moves F037 to "Built".

## Verification
- [ ] Open the Ledger: the current month shows income, expense and transfer rows interleaved newest first; income is green with `+`, expense red with `-`, transfers neutral and unsigned with `From → To`.
- [ ] Step to the previous and next month with the arrows and pick a month from the picker: the list, summary and URL (`?month=`) follow; reload keeps the month; a future month is allowed.
- [ ] Filter by type Transfer: only transfers, category and payment method disabled; filter by a category: transfers disappear and the summary shows only that category.
- [ ] Switch to a custom range and to all time: dates and URL follow; the summary covers the whole scope.
- [ ] Search "cafe" with a "Café" description present: it appears; search a literal `%`: no rows unless one contains it; search 101 characters is blocked by the input.
- [ ] Search for a term that exists only in another month: the empty state offers "Search all time" and clicking it finds the row.
- [ ] Seed more than 1,000 rows in a month (or lower the cap temporarily): the truncation notice shows and the summary still covers all rows.
- [ ] Add a transaction (fuel category included) and a transfer (a trade confirmation too) from the Add menu; edit and delete each from its row.
- [ ] Open `/transfers?focus=<id>`, `/transfers` and `/transactions`: each redirects to the Ledger with its query kept; the transfer opens in the edit dialog. From the Fuel page, "add fill-up" opens the preset dialog on the Ledger.
- [ ] Open an account page and an `INVESTMENT` account page: one "Transactions & transfers" section, read-only, month navigator and summary work; "View in Ledger" lands on the Ledger scoped to that account and month.
- [ ] At 360px width nothing scrolls horizontally on the Ledger or the account page.
- [ ] Migrate a database that already has data to V25: the app starts, existing data and reports are unchanged, and a backup taken afterwards restores into a fresh stack and still searches with accent folding.
- [ ] `./gradlew spotlessCheck test integrationTest` (Docker running) and `npm run lint && npm test && npm run build && npm run format:check` all green.
