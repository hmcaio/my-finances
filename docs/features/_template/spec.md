<!--
Template for docs/features/FXXX-slug/spec.md. Copy it, replace every placeholder, delete every comment.
Keep the headings and their order. Delete a whole section only where its comment says it may go.
Specificity bar for every section: the reader runs `implement-feature` cold, so name WHERE code goes
(file paths, package names), WHAT the schema looks like (table and column shapes, constraints),
and WHICH endpoints exist (verb + path + status codes). A sentence the reader must interpret to find
a file, table or route is too vague.
-->
# FXXX — Title

## Summary
<!-- 1-3 short paragraphs: what the feature does, why now, which ADR/PRD section it implements
(link `../../adr/NNNN-slug.md`, cite `PRD §x.y`). End with the product surface: "Infrastructure/cross-cutting
only, no product or data-model change, no new migration" or "Frontend only: no backend change", when that applies. -->

## Scope
<!-- One bullet per layer or deliverable that is IN: Backend, Frontend, Infra, Docs. Each bullet names the
concrete artifacts (aggregate, endpoint, page, env var). Last bullet: "Out of scope: see [Non-goals](#non-goals)." -->
- Backend: <aggregate/endpoint/migration, concretely>.
- Frontend: <page/component/hook, concretely>.
- Docs: <ADR, PRD sections, CLAUDE.md files, features README row>.
- Out of scope: see [Non-goals](#non-goals).

## Decisions
<!-- Choices a reader could reasonably have made differently. Two columns, one row per decision; "Why" states
the reason AND the rejected alternative when one existed. A decision the ADR already carries in full is
one pointer line above the table ("See ADR NNNN for the full rationale. Implementation-level decisions
not already in the ADR:"), with only the extra rows here. -->

| Decision | Why |
|---|---|
| <what was decided, concrete enough to implement> | <reason; the alternative rejected and what it would have cost> |

## Backend
<!-- Delete this whole section for a frontend-only feature. Keep the subsections the feature touches, drop the rest.
Cross-cutting infrastructure: replace the four subsections with one `###` per mechanism (see F031, F036). -->

### Domain
<!-- Rules-heavy work (see F027 Backend). Per class: full path (`domain/<aggregate>/Name.java`), kind (record/enum/aggregate/value object),
constructor/factory signature, and every invariant with its exact condition. Name which invariants repeat at the DTO and DB layers
(root CLAUDE.md: free-text bounds, money precision and positivity at all three layers). -->
- `domain/<aggregate>/Name.java`: <kind, fields, invariants>.

### Application
<!-- Per use case/service/query: class name, the rule it enforces, the HTTP status each failure maps to,
the new `@ResponseStatus` exception class names, the `AuditLog` call for every write (root CLAUDE.md), the transaction boundary. -->
- `<Service>`: <behaviour, failure -> status/exception>.

### Persistence
<!-- Migration: next free `V` number, table and column shapes (`name type null/not null`, `check (...)`, `references ... on delete ...`, indexes),
backfill of existing rows, dropped columns/constraints, and the migration test (backend CLAUDE.md pattern). Money columns follow root CLAUDE.md. -->
- New migration (next free `V` number): `<table>` (`<column definitions>`).

### API
<!-- Every endpoint: verb + path, request/response DTO changes, status codes, validation rules. Close with
"Regenerate `frontend/src/api/generated/schema.ts`" when a controller changed. -->
- `POST /api/<resource>`: <request fields, `201`, `400`/`404`/`409` causes>.

## Frontend
<!-- Delete for a backend-only feature. Per file: path under `frontend/src/`, what changes, which API module/query hook/MSW handler/mock fixture it touches,
UI states (loading, empty, error), i18n keys when the UI is localized. -->
- `src/api/<area>/<area>.ts` and `<area>Queries.ts`: <HTTP functions, hooks, key factory>.
- `src/features/<area>/<Page>.tsx`: <behaviour>.

## Docs
<!-- Exactly which docs change and what each gets: ADR (already written / row added), `docs/PRD.md` sections or "No PRD change - <reason, precedent feature>",
root/backend/frontend `CLAUDE.md` bullets (only rules a reader could not derive from code), `docs/features/README.md` row and "Status" list,
root `README.md` only if it states something this feature changes. CHANGELOG: state "entry ships with the implementing PR (root CLAUDE.md)" for a user-visible feature. -->

## Non-goals
<!-- One bullet per thing a reader might assume is included and is not, each with the reason or the follow-up. -->
- <excluded capability>: <why not, or where it goes instead>.

## Open questions
<!-- Details the implementer settles, one bullet each. A claim about how a library, framework or tool behaves that was not checked
against its docs or source is written here verbatim as `unverified - confirm during implementation: <claim>`, never as settled text above.
Write "None." when empty. -->
None.

## Dependencies
<!-- Features (FXXX) and existing mechanisms this builds on, each with the reason; then which existing features it touches without changing their behaviour. -->
FXXX (<what it provides>), <existing convention>.
