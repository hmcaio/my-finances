# F013 — Action Plan

**Depends on**: F002, F003, F004, F005, F006, F007, F008, F009, F015 (all entity-owning features, plus test tooling).

## Backend
- [ ] Write tests first for filter applicability per the PRD §6.9 table: each filter affects only its documented files, reference-only files stay full regardless, no-filter export includes everything.
- [ ] Generic row-to-CSV utility (header + rows, FK columns duplicated as `_id`/`_name`), and per-entity export queries for all twelve CSVs implementing the filter rules above to make those tests pass.
- [ ] `DataExportService`: runs all twelve, zips them into one stream.
- [ ] `GET /api/export` endpoint streaming the ZIP with correct `Content-Disposition`.

## Frontend
- [ ] `src/api/export.ts` (triggers a native download rather than JSON parsing).
- [ ] `src/features/export` — filter inputs + download button.

## Verification
- [ ] Full export with no filters: confirm all twelve CSVs are present and each row's `_name` columns match the referenced entity's current name.
- [ ] Filtered export (date range + account): confirm only the documented files are filtered and reference files remain complete.
