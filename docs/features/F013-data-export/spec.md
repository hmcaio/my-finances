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
- `GET /api/export?dateFrom=&dateTo=&accountId=&categoryId=` — streams a `.zip` response (`Content-Disposition: attachment`). Any combination of filters, or none (full export).

## Frontend
- Export page/panel: optional date range, account, category filter inputs, a "download" button that triggers the browser's native file download of the ZIP response.

## Dependencies
All prior features (F002–F009, F017) — this is the last feature to implement, since it reads from every entity introduced by them.
