# F030 — Reports

## Summary
Replaces F013's all-entity backup-shaped CSV/ZIP export with eleven curated, human-readable reports, each reusing an existing read-side query wherever one already exists. F018 (specced, not yet built) makes real `pg_dump` backups the disaster-recovery mechanism, so F013's raw per-table dump — already documented as "not disaster recovery" in both the PRD and F018's own spec — has no remaining purpose and is retired outright rather than kept alongside these. PRD §6.9 (retitled "Reports").

## Scope
Eleven report types, picked via a multi-select on one Reports page and generated through one shared filter bar (date range, as-of date, account, category, vehicle, month — each report ignores the filters it has no dimension for). One report selected returns that report's bare file (CSV or PDF); two or more selected types, **or a single selected type whose own generator renders more than one file** (FII portfolio and Budgets & spending each render both a CSV and a PDF), return a ZIP — see Decisions and the API section for exactly how that's decided. No report writes anything or calls the `AuditLog` port (ADR 0022) — generation is read-only regardless of HTTP verb (see Decisions).

1. **Transactions & Transfers** — one combined chronological statement (a `type` column distinguishes a transaction row from a transfer row), filtered by date range + account + category. CSV.
2. **Account balances** — every open (and, if asked, closed) account's balance as of a selectable date, falling back to the latest value at or before that date wherever the source query is itself as-of-aware (true of every balance/snapshot query already in this codebase — see Backend). CSV.
3. **FII portfolio** — the existing FII portfolio list (ticker, segment, cotas held, amount contributed, current value, latest snapshot date) as of a selectable month. CSV + PDF.
4. **Fuel history & stats** — every fuel fill-up in a date range with its per-row ratios (km/L, spend/km, L/km), plus one per-vehicle summary block (total spent, total liters, average km/L) computed at report time. CSV.
5. **Budgets & spending by category** — every category with any spend in a selected month: a budgeted category shows cap, actual and variance; an unbudgeted category shows actual only (cap/variance blank). Single month only. CSV + PDF.
6. **Investments** — every holding's latest snapshot as of a selectable date, plus the trade-confirmation buy/sell lines booked against those holdings within a date range. Splits (F028/F029) are excluded — nothing to report on until that feature ships. CSV.
7. **Net worth trend** — the existing net-worth trend series over a date range, same Monthly/Every-change granularity toggle as the dashboard chart. CSV.
8. **Recurring templates / fixed costs summary** — every currently-active template (description, category, account, amount, day-of-month, next occurrence) as of a selectable date. No history, no inactive templates — a "what am I committed to right now" view. CSV.
9. **Investment allocation** — two sections in one report: by institution (every investment holding, any product, grouped by the holding's account's institution) and by FII segment (existing FII-only segment allocation). Both as of a selectable date/month. CSV.
10. **Dividend income** — every dividend transaction in a date range, per-product totals with a per-month subtotal column. CSV.
11. **Annual summary** — one calendar year's total income, total expense, and net worth at the start and end of the year with the delta. No per-category breakdown (report 5 already covers that, per month). CSV.

### Retiring F013
`DataExportService`, `DataExportController`, `GET /api/export`, the `/export` frontend page and its API client are all removed, not kept alongside. F013's spec/plan are marked superseded (history, not deleted) and `docs/features/README.md`/root `README.md` note the replacement.

