# F014 — CI/CD & Production Packaging

## Summary
A separate, distinct production packaging/deployment path layered on top of F001's local dev setup, plus GitHub Actions CI/CD to build, test, and publish it. Infrastructure-only — no product/domain model changes, and F001's dev workflow (native `./gradlew bootRun` / `npm run dev`, Postgres-only `docker-compose.yml`) is untouched.

## Scope
- `backend/Dockerfile`: multi-stage, Gradle build → slim JRE runtime.
- `frontend/Dockerfile`: multi-stage, `npm run build` → nginx serving `dist/`, reverse-proxying API calls to the backend.
- `docker-compose.prod.yml`: three services (`postgres`, `backend`, `frontend`), pulling versioned images from GHCR (not building locally) so it can pin a specific release.
- Config separation: `application-prod.yml` Spring profile (backend), `.env.development`/`.env.production` (frontend, via Vite), gitignored `.env` for prod compose secrets/DB credentials.
- GitHub Actions: test on every push/PR; build and push Docker images to GHCR only on pushes to `main` and on version tags.
- Out of scope: any actual deployment target/hosting (the PRD explicitly keeps this local/on-demand, §7.3 — this feature produces deployable artifacts and a way to run them, it doesn't stand up a server anywhere).

## Backend

### Dockerfile (`backend/Dockerfile`)
- Multi-stage:
  - Build stage: a Gradle image, copies source, runs `./gradlew bootJar` (or `build -x test` — tests already ran in CI before this step, no need to repeat them inside the image build).
  - Runtime stage: a slim JRE base image (e.g. `eclipse-temurin:<version>-jre-alpine` or equivalent), copies only the built jar from the build stage, `ENTRYPOINT ["java", "-jar", "app.jar"]`.
- Runtime stage activates the `prod` Spring profile by default (`SPRING_PROFILES_ACTIVE=prod` env var set in the image or in `docker-compose.prod.yml`).

### `application-prod.yml`
- Same shape as F001's dev `application.yml` but sourcing datasource host/port/db/user/password from environment variables (e.g. `${DB_HOST}`, `${DB_PORT}`, `${DB_NAME}`, `${DB_USER}`, `${DB_PASSWORD}`) instead of the hardcoded local-dev values — these env vars are supplied by `docker-compose.prod.yml` from the gitignored `.env` file, never committed.
- Flyway remains enabled identically to dev — same migrations run against the prod database on startup.

## Frontend

### Dockerfile (`frontend/Dockerfile`)
- Multi-stage:
  - Build stage: Node image, `npm ci`, `npm run build` (using `.env.production` for build-time env vars — Vite inlines these at build time).
  - Runtime stage: an nginx image, copies the build stage's `dist/` into nginx's web root, copies a project-provided `nginx.conf`.
- `nginx.conf`: serves static files with SPA fallback (`try_files $uri /index.html`) and reverse-proxies `/api/` to the backend container by its Docker Compose service name (e.g. `proxy_pass http://backend:8080/`) — this makes the API same-origin from the browser's perspective, so no CORS configuration is needed in production (per the answered clarification).

### Env separation
- `.env.development`: API base URL is empty/relative or points at `http://localhost:8080` for local dev against the natively-run backend (F001) — whichever the existing dev setup already assumes; this feature doesn't change dev behavior, only documents that `.env.development` exists for symmetry with `.env.production`.
- `.env.production`: API base URL is a relative path (e.g. `/api`), since nginx proxies it same-origin — no absolute host needed.

## Infra

### `docker-compose.prod.yml`
- Three services:
  - `postgres`: same image/volume pattern as F001's dev compose, but reading credentials from `.env` instead of hardcoded dev defaults.
  - `backend`: `image: ghcr.io/hmcaio/my-finances-backend:${IMAGE_TAG}`, env vars from `.env`, depends on `postgres` (with a healthcheck-based `depends_on` condition so it doesn't start before Postgres is ready).
  - `frontend`: `image: ghcr.io/hmcaio/my-finances-frontend:${IMAGE_TAG}`, exposes the nginx port to the host, depends on `backend`.
- `docker-compose.yml` (F001's dev file, Postgres-only) is untouched — this is a wholly separate file, not a profile/override of the same one, so dev and prod workflows can't accidentally interfere.
- `.env` (gitignored): `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, `DB_HOST`/`DB_PORT` (matching the compose service name/port), `IMAGE_TAG` (which published version to run). An `.env.example` (committed, no real secrets) documents the required keys.

### Image naming & tagging
- `ghcr.io/hmcaio/my-finances-backend`, `ghcr.io/hmcaio/my-finances-frontend`.
- Every image pushed on a `main` push is tagged with the short git SHA and `latest`.
- Every image pushed on a version tag (`vX.Y.Z`) is additionally tagged with that version string, so `docker-compose.prod.yml` can pin `IMAGE_TAG=vX.Y.Z` for a reproducible deploy instead of always tracking `latest`.

### GitHub Actions (`.github/workflows/ci.yml`)
- `test` jobs, run on every push and pull request (any branch):
  - Backend: spins up a Postgres service container (matching the version used elsewhere), runs `./gradlew spotlessCheck test` against it — needed since the domain-heavy logic (F006 budget versioning, F007 recurring catch-up) has integration-level tests that hit real persistence per the TDD approach called out in the PRD (§7.2), and `spotlessCheck` (F001) gates formatting.
  - Frontend: `npm ci`, `npm run lint`, and test (whatever test runner the frontend template ends up using).
- `build-and-push` job, gated to only run on push to `main` or on a `v*` tag (per the answered clarification — every push runs tests, but images are only published for deliberate, deployable versions, not every branch commit):
  - Needs both `test` jobs to pass first.
  - Builds `backend/Dockerfile` and `frontend/Dockerfile`, logs into `ghcr.io` (using the built-in `GITHUB_TOKEN`, which has package-write permission for the repo's own GHCR namespace), pushes both images with the tags described above.

## Release Process
Per [ADR 0007](../../adr/0007-single-shared-semver-and-changelog.md): backend and frontend share one version. To cut a release: move `CHANGELOG.md`'s `[Unreleased]` entries under a new `[X.Y.Z] - YYYY-MM-DD` heading (add a fresh empty `[Unreleased]` above it), bump `version` in both `backend/build.gradle` and `frontend/package.json` to match, commit, then tag `vX.Y.Z` and push the tag — which is exactly the trigger the `build-and-push` CI job (above) watches for.

## Dependencies
F001 (dev scaffolding this builds a separate prod path on top of). Does not depend on, block, or change F002–F013 — purely infrastructure around whatever domain features exist at the time it's built.
