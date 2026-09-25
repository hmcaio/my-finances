# F013 — Data Export

## Summary
All-entity export as a ZIP of CSVs, with FK names denormalized inline, optionally filtered by date range/account/category (PRD §6.9). The last feature since it reads from every other entity in the system.

## Scope
- One CSV per entity: `categories.csv`, `payment_methods.csv`, `institutions.csv`, `accounts.csv`, `transactions.csv`, `transfers.csv`, `budgets.csv` (one row per `BudgetVersion`), `recurring_templates.csv` (one row per `RecurringTemplateVersion`), `investment_categories.csv`, `investment_subcategories.csv`, `investment_products.csv`, `investment_snapshots.csv` — twelve files. Investment accounts are rows of `accounts.csv` (`type` = `INVESTMENT`, opening balance and date empty), and buys/sells are rows of `transfers.csv`, which also carries `investment_product_id`/`investment_product_name`, `quantity`, `unit_price` and `taxes` (empty for ordinary transfers; ADR 0012). `investment_products.csv` carries the category and sub-category id/name (sub-category empty when none). `accounts.csv` carries `institution_id` and `institution_name` (never empty — accounts without a real institution point at the built-in "No institution" row, which `institutions.csv` includes), per the FK-name convention.
- Delivered as a single ZIP download.
- Optional filters: date range, account, category — applied only to files with that dimension (PRD §6.9 spells out exactly which files each filter touches); reference-only files are always exported in full.

## Backend

### Application
- One `CsvWriter`-style helper per entity (or a small generic row-to-CSV utility reused across all twelve), each responsible for: selecting rows (applying the relevant filter(s) if provided and applicable to that entity), and writing a header row plus data rows with FK columns duplicated as both `_id` and `_name`.
- A `DataExportService` orchestrates: run each entity's writer into an in-memory or temp-file CSV, then zip all twelve together into the response stream.
- Filter applicability, exactly as PRD §6.9 specifies:
  | Filter | Applies to |
  |---|---|
  | Date range | `transactions`, `transfers`, `investment_snapshots`, `budgets` (by `effective_from`), `recurring_templates` (by `effective_from`) |
  | Account | `transactions`, `transfers` (matches either side), `recurring_templates` |
  | Category | `transactions`, `budgets`, `recurring_templates` |
  | Always full, no filter | `categories`, `payment_methods`, `institutions`, `accounts`, `investment_categories`, `investment_subcategories`, `investment_products` |

### API
- `GET /api/export?dateFrom=&dateTo=&accountId=&categoryId=` — a `.zip` response (`Content-Type: application/zip`, `Content-Disposition: attachment; filename="my-finances-export-YYYY-MM-DD.zip"`, the date being today). Any combination of filters, or none (full export). `dateFrom` after `dateTo` is a `400` (`InvalidExportRangeException`); a malformed date or UUID is Spring's standard `400`. An unknown `accountId`/`categoryId` is not an error, it simply matches nothing in the filtered files.

### Decisions (resolving PRD §8's "exact CSV column ordering/naming and ZIP naming")
- **Built in memory, then sent.** `DataExportService.export(filter, OutputStream)` writes the twelve entries straight into a `ZipOutputStream`, but the controller hands it a `ByteArrayOutputStream`, so a failure is a clean error response rather than a truncated download. Fine for a local single-user dataset. The service is `@Transactional(readOnly = true)` so all twelve files come from one snapshot.
- **Format**: UTF-8 without a BOM, RFC 4180 (CRLF line endings; a cell is quoted only if it contains a comma, quote, CR or LF, quotes doubled). Dates are ISO (`2026-03-09`), a budget/template `effective_from` and `last_generated_for` are `yyyy-MM`, amounts are plain decimals (no scientific notation, database scale kept, so trade `quantity`/`unit_price` read `1.00000000`), booleans `true`/`false`, enums their names, `null` an empty cell. Excel may need "import as UTF-8" for accented names.
- **Formula-injection guard**: a *text* cell (names, descriptions, notes: the only user-typed values) whose first character is `=`, `+`, `-`, `@`, tab or CR is written with a leading `'`, so spreadsheets show it as text instead of evaluating it. Ids, dates, enums and numbers are never touched (a negative amount stays a number). The trade-off is that the exported text differs from the stored text by that one character; the export is for reading in a spreadsheet, and the guard is the safer default.
- **Row order** is deterministic: dated files by date then id, versioned files by parent then `effective_from`, reference files by name then id.
- **Date range on versioned files compares months**: a budget or recurring-template version is kept when its `effective_from` month lies within `[month(dateFrom), month(dateTo)]`, so `dateFrom = 2026-02-20` still includes the version effective `2026-02`. Filters combine with AND.
- **Columns** (FK columns are `x_id` then `x_name`; `investment_subcategory_*` is empty when a product has none):
  - `categories`: `id, name, type, built_in`; `payment_methods`: `id, name`; `institutions`: `id, name, built_in`; `investment_categories`: `id, name`
  - `accounts`: `id, name, type, institution_id, institution_name, opening_balance, opening_balance_date, closed_date`
  - `transactions`: `id, date, amount, type, category_*, account_*, payment_method_*, recurring_template_version_id, description, additional_notes`
  - `transfers`: `id, date, from_account_*, to_account_*, amount, description, additional_notes, investment_product_*, quantity, unit_price, taxes`
  - `budgets` (one row per version): `budget_id, version_id, category_*, monthly_cap, effective_from` — `monthly_cap` is empty on a tombstone version (the budget was stopped from that month, F006 / issue #61)
  - `recurring_templates` (one row per version): `template_id, version_id, category_*, account_*, description, active, last_generated_for, amount, day_of_month, effective_from`
  - `investment_subcategories`: `id, investment_category_*, name`; `investment_products`: `id, account_*, investment_category_*, investment_subcategory_*, name, closed_date`; `investment_snapshots`: `id, investment_product_*, date, balance`

## Frontend
- Export page (`/export`, `features/export/ExportPage`): optional From/To dates, account (closed and investment accounts included) and category selects, a note on which files each filter touches and that the reference files are always complete, and a "Download" button. It rejects a reversed date range client-side. The request goes through the shared Axios client (so `X-Request-Id` is sent) with `responseType: 'arraybuffer'` (a `blob` response hangs under MSW's XHR interceptor on Node 22) and is saved through a temporary object URL as `my-finances-export-YYYY-MM-DD.zip` (named client-side: `Content-Disposition` isn't exposed to the dev server's cross-origin requests).

## Dependencies
All prior features (F002–F009, F017) — this is the last feature to implement, since it reads from every entity introduced by them.
