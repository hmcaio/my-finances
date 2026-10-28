<!--
Template for docs/features/FXXX-slug/plan.md. Copy it, replace every placeholder, delete every comment.
Every checklist item starts unchecked (`- [ ]`): design-feature plans, implement-feature ticks.
One item = one coherent, verifiable change (roughly one commit). Name the files/classes it touches.
Test-first marking: for every rules-heavy item write two items in this order, "Test first: <exact cases the test asserts>" then
"Implement <class/file>"; or one item opening "Write tests for <X> (<cases>), then implement." Items with no logic to test (config, wiring, generated files) need no test item.
-->
# FXXX — Action Plan

**Depends on**: <FXXX, FYYY, or "none">. See [ADR NNNN](../../adr/NNNN-slug.md).

<!-- One paragraph: the feature branch name (`feature/fxxx-slug`) and the layer order with the reason
("backend first - the body shape must exist before the frontend has anything to consume"). -->

## Phase 0 — Decision record
<!-- Delete the phase when there is no ADR and no PRD change. -->
- [ ] Write `docs/adr/NNNN-slug.md` and add its row to `docs/adr/README.md`.
- [ ] Update `docs/PRD.md`: <sections and what each gets>.

## Phase 1 — Backend: <slice name>
<!-- Order inside the phase: domain tests/impl, application, persistence + migration, REST, then the gate command.
Name the repo's own check: `./gradlew spotlessApply` then `scripts/verify.sh backend` (Docker running; the same stage CI runs). -->
- [ ] Test first: <domain cases>.
- [ ] Implement `domain/<aggregate>/Name.java`.
- [ ] Flyway migration `V<next>__<slug>.sql`: <tables/columns/backfill>, plus its migration test.
- [ ] REST: <verb + path>, controller tests for <status codes>; regenerate `frontend/src/api/generated/schema.ts`.
- [ ] `./gradlew spotlessApply` then `scripts/verify.sh backend` (Docker running).

## Phase 2 — Frontend: <slice name>
<!-- Delete for a backend-only feature. Frontend gate command: `scripts/verify.sh frontend` (lint, format check, type-check and build, tests with coverage: what CI runs; a bare `npm test` skips the coverage run). -->
- [ ] Test first: <component/hook behaviours>.
- [ ] Implement `src/features/<area>/<Component>.tsx`, `src/api/<area>/<area>Queries.ts`, MSW handler.
- [ ] `scripts/verify.sh frontend`.

## Phase 3 — Docs
- [ ] Root/backend/frontend `CLAUDE.md`: <the rule or gotcha each gets>.
- [ ] `docs/features/README.md` row verified against what was built, and the feature removed from its "Status" list.

## Verification
<!-- Human-driven checks, each phrased as something a person does in the running app and the result they see
("create X, confirm Y appears"; "stop Postgres, hit Z, body is exactly ..."). implement-feature ticks each one only after driving it with the `run` skill.
Cover: the happy path, each rejected/edge case from the spec, migration of pre-existing data, and the no-leak/no-regression check for cross-cutting work.
Finish with the automated gates (the backend and frontend commands above, all green). -->
- [ ] <action in the running app> -> <observable result>.
- [ ] <rejected case> -> <exact error/status>.
- [ ] Migrate a database with pre-existing <rows>: <result, unchanged reports>.
- [ ] `scripts/verify.sh backend frontend` (Docker running) all green.
