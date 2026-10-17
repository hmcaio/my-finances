# CLAUDE.md

Local, single-user finance app (Java/Spring Boot + PostgreSQL/Flyway backend, React/TypeScript/Vite/MUI frontend), bound to `localhost` unless `BIND_ADDR` says otherwise and run on demand, so recurring generation is catch-up-based, not cron-based. DDD, Hexagonal Architecture and test-first for the rules-heavy logic (ADR 0004). Stack rules load automatically in `backend/` and `frontend/` (`backend/CLAUDE.md`, `frontend/CLAUDE.md`).

## Source of truth

- **Read `docs/PRD.md` before any architectural or scope decision**: data model, versioning rules, net worth formula, requirements, non-goals. It already resolved the non-obvious tradeoffs (credit card spend is a liability-increasing expense and payments are transfers; budgets and recurring templates are versioned, not mutated; recurring generation is lazy/catch-up, not a scheduler), so decide from it, not from first principles.
- `docs/adr/` holds architectural decisions (index `docs/adr/README.md`); `docs/features/FXXX-*/{spec,plan}.md` one folder per feature (built vs. specified: each `plan.md` checklist and the README's "Project status"). Why something changed lives in `git log`, PR descriptions and `spec.md`.
- Mechanics for one area live in `docs/conventions/` (index: `docs/conventions/README.md`); pointers below name when to read each. Before calling a task done, when splitting or refactoring a method, and when reviewing a diff: read `CODING_STANDARDS.md`.

## Commands

```
docker compose --profile full up -d   # default dev: whole stack in containers (backend :8080, frontend :5173) with hot reload; ADR 0018
docker compose --profile full down
docker compose up -d      # Postgres only (+ pgAdmin at http://localhost:5050), for running the apps natively; data persists in a named volume
docker compose down
```
`full` and native `bootRun`/`npm run dev` both bind 8080/5173: use one. `scripts/verify.sh [versions|backend|frontend|e2e|backup]` is the one definition of the checks (CI calls it); change a check there, not only in `ci.yml`. Run `scripts/setup-hooks.sh` once to install the local pre-commit format check. Backend and frontend commands: their own `CLAUDE.md`.

## Workflow

- Conventional Commits (ADR 0009). Branch off `develop` and PR into `develop` (ADR 0008); never commit to `develop` or `main` directly; push or open a PR only when asked.
- **CHANGELOG entries ship with the PR.** A user-visible change (`feat`, `fix`, breaking, or a `refactor`/`perf` users would notice) adds a bullet under `## [Unreleased]` in `CHANGELOG.md`, in the same commit as the code: one bullet per feature id (`**F004 — Transactions** — <summary>`), an unscoped fix/change as a plain bullet citing its issue (`closes [#N](…)`), under Keep a Changelog sections. `docs`/`chore`/`test`/`ci`/`build`/`style` and internal refactors get none. Add an `Upgrade:` sub-line when it changes how someone runs or upgrades the stack (compose, volumes, env vars, config keys, migrations). The PR number doesn't exist yet: add `([#N](…))` in a follow-up commit once the PR is open (squash-merge hides it); a release-time check fills missing ones. Version bump, renaming `[Unreleased]` and tagging are release-time (ADR 0007); the SemVer bump is read off commit types (ADR 0009), not off the section.
- `/implement-feature` builds a planned feature from `docs/features/FXXX`; `/audit-and-fix` handles cross-cutting audits and fixes (issue → branch → fix with tests → verify → docs).
- To launch the stack and drive a flow in the browser, use the `run-app` skill (throwaway login, its own compose project, torn down afterwards).

## Rules for every task

- **Every controller sits behind the login (F036, ADR 0029) in every profile; there is no dev bypass.** A new endpoint is authenticated by default; a new *unauthenticated* path is added to `SecurityConfig`'s `permitAll` list deliberately. The credential is `AUTH_USERNAME` + `AUTH_PASSWORD_HASH` (Argon2, from `cd backend && ./gradlew -q generateAuthHash`, never the plaintext), required in dev and prod; in `.env` it is **single-quoted**, because the hash contains `$` and Compose would silently expand it.
- **Free-text fields are bounded at every layer**: a domain constructor/mutator length check (beside the non-blank check), `@Size(max = ...)` beside `@NotBlank` on the request DTO, a matching `varchar(n)` column, and `maxLength` on the frontend input; limits are `domain/shared/TextFieldConstraints` constants.
- **Money columns are `numeric(19,2)`**, and every amount/cap positivity rule is backed at all three layers: DTO `@Positive`, domain constructor check, DB `CHECK`.
- **Every error response is an RFC 9457 Problem Details body** (F031, ADR 0026): `type` is derived from the throwing exception's class name (`urn:my-finances:error:<slug>`), so no exception opts in. `detail` stays permanently null (it is the field that would carry exception text or entity data). The frontend maps `type` through `PROBLEM_TYPE_MESSAGES` in `apiError.ts`; an unmapped or absent `type` falls back to "Request failed with status {status}".
- **Log lines carry ids and counts only**, never amounts, descriptions/notes, account/category/payment-method names or opening balances, in either stack (ADR 0011). Deliberate exception, not a conflict (F025, ADR 0022): the `audit_log` table holds them in its `changes`/`entity_label` columns, so `AuditRecorder` and the audit adapter never echo them to a log line.
- **A tool version is pinned in every file that names it** (`postgres`, Node, `eclipse-temurin`; full patch tags, never floating ones), so bump every sibling in the same PR; `scripts/check-versions.sh` fails on disagreement.
- **API types are generated**: after changing a controller, regenerate `frontend/src/api/generated/schema.ts` with `AUTH_PASSWORD=... npm run generate-api-types` (backend running), and again after rebasing a branch that touched controllers.
- **Date/time tests pin a non-UTC zone** (backend `-Duser.timezone=America/Sao_Paulo`, frontend `test.env.TZ`; override with `vi.stubEnv('TZ', ...)`); the machine's zone is never relied on.

## Conventions by area

Read the file when you touch the area.

- A new exception, an error body, `apiError.ts` or its message maps → `docs/conventions/errors.md`.
- Login, session, CSRF, CORS, `BIND_ADDR` or auth env vars → `docs/conventions/auth.md`.
- A log statement, log config, the request id or log reading → `docs/conventions/logging.md`.
- A new text or amount field, a limit constant or a money-precision exception → `docs/conventions/data-constraints.md`.
- Compose files (`docker-compose.prod.yml` replaces the dev Postgres container), Dockerfiles, CI, `verify.sh` prerequisites, pinned-version siblings, API type generation, time zone (`TZ`, `Clock`, date logic) → `docs/conventions/tooling.md`.

## Keeping these files useful

Add a line to a `CLAUDE.md` only for a rule easy to violate or a gotcha that would cost time to rediscover, that the code cannot show and no check enforces. Area mechanics go in `docs/conventions/<topic>.md` behind a pointer that names its trigger; review judgement in `CODING_STANDARDS.md`; a mechanical rule becomes an ArchUnit/ESLint/script check; the story (what changed, why) goes in the commit/PR body or the feature's `spec.md`. Keep the root `CLAUDE.md` near 50 lines and each stack file near 30.
