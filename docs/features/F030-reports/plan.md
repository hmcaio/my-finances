# F030 — Action Plan

**Depends on**: F002–F010, F017, F022, F023, F024, F026, F027 (all entity-owning/report-source features). Retires F013.

## Backend
- [x] Move F013's generic CSV-writing helper and ZIP-writing code from `application/dataexport` to `application/report` (same formula-injection guard, RFC 4180 formatting); delete `application/dataexport` once nothing references it.
- [x] `ReportType`, `ReportFilter`, `ReportGenerator` interface, `GeneratedFile`/`ReportFormat`.
- [x] `AllocationGrouping.INSTITUTION` + the query-side change in `InvestmentAllocationQuery` to group by the holding account's institution.
- [x] `AnnualSummaryQuery` (new): income/expense totals for a calendar year from `TransactionRepository.findAll`, plus `NetWorthQuery.asOf` at year-start and year-end.
- [x] One `ReportGenerator` per type (eleven), each wired to existing queries/services per the spec's Backend section — write tests first for each generator's output shape (CSV column set, PDF section presence) against seeded data, including the budgets report's union of budgeted + unbudgeted categories and the fuel report's per-vehicle summary math.
- [x] `ReportService`: resolves generators, runs them, returns a bare file for one type or a ZIP for several.
- [x] Add OpenPDF to `backend/gradle/libs.versions.toml`/`build.gradle`; a small `PdfReportWriter` helper (table-rendering utility reused by the FII portfolio and budgets PDF outputs).
- [x] `POST /api/reports` controller + DTOs; `400` on an unknown/empty `types` list.
- [x] Remove `DataExportController`, `DataExportService`, `GET /api/export`, and their tests (or repurpose any still-relevant assertions into the new generators' tests).
- [x] Regenerate `frontend/src/api/generated/schema.ts`.

## Frontend
- [x] `src/api/reports/reports.ts` — `POST /api/reports`, arraybuffer response, client-side filename, object-URL download (same pattern as F013's `export.ts`).
- [x] `features/reports/ReportsPage` — checkbox list of the eleven types, shared filter bar showing only the dimensions at least one checked type uses, Generate button, reversed-date-range client-side validation.
- [x] Remove `features/export/ExportPage` and `src/api/export/export.ts`.
- [x] Nav entry: rename "Export" to "Reports", same route position (`/reports` replacing `/export`).

## Docs
- [x] `docs/features/F013-data-export/spec.md` — add a superseded-by note at the top (pattern: F003's note about F017), same for `plan.md` if it references the live endpoint. *(spec.md's note was already in place from the design PR and still reads accurately; `plan.md` references the endpoint only in past-tense verification notes, same as F003's own plan.md leaves its superseded field undecorated, so no change there.)*
- [x] `docs/features/README.md` — new F030 row; amend F013's row to note it's superseded. *(both already accurate from the design PR, verified unchanged.)*
- [x] Root `README.md` — amend the F013 bullet under "Built" to note supersession (already done); move F030 from "Documented and next up" into "Built" with a real summary of what was actually built.
- [x] `docs/PRD.md` §6.9 rewritten (already done by the design-feature run that produced this plan) — verified against the actual implementation and fixed the one drifted line (the CSV+PDF-still-zips-alone behavior wasn't spelled out).
- [x] `CHANGELOG.md` `[Unreleased]` entry (`feat(F030): ...`), noting the removal of `GET /api/export` as a breaking-ish behavior change for anyone scripting against it (no formal API versioning in this app, but worth a line).

## Verification
- [x] Each report type, with and without its applicable filters, against seeded data: correct rows/columns, correct as-of fallback (a date with no exact snapshot still returns the latest one before it), correct CSV formula-injection guarding. *(Verified via each generator's own unit tests against fakes, plus `ReportServiceTest`/`ReportControllerTest` against a real Testcontainers Postgres.)*
- [x] Budgets report: a budgeted category shows cap/actual/variance, an unbudgeted category with spend shows actual only, a category with no spend and no budget doesn't appear. *(`BudgetsReportGeneratorTest`.)*
- [x] Multi-select batch: two or more types produce a ZIP with one correctly named file per type; a single type produces a bare file, not a ZIP (except FII portfolio/Budgets alone, which still zip - two files from one type). *(`ReportServiceTest`, `ReportControllerTest`.)*
- [x] `GET /api/export` is gone (404), `/export` page is gone, `/reports` page works end to end. *(`ReportControllerTest.getApiExportNoLongerExists`, `App.test.tsx`'s nav routing test, and the full `scripts/verify.sh backend frontend` run - not checked in a live browser session.)*
