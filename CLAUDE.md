# CLAUDE.md

Guidance for Claude Code in this repository. This file holds what applies to both stacks. Backend-specific rules live in [backend/CLAUDE.md](backend/CLAUDE.md) and frontend-specific ones in [frontend/CLAUDE.md](frontend/CLAUDE.md); they load automatically when you work in those directories.

## Source of truth

**Read [docs/PRD.md](docs/PRD.md) before making any architectural or scope decision.** It is the authoritative spec — data model (entities, versioning rules, net worth formula), functional requirements, tech stack, explicit non-goals. Do not re-derive product decisions from first principles; the PRD already resolved many non-obvious tradeoffs (why credit card spend is a liability-increasing expense and payments are transfers, why budgets/recurring templates are versioned instead of mutated in place, why recurring-occurrence generation is lazy/catch-up rather than a scheduler).

- `docs/adr/` — architectural decisions (index: `docs/adr/README.md`).
- `docs/features/FXXX-*/{spec,plan}.md` — one folder per feature. Which are built vs. only specified is tracked by each `plan.md` checklist and the README's "Project status" — not here, so it can't go stale.
- Why something changed (fixes, audits, migration renumbering) lives in `git log`, PR descriptions and the relevant `spec.md` — not in this file.

## Project shape

Local, single-user, no auth, bound to `localhost`, run on-demand rather than always-on — which is why recurring-template generation is catch-up-based, not cron-based. Java/Spring Boot + PostgreSQL/Flyway backend, React/TypeScript/Vite/MUI frontend. Domain-Driven Design, Hexagonal Architecture and test-first for the rules-heavy logic (ADR 0004).

## Commands

```
docker compose up -d      # local Postgres (+ pgAdmin at http://localhost:5050); data persists in a named volume
docker compose down
```
pgAdmin login, the pre-registered server, and the "editing `servers.json` needs `docker compose down -v`" caveat are commented in `docker-compose.yml`. Backend and frontend commands are in their own `CLAUDE.md` files.

Production packaging (F014) is a wholly separate `docker-compose.prod.yml` (ADR 0006), not part of the dev loop. Local smoke test: build `ghcr.io/hmcaio/my-finances-{backend,frontend}:local` from `backend`/`frontend`, `cp .env.example .env` with `IMAGE_TAG=local`, then `docker compose -f docker-compose.prod.yml up -d` / `down -v`. Both compose files default to the same project name and both have a `postgres` service, so prod `up`/`down` replaces/removes the *dev* Postgres container (its data volume survives; `docker compose up -d` brings dev back).

CI (`.github/workflows/ci.yml`) runs backend `spotlessCheck test` and frontend `npm ci && npm run lint && npm test` on every push/PR, and builds+pushes both images to GHCR on `main` and `vX.Y.Z` tags.

## Workflow

- Conventional Commits (ADR 0009). Branch off `develop` and PR into `develop` (ADR 0008) — never commit to `develop` or `main` directly; don't push or open a PR unless asked.
- **CHANGELOG entries ship with the PR.** A PR with a user-visible change (`feat`, `fix`, breaking, or a `refactor`/`perf` users would notice) adds its bullet under `## [Unreleased]` in `CHANGELOG.md`, in the same commit as the code. One bullet per feature id (`**F004 — Transactions** — <summary>`), an unscoped fix/change as a plain bullet citing its issue (`closes [#N](…)`), sorted under Keep a Changelog sections. No entry for `docs`/`chore`/`test`/`ci`/`build`/`style` or internal refactors. Add an `Upgrade:` sub-line for anything that changes how someone runs or upgrades the stack (compose, volumes, env vars, config keys, migrations). The PR's own number doesn't exist yet when the entry is written: add `([#N](…))` in a follow-up commit once the PR is open (squash-merge hides the extra commit); a release-time check fills any that are missing. Bumping `version`, renaming `[Unreleased]` and tagging stay release-time (ADR 0007), and the SemVer bump is read off commit types (ADR 0009), not off the section an entry sits in.
- `/implement-feature` builds a planned feature from `docs/features/FXXX`; `/audit-and-fix` handles cross-cutting audits and fixes (issue → branch → fix with tests → verify → docs).

## Cross-stack conventions

- **Free-text fields are bounded at every layer**: a length check in the domain constructor/mutator (next to the non-blank check for mandatory fields), `@Size(max = ...)` next to `@NotBlank` on request DTOs, and a matching `varchar(n)` column (frontend description/notes inputs also set `maxLength`). All limits come from `domain/shared/TextFieldConstraints` — reuse its constants: `MAX_NAME_LENGTH` (100) for flat-taxonomy names (`Category`, `PaymentMethod`, `Account`, `Institution`, and future ones like F008's `InvestmentCategory`), `MAX_DESCRIPTION_LENGTH` (150, mandatory) / `MAX_ADDITIONAL_NOTES_LENGTH` (500, optional) for the description/notes pair on `Transaction`, `Transfer` and `RecurringTemplate`.
- **Money columns are `numeric(19,2)`** (`transactions.amount`, `accounts.opening_balance`, `budget_versions.monthly_cap`, ...). Reuse that precision for any new amount, and back every amount/cap positivity rule at all three layers: DTO `@Positive`, domain constructor check, DB `CHECK`.
- **The backend never sends exception text** (`spring.web.error.include-message: never`), so every expected `@ResponseStatus(CONFLICT)` case needs an explicit `conflictMessage` at the frontend API-client call site or the user just sees "Request failed with status 409".
- **API types are generated**: `npm run generate-api-types` (backend running) regenerates `frontend/src/api/generated/schema.ts` from `/v3/api-docs`. When an aggregate introduces a new `java.time` type, check its generated schema shape — springdoc mis-maps `YearMonth` as an object without `OpenApiConfig`'s `replaceWithClass(YearMonth.class, String.class)`.

- **Request id and logging**: the frontend sends `X-Request-Id` (`crypto.randomUUID()`) on every Axios request, nginx fills one in if it's missing and forwards it, and the backend accepts it only if it matches `^[A-Za-z0-9-]{1,64}$` (else generates its own), puts it in the MDC and echoes it back — so one failing click can be followed browser console → nginx → backend log. Logs everywhere carry ids and counts only: **never amounts, descriptions/notes or entity names**, in either stack (ADR 0011; details in each stack's `CLAUDE.md`).

## Keeping these files useful

Add a bullet only for a rule or gotcha that can't be derived from the code and would cost time to rediscover. Put the story (what changed, why, what was decided) in the commit/PR body or the feature's `spec.md`. Put a rule in the file for the stack it applies to, or here if it spans both.
