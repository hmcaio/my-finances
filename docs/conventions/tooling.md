# Running, verifying and shipping the stack

Read when changing compose files, Dockerfiles, CI, a pinned tool version, the verify script, or when running the prod-shaped stack locally. The commands themselves live in `scripts/verify.sh` (stages and their prerequisites are in its header) and `.github/workflows/ci.yml`; this file keeps only what those don't say.

## Dev stack

- The `full` profile is the default dev path because its toolchain versions are the pinned ones CI and prod use; native `bootRun`/`npm run dev` is the fast-loop alternative. They both bind 8080/5173, so use one or the other. `docker compose --profile full down` stops the app containers too (plain `down` leaves them running).
- pgAdmin login, the pre-registered server, and the "editing `servers.json` needs `docker compose down -v`" caveat are commented in `docker-compose.yml`.
- Port exposure (`BIND_ADDR`): [auth.md](auth.md).

## Verify and CI

- `scripts/verify.sh` is the one definition of the checks: CI's jobs call it, so a green local run means a green CI run. Change a check in that script, not only in `ci.yml`. It runs every stage even if one fails.
- `backup` stage: needs `bats` (bats-core) on `PATH`. CI installs it with `apt-get install bats`; locally, run it via a one-off Docker image with `bash`/`coreutils`/`jq` added to `bats/bats`, or install bats-core yourself. `backup/test/e2e.sh` (the stage's second half) builds the real sidecar image and runs it against throwaway Postgres containers of its own: safe to re-run, cleans up after itself.
- CI builds and pushes all three images to GHCR on `main` and `vX.Y.Z` tags only (`build-and-push` job in `ci.yml`). It triggers on PRs, `main` pushes and `v*` tag pushes, so a feature branch is built once, by its PR.
- Docs-only PRs (every changed path under `docs/` or `.claude/`, or a `*.md`) skip the test jobs through the `changes` job in `ci.yml`: they start, are skipped by `if:`, and a skipped job counts as passed for a required check. Never use `[skip ci]` or `paths-ignore` for this: the workflow would not start, the required checks would stay "Expected" and the PR could not merge. A new test job must also get `needs: changes` and the same `if:`.
- Branch and tag protection is code: `.github/rulesets/*.json` (ADR 0008), applied with `scripts/apply-rulesets.sh` (needs `gh` with admin rights; upserts by name = file basename). Edit the file, then re-run the script; don't change a ruleset only in the GitHub UI. The required status checks are the job `name:` values in `ci.yml`, so renaming a job there means updating `protect-main.json` and `protect-develop.json` in the same PR, or every merge blocks.

## Pinned versions

A tool version is pinned in every file that names it, and `scripts/check-versions.sh` (the `versions` stage, CI's first backend step) fails if they disagree. Images use full patch tags (Dependabot bumps them), never floating `:24-alpine`-style tags, so bump every sibling in the same PR.

- `postgres`: both compose files + `TestcontainersConfiguration` + `backup/Dockerfile` (F018: its `pg_dump`/`pg_restore` must match the server major, and the pinning rule applies to it exactly as to the others).
- Node: `frontend/.nvmrc` is the source; `frontend/Dockerfile`, the dev compose `frontend` service, CI's `setup-node` and `engines` follow it.
- `eclipse-temurin` JDK/JRE: `backend/Dockerfile` + dev compose `backend`.

## Production packaging (F014, ADR 0006)

`docker-compose.prod.yml` is wholly separate from the dev loop.

- Local smoke test: build `ghcr.io/hmcaio/my-finances-{backend,frontend,backup}:local` from `backend`/`frontend`/`backup`, `cp .env.example .env` with `IMAGE_TAG=local` and a real `BACKUP_AGE_RECIPIENT` (`docker compose -f docker-compose.prod.yml run --rm --no-deps backup backup-keygen`), then `docker compose -f docker-compose.prod.yml up -d` / `down -v`. The auth variables it also needs: [auth.md](auth.md).
- Both compose files default to the same project name and both have a `postgres` service, so prod `up`/`down` replaces/removes the *dev* Postgres container (its data volume survives; `docker compose up -d` brings dev back).
- The prod stack's fourth service, `backup` (F018, ADR 0015), writes into `my-finances-backups-prod`, an `external: true` volume `down -v` does **not** remove. Setup/restore: the README's Backups section; full design: `docs/features/F018-backups/spec.md`.

## API types and time zone

- **API types are generated**: `AUTH_PASSWORD=... npm run generate-api-types` (backend running) regenerates `frontend/src/api/generated/schema.ts` from `/v3/api-docs`. Operation ids are pinned to `<controller>_<method>` by `OpenApiConfig` because springdoc's default positional `delete_8` ids collide silently when two PRs each add a controller method and git merges the regenerated file without a conflict; `ArchitectureTest` enforces unique handler names, and the schema is regenerated again after rebasing a branch that touched controllers. CI runs `npm run build`, so a broken generated file fails the PR. When an aggregate introduces a new `java.time` type, check its generated schema shape: springdoc mis-maps `YearMonth` as an object without `OpenApiConfig`'s `replaceWithClass(YearMonth.class, String.class)`.
- **Time zone is explicit**: the backend's "today" is the JVM default zone (`Clock.systemDefaultZone()`), set in containers from `TZ` (`.env`, default `UTC`); backend tests run with `-Duser.timezone=America/Sao_Paulo` (`build.gradle`) and frontend tests with `test.env.TZ` (`vite.config.ts`), so a UTC-vs-local bug fails in CI. A test that needs another zone overrides it (`vi.stubEnv('TZ', ...)`).