## Decisions
- **No new migration.** Every report either reuses an existing application-layer query as-is, or needs only a small addition to one (a new `AllocationGrouping.INSTITUTION` value; a new `AnnualSummaryQuery` that sums already-queryable transactions and calls `NetWorthQuery.asOf` twice). No new table, no new column.
- **`POST /api/reports`, not GET.** F013's handful of scalar filters fit query params; a batch of up to 11 report types plus up to 7 filter dimensions doesn't, and there's no bookmarking/caching need for a generated file. Body: `{types: [...], filter: {...}}`.
- **Report generation calls queries directly, in-process — no per-report-type REST endpoint.** A `ReportGenerator` per type is constructor-injected with the existing application services/queries it needs (e.g. the fuel report's generator depends on `FuelRatiosQuery` and `TransactionRepository`, not a new fuel-report controller). Only one endpoint is added for all eleven types.
- **"As of" fallback is just what the underlying queries already do.** `AccountBalanceQuery.balanceAsOf`, `LatestInvestmentSnapshotQuery.latestOf(holdingId, asOfDate)`/`latestByHolding(asOfDate)`, and `FiiPortfolioQuery.portfolio(status, asOf)` already resolve to the latest value at or before the given date — no new "fallback" logic needed; a report's as-of date is simply passed straight through.
- **PDF via OpenPDF (LibrePDF), not iText.** iText's current major is AGPL-3.0, which is a poor fit for a project that publishes Docker images; OpenPDF is LGPL-2.1/MPL-2.0 and has a plain `Document`/`Paragraph`/`PdfPTable` API, enough for the two reports (FII portfolio, budgets) that get a PDF rendering. Added to `backend/gradle/libs.versions.toml`.
- **CSV writer is F013's, generalized.** F013's generic row-to-CSV helper (header + rows, RFC 4180, UTF-8, formula-injection guard on text cells) moves from `application/dataexport` to `application/report` and is reused by every report's CSV output — no new CSV-writing code.
- **Investments report's "institution" and "segment" sections are genuinely different scopes**, called out in the report rather than hidden: institution spans every investment holding regardless of product; segment is restricted to the "REITs (FIIs)" sub-category, same restriction `FiiAllocationQuery` already has. Documented as two clearly labeled sections rather than merged into one table.
- **Annual summary stays high-level by design** — income/expense totals and net-worth start/end/delta only, no category breakdown, so it doesn't duplicate report 5's job.
- **`ReportGenerator.generate` returns a list of files, not one** (resolved during implementation, not fully settled by this doc's original wording): `List<GeneratedFile> generate(ReportFilter filter)`. Every generator but FII portfolio's and Budgets' returns a singleton list; those two return both a CSV and a PDF. `ReportService` zips whenever the *total* file count across every requested type is more than one — "one file per type" in the common case, but a single-type request for FII portfolio or Budgets still zips, since it resolves to two files.
- **Annual summary's year comes from the `month` filter's year** (`YearMonth.getYear()`), defaulting to the current year when `month` is absent — no new filter field, consistent with "no new migration"/no schema change; `ReportFilter` already carries `month` for the two month-scoped reports, and a `YearMonth` already carries a year.
- **Account balances report has no `includeClosed` toggle** — `ReportFilter` carries no such field (the spec's filter list is fixed to the seven dimensions above), so the report always includes every account, open and closed, with its own `closed_date` column signaling status; this satisfies "every open (and, if asked, closed) account" without adding an unspecified filter field.

## Backend

### Application
New package `application/report/`:
- `ReportType` — enum, the eleven types above.
- `ReportFilter` — one value object carrying every optional filter dimension (`dateFrom`, `dateTo`, `asOfDate`, `accountId`, `categoryId`, `vehicleId`, `month: YearMonth`, `netWorthGranularity`), all nullable; a `ReportGenerator` reads only the fields it needs and ignores the rest.
- `ReportGenerator` — interface, `List<GeneratedFile> generate(ReportFilter filter)` (a list, not a single file — see Decisions); `GeneratedFile(String fileNameWithoutExtension, ReportFormat format, byte[] content)`.
- One generator per `ReportType`, each injected with existing queries/services only:
  - `TransactionsAndTransfersReportGenerator` — `TransactionRepository`, `TransferRepository`, `CategoryRepository`/`AccountRepository` for name lookups; merges both into one date-sorted list with a `type` discriminator column.
  - `AccountBalanceReportGenerator` — `AccountBalanceQuery.balanceAsOf`, every account (open and closed, with a `closed_date` column - see Decisions).
  - `FiiPortfolioReportGenerator` — `FiiPortfolioQuery.portfolio(status, asOf)`, `InvestmentSegmentRepository` for segment names. CSV and PDF.
  - `FuelReportGenerator` — `TransactionRepository.findAll` filtered to fuel transactions (optionally by `vehicleId`)/`FuelRatiosQuery.ratiosFor` per row, `VehicleRepository` for names, plus an in-generator summary pass (sum liters/amount, average km/L) per vehicle.
  - `BudgetsReportGenerator` — `BudgetReportQuery.forMonth(month)` for budgeted categories, unioned with every other `EXPENSE` category's actual spend for that month via `TransactionRepository.sumAmountByCategoryAndDateRange` (month-start to month-end) for categories `BudgetReportQuery` doesn't return. CSV and PDF.
  - `InvestmentsReportGenerator` — `LatestInvestmentSnapshotQuery.latestByHolding(asOf)` for the snapshot section; `TransferTradeLineRepository.findAll()` filtered in-memory to the date range (each `TransferTradeLine` already carries its own `date`) for the activity section, `AccountRepository`/`InvestmentProductRepository` for names.
  - `NetWorthTrendReportGenerator` — `NetWorthQuery.trend(from, to, granularity)` as-is.
  - `RecurringTemplatesReportGenerator` — `RecurringTemplateService`'s active-templates listing plus `RecurringTemplateVersionRepository` (to resolve the version effective for the as-of date's cycle, or the next cycle's if the current one's due date has already passed), as of a date (defaults to today).
  - `InvestmentAllocationReportGenerator` — `InvestmentAllocationQuery.allocation(asOfDate, AllocationGrouping.INSTITUTION)` (new grouping value, below) for the institution section; `FiiAllocationQuery.allocation(ACTUAL, SEGMENT, month)` for the FII segment section.
  - `DividendIncomeReportGenerator` — `DividendHistoryQuery.totalsByTicker`/`totalsByMonth`/`dividends` (all three already exist from F026) over the date range.
  - `AnnualSummaryReportGenerator` — new `AnnualSummaryQuery`: `TransactionRepository.findAll` with a date-range filter covering the calendar year (summed by `CategoryType` in Java, consistent with this app's small local dataset — no new repository aggregate method), plus `NetWorthQuery.asOf` at the year's first and last day.
- `ReportService` — orchestrates: for each requested `ReportType`, resolve its generator, run it, collect every `GeneratedFile` from every generator. One file in total → return as-is. More than one (several types, or one type whose generator yields several files) → zip them (reusing F013's zip-writing code, moved alongside the CSV helper, as `ZipBundler`).
- `AllocationGrouping` (existing, F009/F023) gains `INSTITUTION`: one row per institution, the sum of the latest snapshots of every investment holding under an account referencing that institution.

### Persistence
No migration. `AllocationGrouping` is a plain enum (no DB representation); `AnnualSummaryQuery` composes existing repositories.

### API
- `POST /api/reports` — body `{types: ["TRANSACTIONS_TRANSFERS", ...], filter: {dateFrom, dateTo, asOfDate, accountId, categoryId, vehicleId, month, netWorthGranularity}}`. Response: the bare file (`Content-Type` per format, `Content-Disposition: attachment; filename="my-finances-<type-slug>-YYYY-MM-DD.<csv|pdf>"`) when the request resolves to exactly one generated file, else a `.zip` (`my-finances-reports-YYYY-MM-DD.zip`, entries named the same way the bare-file response would have been) — in practice: one bare file for one selected type (except FII portfolio/Budgets, whose generators each yield a CSV and a PDF), a zip for two or more selected types or for a single FII-portfolio/Budgets selection. An unknown/empty `types` list is `400`; a filter value a given type doesn't use is silently ignored by that type's generator (not an error).
- Remove `GET /api/export` and `DataExportController`.
- Regenerate `frontend/src/api/generated/schema.ts`.

## Frontend
- New `/reports` page (`features/reports/ReportsPage`) replacing `/export`: a checkbox list of the eleven report types, one shared filter bar (`ResponsiveFilterBar` — date range, as-of date, account, category, vehicle, month, net-worth granularity toggle, each shown/enabled only when at least one checked type uses it), and a "Generate" button.
- `src/api/reports/reports.ts`: `POST` through the shared Axios client (`X-Request-Id` sent), `responseType: 'arraybuffer'` (same MSW-on-Node-22 reasoning F013 already documented), saved via a temporary object URL. `Content-Disposition` isn't exposed cross-origin in dev, but `Content-Type` is a CORS-safelisted response header, so the extension (`csv`/`pdf`/`zip`) is read from it rather than duplicating the backend's per-type format table client-side; the base name is `my-finances-reports-<today>` for a zip, `my-finances-<slug>-<today>` for a bare file, where `<slug>` comes from a small client-side `REPORT_TYPES` table (also used to render the checkbox list and each type's filter dimensions).
- Remove `features/export/ExportPage` and its API client.
- Nav entry renamed from "Export" to "Reports", same position.

## Dependencies
F002–F010, F017 (all entity-owning features this reads from), F022/F023 (holdings, allocation), F024 (fuel), F026 (FII portfolio, allocation, dividends), F027 (trade confirmation lines). Independent of F018/F028/F029 — F018 motivates the retirement of F013 but this feature's code doesn't depend on F018 shipping first; F028/F029 (unbuilt) are explicitly out of scope for the Investments report until they land.
