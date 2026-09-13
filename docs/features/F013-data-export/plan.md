# F013 — Action Plan

**Depends on**: F002, F003, F004, F005, F006, F007, F008, F009 (all entity-owning features).

## Backend
- [ ] Generic row-to-CSV utility (header + rows, FK columns duplicated as `_id`/`_name`).
- [ ] Per-entity export query for all twelve CSVs, each applying the applicable filters per the PRD §6.9 table.
- [ ] `DataExportService`: runs all twelve, zips them into one stream.
- [ ] `GET /api/export` endpoint streaming the ZIP with correct `Content-Disposition`.
- [ ] Tests: each filter affects only its documented files and leaves reference-only files full; no-filter export includes everything.

## Frontend
- [ ] `src/api/export.ts` (triggers a native download rather than JSON parsing).
- [ ] `src/features/export` — filter inputs + download button.

## Verification
- [ ] Full export with no filters: confirm all twelve CSVs are present and each row's `_name` columns match the referenced entity's current name.
- [ ] Filtered export (date range + account): confirm only the documented files are filtered and reference files remain complete.
