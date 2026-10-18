# F037 — Ledger

## Summary
Replaces the separate Transactions and Transfers pages with one **Ledger** page (`/ledger`): transactions and transfers interleaved in one list, newest first, scoped to a month with a `‹ month ›` navigator, with income / expense / net totals for the scope, a type filter, text search that ignores case and accents, and a clear visual distinction between income, expense and transfer rows. The account detail page's two embedded lists collapse into the same component, scoped to one account and read-only. Implements the new PRD §6.16 (with pointers in §6.1 and §6.2, deferrals in §9). The accent-insensitive search relies on the Postgres `unaccent` extension, the first extension the app depends on: see [ADR 0030](../../adr/0030-postgres-unaccent-for-accent-insensitive-search.md).

Product surface: one new read endpoint (`GET /api/ledger`), one migration that only enables an extension (no table or column change), and a frontend page/route/nav rename. Nothing is written by the ledger itself; create, edit and delete still go through the existing transaction and transfer endpoints.

## Scope
- Backend: `GET /api/ledger` and an application-layer `LedgerQuery` that merges both repositories; a `type` and a `searchText` dimension on `TransactionFilter`, a `searchText` dimension on `TransferFilter`; `TransactionRepository.sumAmountByType`; a shared accent-insensitive search predicate used by both adapters; migration `V25__enable_unaccent.sql`.
- Frontend: `/ledger` page (`LedgerPage`) with `MonthNavigator`, filters, summary strip, shared `LedgerTable`, split "Add" button reusing the existing transaction and transfer dialogs; `AccountLedger` on the account detail page; `/transactions` and `/transfers` redirects; nav item "Ledger" (pt-BR "Lançamentos"); en and pt-BR strings; MSW handler; Playwright spec.
- Docs: ADR 0030, PRD §6.16 / §6.1 / §6.2 / §9, conventions files, features README row, root README status.
- Out of scope: see [Non-goals](#non-goals).

## Decisions
See ADR 0030 for why search folds accents in SQL. Implementation-level decisions not in the ADR:

| Decision | Why |
|---|---|
| One new endpoint, `GET /api/ledger`, served by an application-layer `LedgerQuery` that calls `TransactionRepository.findAll` and `TransferRepository.findAll` and merges the two lists in memory. `GET /api/transactions` and `GET /api/transfers` are untouched. | The two lists page independently, so merging in the browser would break the row cap and the totals. A `UNION ALL` read model would bypass the hexagonal repositories and the existing `Specification`s; two bounded reads plus a sort is simple and, for one user's month, cheap. Same shape as F030's `TransactionsAndTransfersReportGenerator`, but with paging, a cap and a summary. |
| Each side is fetched with `PageRequest.of(0, MAX_ENTRIES + 1, date DESC, id DESC)`; the merged list is re-sorted and cut to `MAX_ENTRIES = 1000`; `truncated = mergedSize > MAX_ENTRIES`. | Fetching cap+1 from each side guarantees the top `cap` of the merge is present. No separate count query for the truncation flag (the `Page` count query that Spring Data issues is accepted). |
| Merge order: `date` descending, then transactions before transfers, then `id` descending compared the way Postgres orders `uuid`: bytewise, which equals comparing the lower-case canonical strings (confirmed against Postgres by an `id DESC` test in each repository adapter test), so the Java tie-break agrees with the repositories' `id DESC` page order. | A deterministic order is needed so a refetch never reshuffles rows that share a date; the repositories' own default sort has no secondary key. |
| The summary comes from new `TransactionRepository.sumAmountByType(TransactionFilter)` over the whole filtered scope, not from the returned entries. Transfers contribute nothing: `income`, `expense` and `net = income - expense` are income/expense transactions only. | Correct even when the list is truncated, and consistent with PRD §5.5 (a credit card payment is a transfer, not spend). |
| Filter semantics: `type=INCOME\|EXPENSE` restricts to transactions of that type; `type=TRANSFER` restricts to transfers; a set `categoryId` or `paymentMethodId` excludes transfers (they have neither), so `type=TRANSFER` plus a category is a valid request with an empty result, not a `400`. `accountId` matches a transaction's account or either side of a transfer. | The UI disables the contradictory combination, but the API stays total and predictable. Differs knowingly from the F030 report, where a category filter only narrows the transaction side and transfers stay in. |
| `q` is trimmed; a blank value means "no search". It is matched against `description` OR `additionalNotes` of both aggregates. `%`, `_` and `\` in the text are escaped in Java (`\` as the `LIKE` escape character) before the `%…%` pattern is built. | Matches the settled design: no category/account/product names, no trade tickers, no highlighting. `CriteriaBuilder.like(expr, pattern, escapeChar)` only declares the escape character, it does not escape the user's text (verified, see claim list). |
| `q` is bounded by `@Size(max = TextFieldConstraints.MAX_SEARCH_TEXT_LENGTH)` (100) **on the `@RequestParam`**, with no `@Validated` on the controller class, and `maxLength` on the frontend input. No domain or column limit exists because the text is never stored. | Root `CLAUDE.md` bounds free text at every layer that applies. In Spring 7 a constraint directly on a controller parameter triggers built-in method validation (`HandlerMethodValidationException`); a class-level `@Validated` would switch to an AOP proxy and surface a `ConstraintViolationException`, which `GlobalExceptionHandler` would turn into a `500`. |
| Rows keep today's per-row edit and delete icon buttons (and the mobile card actions) instead of whole-row click targets. Edit loads the full record by id (`useTransaction(id)` / `useTransfer(id)`) and opens the matching existing dialog. | A ledger entry carries only the list columns; the edit forms need the full aggregate (fuel details, trade confirmation, taxes). Fetch-by-id is also what the `?focus=` deep link already does, so one mechanism serves both. |
| View state lives in the URL query string: `month=YYYY-MM`, or `scope=range` with optional `from`/`to` (ISO dates, either may be empty: both empty is "all time"), plus `type`, `account`, `category`, `paymentMethod`, `q`, `focus`. Invalid values are dropped silently. `month` is used only when `scope=range` is absent. | Links and refresh restore the view (settled design); one parser/serializer module is unit-tested instead of ad hoc `URLSearchParams` reads. |
| `MonthNavigator` is a thin wrapper: a previous-month `IconButton`, the existing `MonthPicker` (native `type="month"`, no clamp, so future months are allowed), a next-month `IconButton`. | Reuses the shared month field instead of a custom popover; the picker already displays "October 2026" in the browser locale and opens the native month chooser on click. |
| The search box is debounced about 300 ms into the URL (`replace`, not `push`) so typing does not fill the browser history. | One request per pause, back button unaffected. |
| `AccountLedger` keeps its month in local component state (not the URL) and exposes a "View in Ledger" link to `/ledger?account=<id>&month=<month>`. | The account page's own URL has no query contract today, and the embed is read-only. |
| The old paged `GET /api/transactions` / `GET /api/transfers` endpoints stay. The frontend `getTransactions`/`useTransactions`/`getTransfers`/`useTransfers` are deleted only if no caller remains once the old pages are gone. | The endpoints are public API with their own tests and are still the right shape for report code; removing them is a follow-up, not part of a UI merge. |

## Backend

### Domain
- `domain/shared/TextFieldConstraints.java`: add `MAX_SEARCH_TEXT_LENGTH = 100`, with a short note that it bounds a query parameter, not a stored field.
- `domain/transaction/TransactionFilter.java`: add two nullable components at the end, `CategoryType type` and `String searchText`; keep a five-argument constructor with the old shape (delegating `null, null`) and `none()` so existing callers compile. `searchText` is raw text; the adapter escapes it.
- `domain/transfer/TransferFilter.java`: add one nullable component, `String searchText`, with the same compatibility constructor.
- `domain/transaction/TransactionRepository.java`: add `Map<CategoryType, BigDecimal> sumAmountByType(TransactionFilter filter)`; a type with no matching rows is absent from the map (callers default to `BigDecimal.ZERO`).
- No change to `Transaction` or `Transfer`.

### Application
New package `application/ledger/`:
- `LedgerEntryType` — enum `INCOME`, `EXPENSE`, `TRANSFER` (the API `type` filter and each entry's `type`).
- `LedgerFilter` — record `(LocalDate dateFrom, LocalDate dateTo, LedgerEntryType type, UUID accountId, UUID categoryId, UUID paymentMethodId, String searchText)`, all nullable; `searchText` is trimmed and blank becomes `null` in the compact constructor.
- `LedgerEntry` — sealed interface with `TransactionEntry(Transaction)` and `TransferEntry(Transfer)` records, exposing `id()`, `date()`, `type()` (a transaction's `INCOME`/`EXPENSE` from `Transaction.getType()`, or `TRANSFER`).
- `LedgerSummary(BigDecimal income, BigDecimal expense, BigDecimal net)` and `Ledger(List<LedgerEntry> entries, LedgerSummary summary, boolean truncated)`.
- `LedgerQuery` — `@Service`, read-only, `@Transactional(readOnly = true)` so the two reads and the sums see one snapshot. `Ledger find(LedgerFilter filter)`:
  1. Transactions are read unless `type == TRANSFER`; transfers are read unless `type` is `INCOME` or `EXPENSE`, or `categoryId` or `paymentMethodId` is set.
  2. Each side is read with the cap+1 page request from Decisions, mapped to `TransactionFilter` (`type` as `CategoryType`) / `TransferFilter`.
  3. Merge, sort, truncate, compute `truncated`.
  4. Summary from `sumAmountByType` when transactions are read; all zeros otherwise.
  - Failure modes: none beyond `400` from request binding; no audit call (read-only, ADR 0022); no exceptions of its own.
- Depends on two aggregates' domain packages, which is allowed in `application/` (ADR 0014 restricts only `domain/` cross-imports), as `application/report` already does.

### Persistence
- Migration `backend/src/main/resources/db/migration/V25__enable_unaccent.sql`: `CREATE EXTENSION IF NOT EXISTS unaccent;`. No table, column or index change. Migration test (`UnaccentMigrationTest`, a throwaway database of the shared container, because the extension is database-wide): V25 on top of V1-V24 succeeds inside Flyway's per-migration transaction on `postgres:17.11-alpine` (the image ships the extension), `SELECT unaccent('Café açaí')` returns `Cafe acai`, and `unaccent` leaves `\`, `%` and `_` unchanged so applying it to the already-escaped pattern keeps the escapes intact (also asserted end to end by the repository tests with a literal `%` and `\`).
- `infrastructure/persistence/shared/TextSearchPredicates.java` (new package if no shared persistence package exists): `static String likePattern(String text)` that escapes `\`, `%`, `_` with `\` and wraps in `%…%`; `static Predicate matches(CriteriaBuilder cb, Expression<String> column, String pattern)` building `cb.like(cb.lower(cb.function("unaccent", String.class, column)), cb.lower(cb.function("unaccent", String.class, cb.literal(pattern))), '\\')`. The `unaccent` call is unqualified (ADR 0030).
- `TransactionRepositoryAdapter.toSpecification`: add the `type` equality predicate and, when `searchText` is set, an OR of `matches(description)` and `matches(additionalNotes)` (null-safe: a null notes column never matches). `TransferRepositoryAdapter` gets the same search clause in its `Specification`.
- `TransactionRepositoryAdapter.sumAmountByType`: a criteria query selecting `type` and `sum(amount)` grouped by `type`, built from the same `toSpecification` predicate so the list and the summary can never disagree on a filter.
- In-memory fakes (`FakeTransactionRepository`, `FakeTransferRepository`): implement `type`, `searchText` (case-insensitive, accents stripped with `java.text.Normalizer`) and `sumAmountByType`, and keep handling `Pageable.unpaged()` (csv-output convention).
- Index/performance: none added (ADR 0030).

### API
- `GET /api/ledger` in `infrastructure/web/ledger/LedgerController.java` (authenticated by default, nothing added to `permitAll`). Query params, all optional: `dateFrom`, `dateTo` (ISO date, inclusive), `type` (`INCOME|EXPENSE|TRANSFER`), `accountId`, `categoryId`, `paymentMethodId`, `q` (`@Size(max = 100)`). `200` with `LedgerResponse`; `400` for an unparsable date/enum/UUID or a `q` longer than 100 characters.
- `LedgerResponse(List<LedgerEntryResponse> entries, LedgerSummaryResponse summary, boolean truncated)`; `LedgerSummaryResponse(BigDecimal income, BigDecimal expense, BigDecimal net)`.
- `LedgerEntryResponse` (flat, nulls where not applicable): `id`, `type`, `date`, `amount` (always positive), `description`, `additionalNotes`, `categoryId`, `accountId`, `paymentMethodId` (transactions), `fromAccountId`, `toAccountId`, `tradeLines` (`[{side, productId}]`, empty unless the transfer is a trade confirmation). `id` is the transaction or transfer id; the frontend uses `type` to pick the right endpoint for edit and delete.
- Declare the error `@ApiResponse`s the way the other controllers do (errors convention); regenerate `frontend/src/api/generated/schema.ts`.

## Frontend

### API layer
- `src/api/ledger/ledger.ts` and `ledgerQueries.ts`: `getLedger(filter)` over the shared Axios client; `ledgerKeys` key factory; `useLedger(filter)` with `placeholderData: keepPreviousData` so month changes do not blank the table. Mutations already invalidate every query (frontend `CLAUDE.md`), so no ledger-specific invalidation is added.
- `src/api/transactions/transactionsQueries.ts`: add `useTransaction(id: string | null)` over the existing `getTransaction`, mirroring `useTransfer`.
- `src/api/core/apiError.ts`: add a `PROBLEM_TYPE_MESSAGES` entry for the `HandlerMethodValidationException` type, slug `handler-method-validation` (confirmed by `LedgerControllerTest`), with en/pt-BR text such as "One of the values is not valid."
- `src/mocks/handlers/ledger.ts` (registered in `src/mocks/handlers.ts`): reads the same in-memory stores as the transactions and transfers handlers so create/edit/delete show up, implements `type`, `accountId`, `categoryId`, `paymentMethodId`, date range and `q` (case- and accent-insensitive via `String.prototype.normalize('NFD')`), the 1,000 cap, and the summary.

### Shared pieces
- `src/components/inputs/MonthNavigator.tsx` (+ test): props `value` (`YYYY-MM`), `onChange`, optional `label`; previous/next `IconButton`s with i18n `aria-label`s around `MonthPicker`.
- `src/utils/localDate.ts`: add `shiftMonth(month, delta)` and `monthRange(month)` (first and last local date of a month, no UTC conversion) unless equivalents exist.
- `src/hooks/useDebouncedValue.ts` (+ test) unless an equivalent exists.

### Ledger page
- `src/features/ledger/ledgerSearchParams.ts` (+ test, pinned non-UTC zone): parse and serialize the URL contract from Decisions into a typed `LedgerView` and into an API `LedgerFilter`; month `^\d{4}-(0[1-9]|1[0-2])$` else default; `type` in the URL is lower-case (`income|expense|transfer`) and mapped to the API enum.
- `src/features/ledger/ledgerEntryStyle.ts`: maps entry `type` to icon, theme color token and sign: income = `ArrowUpward`/`success`/`+`, expense = `ArrowDownward`/`error`/`-`, transfer = `SwapHoriz`/`info`/no sign. Colors come from the MUI theme only. Mobile cards add a colored left border.
- `src/features/ledger/LedgerTable.tsx` (+ test): a `ResponsiveTable` of `LedgerEntry` rows shared by the page and the embed. Columns: date, type indicator (icon with an accessible label), description, category (`—` for transfers), account (`From → To` for transfers, with Buy/Sell chips up to 3 then "+N more" from `tradeLines`, same logic as today's `TransfersPage`), payment method (`—` for transfers; tablet priority low), amount (signed and colored per `ledgerEntryStyle`; transfers unsigned and neutral). Props: `entries`, `state` (a `LoadState`), name lookups, optional `onEdit`/`onDelete` (omitted in the embed), `embedded`.
- `src/features/ledger/LedgerSummary.tsx`: income, expense, net, as a row of labelled figures (compact single line below `sm`).
- `src/features/ledger/LedgerFilters.tsx`: in a `ResponsiveFilterBar`: scope toggle (Month / Range), `MonthNavigator` or from/to date fields, type select (All / Income / Expense / Transfer), account select (all accounts, including closed), category select, payment-method select, search field (`maxLength={100}`, clear button). Choosing type Transfer clears and disables category and payment method; setting either of those while type is Transfer is impossible.
- `src/features/ledger/LedgerPage.tsx` (+ test): reads the view from `useSearchParams`, calls `useLedger`, renders filters, summary, a truncation `Alert` (info) when `truncated`, and the table. Empty states: no entries in scope; and, when `q` is set outside all-time, "no matches in this scope" with a "Search all time" button that switches to `scope=range` with both dates empty. Split "Add" button (`Button` plus `Menu`) with "New transaction" and "New transfer".
  - Edit: a row's edit button sets the editing target `{ kind, id }`; `useTransaction`/`useTransfer` fetch it and the matching dialog opens when loaded. Delete: `ConfirmDialog`, then `useDeleteTransaction` or `useDeleteTransfer` by `type`.
  - `?focus={transferId}` keeps working: fetched through `useTransfer(focusId)` and opened as an edit, independent of the current scope; after it is consumed only the `focus` key is removed from the URL (the old page's `setSearchParams({}, …)` would wipe the other params).
  - `location.state.presetCategoryId` (from `FuelPage`) still opens the new-transaction dialog preset.
- `src/features/transactions/TransactionFormDialog.tsx`: the create/edit dialog extracted unchanged from `TransactionsPage` (using `TransactionFormFields.tsx` and `transactionForm.ts`). `TransferForm.tsx` stays where it is. Both are imported by `LedgerPage`.
- `src/features/ledger/AccountLedger.tsx` (+ test): `MonthNavigator` (local state, defaults to the current month), `LedgerSummary`, and a read-only `LedgerTable` for one `accountId`, plus the "View in Ledger" link; no other filters.
- `src/features/ledger/LegacyRedirects.tsx`: `TransactionsRedirect` (to `/ledger`) and `TransfersRedirect` (to `/ledger` with `type=transfer` added), both `<Navigate replace>` with the current `location.search` appended (a string `to` does not carry the query string).

### Routing, nav, callers
- `src/App.tsx`: `/ledger` → lazy `LedgerPage` (named export, `.then(m => ({ default: m.LedgerPage }))`); `/transactions` and `/transfers` → the redirect components; remove the two old page imports.
- `src/components/layout/Layout.tsx`: replace the `nav.transactions` and `nav.transfers` items with one `nav.ledger` item (`/ledger`) in the transactions slot; `common.json` en "Ledger", pt-BR "Lançamentos".
- `src/features/fuel/FuelPage.tsx`: `navigate('/ledger', { state: { presetCategoryId } })`.
- `src/features/investmentProducts/InvestmentProductDetailPage.tsx`: both links to `/ledger?focus=${trade.transferId}`.
- `src/features/accounts/AccountDetailPage.tsx`: replace the Transactions and Transfers sections with one "Transactions & transfers" section rendering `AccountLedger`; an `INVESTMENT` account keeps its products section above it. Update `AccountDetailPage.test.tsx` and the skeleton (one list section instead of two).
- Delete `TransactionsPage`, `TransfersPage`, `AccountTransactionList`, `AccountTransferList` and their tests.

### i18n
New namespace `ledger` (`src/i18n/locales/en/ledger.json`, `pt-BR/ledger.json`, registered in `i18n.ts` and `i18next.d.ts`) for the page, filters, summary, empty states and the type labels; page-only keys left in the `transactions` and `transfers` namespaces after the deletions are removed (dialog strings stay); `accounts:detail.transactions` / `accounts:detail.transfers` become `accounts:detail.ledger`. The locale-parity test must pass. Month labels use the formatter in `src/i18n/format.ts`.

### Tests and e2e
- Vitest: URL parser/serializer, `ledgerEntryStyle`, `LedgerTable` (each row type: icon, sign, `—` cells, chips), `LedgerFilters` (Transfer disables category and payment method), `LedgerPage` (month navigation, scope toggle, search to URL, truncation notice, "Search all time", focus deep link, preset category, add menu, edit/delete), `AccountLedger`, redirects (query string kept), `MonthNavigator`.
- Playwright (`e2e/ledger.spec.ts` replacing `transactions.spec.ts` and `transfers.spec.ts`; update `accounts.spec.ts` and `login.spec.ts` references): geometry at the mobile/tablet/desktop projects (no horizontal overflow at 360px, summary and filter bar fit).

## Docs
- ADR 0030 written (this PR) and indexed.
- `docs/PRD.md`: §6.16 Ledger added, pointers in §6.1 and §6.2, a deferred-items line in §9 (this PR).
- `docs/conventions/frontend-components.md`: `MonthNavigator` and the shared `LedgerTable` usage; `docs/conventions/frontend-structure.md`: `inputs/` now lists `MonthNavigator`.
- `docs/conventions/errors.md`: the first parameter-level `@Size` (built-in method validation, no `@Validated`) and the resulting `HandlerMethodValidationException` problem type; the file currently states no parameter-level validation exists.
- `docs/conventions/data-constraints.md`: `MAX_SEARCH_TEXT_LENGTH` is a query-parameter bound with no domain or column counterpart.
- `docs/features/F004-transactions/spec.md` and `F005-transfers/spec.md`: one-line note at the top of each that the list UI is superseded by F037; their backend is unchanged.
- `docs/features/README.md` row and root `README.md` "Project status" (this PR for "Documented and next up"; the implementing PR moves it to "Built").
- `CHANGELOG.md`: entry ships with the implementing PR (root `CLAUDE.md`): `**F037 — Ledger**` under `## [Unreleased]`, with an `Upgrade:` sub-line because the migration enables the `unaccent` extension on the existing database.
- No `CLAUDE.md` change: the new rules are derivable from code and the conventions files above.

## Non-goals
- Running balance per row: the account page still shows the account's balance; a per-row balance is a PRD §9 deferral.
- User-selectable sort and day-group headers: the list is always newest first, flat.
- Search over category, account or product names, trade-line tickers, or match highlighting; search inside the account embed.
- Bulk select or delete, and export: reports (F030) remain the export path and are unchanged.
- An investment-product filter in the UI (the API on `/api/transfers` keeps it).
- A signed (in/out) transfer amount relative to an account, including on the account page: wrong for credit cards, and the `From → To` cell already shows direction.
- Removing the paged `GET /api/transactions` and `GET /api/transfers` endpoints.
- Pagination: the whole scope loads, capped at 1,000 rows with a visible notice.

## Open questions
- The exact Material icons and the `info` vs `primary` token for transfers are settled when the table is built; the contract is neutral/blue, icon-plus-sign, theme tokens only.

## Dependencies
F004 (transactions, filters, `TransactionFormFields`), F005 and F027 (transfers, trade confirmations, `?focus=`, `TransferForm`), F003 (account detail page, `AccountBalanceQuery` untouched), F019 (TanStack Query hooks), F021 (`ResponsiveTable`, `ResponsiveFilterBar`, `ResponsiveDialog`), F031 (Problem Details, `PROBLEM_TYPE_MESSAGES`), F033 (react-i18next, locale parity), F036 (login: the endpoint is authenticated by default), F030 (precedent for merging both lists; report untouched), ADR 0030 (`unaccent`). Touches without changing behaviour: F024 (the Fuel page's preset navigation now targets `/ledger`), F009/F027 (the product detail page's "View confirmation" link).
