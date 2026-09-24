# F013 — Action Plan

**Depends on**: F002, F003, F017, F004, F005, F006, F007, F008, F009, F015 (see also [ADR 0012](../../adr/0012-investments-as-accounts-and-transfers.md)) (all entity-owning features, plus test tooling).

## Backend
- [x] Write tests first for filter applicability per the PRD §6.9 table: each filter affects only its documented files, reference-only files stay full regardless, no-filter export includes everything.
- [x] Generic row-to-CSV utility (header + rows, FK columns duplicated as `_id`/`_name`), and per-entity export queries for all twelve CSVs (including the trade-detail and product columns on `transfers.csv`, and the empty opening fields for `INVESTMENT` accounts) implementing the filter rules above to make those tests pass.
- [x] `DataExportService`: runs all twelve, zips them into one stream.
- [x] `GET /api/export` endpoint streaming the ZIP with correct `Content-Disposition`.

## Frontend
- [x] `src/api/export.ts` (triggers a native download rather than JSON parsing).
- [x] `src/features/export` — filter inputs + download button.

## Verification
- [x] Full export with no filters: confirm all twelve CSVs are present and each row's `_name` columns (including `institution_name` on accounts, and the product and category/sub-category names on `transfers.csv`/`investment_products.csv`) match the referenced entity's current name. *Verified by `DataExportServiceTest`/`DataExportControllerTest` and by calling `GET /api/export` on a running backend against the dev database (all twelve files present, names as expected); not checked in a browser.*
- [x] Filtered export (date range + account): confirm only the documented files are filtered and reference files remain complete. *Verified the same way (a unit test per filter, plus `GET /api/export?dateFrom=&dateTo=&accountId=` on a running backend: transactions, transfers and recurring templates shrank, every reference file stayed full, and the account filter left snapshots and budgets alone); not checked in a browser.*
