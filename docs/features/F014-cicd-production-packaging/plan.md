# F014 — Action Plan

**Depends on**: F001.

## Backend
- [x] Add `application-prod.yml` with datasource settings sourced from environment variables.
- [x] Add `backend/Dockerfile` (multi-stage: Gradle build → slim JRE runtime), `SPRING_PROFILES_ACTIVE=prod` default.
- [x] Confirm Flyway migrations run correctly against a freshly-provisioned prod-shaped Postgres (same migrations as dev, different connection target). Verified locally: `V1__baseline.sql` applied cleanly against a fresh `postgres:17-alpine` container started by `docker-compose.prod.yml`.

## Frontend
- [x] Add `.env.development` and `.env.production` (API base URL: relative dev default vs relative `/api` for the nginx-proxied prod setup).
- [x] Add `frontend/nginx.conf`: static SPA serving with fallback to `index.html`, `location /api/` reverse-proxying to the backend service.
- [x] Add `frontend/Dockerfile` (multi-stage: `npm run build` → nginx runtime, copying `dist/` and `nginx.conf`).

## Infra
- [x] Add `docker-compose.prod.yml` (postgres, backend, frontend services; images pulled by tag from GHCR, not built locally; `depends_on` with healthchecks).
- [x] Add `.env.example` (documented keys, no real secrets) and add `.env` to `.gitignore` (already gitignored — F001 added the `.env` rule preemptively with a comment pointing at F014/ADR 0007).
- [x] Decide and document final image names/tag format in the compose file's comments (`ghcr.io/hmcaio/my-finances-backend`/`-frontend`).

## CI/CD
- [x] Add `.github/workflows/ci.yml`.
- [x] `test` job (backend): Postgres service container, `./gradlew spotlessCheck test`. Runs on every push and PR. (Includes a `chmod +x gradlew` step — the wrapper script is checked in without the executable bit, since the repo was authored on Windows, which Linux runners need set explicitly. Activates an explicit `SPRING_PROFILES_ACTIVE=test`, matching `application-test.yml`, rather than implicitly riding the `dev` default.)
- [x] `test` job (frontend): `npm ci`, `npm run lint`, test. Runs on every push and PR. (No test runner exists yet in `frontend/package.json` — the step checks for a `test` script at run time via `npm run` and skips gracefully instead of hardcoding `npm test`, so it activates automatically once a test runner is added.)
- [x] `build-and-push` job: builds both Dockerfiles, logs into `ghcr.io`, pushes with SHA/`latest` tags on `main` pushes and version tags on `v*` tag pushes. Gated on both test jobs passing and on the branch/tag condition (not run on arbitrary feature-branch pushes).

## Release Process
- [x] Document the release steps (CHANGELOG update, version bump in both `build.gradle` and `package.json`, tag, push) in `CHANGELOG.md`'s header or this file — see ADR 0007. Already documented: `CHANGELOG.md`'s header points at ADR 0007 and states the tag-triggers-CI relationship; this feature's own `spec.md` (## Release Process) spells out the exact steps. No further duplication added.

## Verification
- [x] Local smoke test: `docker compose -f docker-compose.prod.yml up` (with images built locally and tagged to match `.env`'s `IMAGE_TAG`, simulating a real GHCR pull) brings up all three services and the app is reachable through nginx's exposed port, with API calls correctly proxied through to the backend. Verified for real (see report) — this surfaced and fixed two real bugs (backend `server.address` binding, healthcheck IPv6/IPv4 loopback resolution) that a paper read-through would have missed.
- [x] Confirm F001's dev workflow (`docker-compose.yml`, native `bootRun`/`npm run dev`) still works unmodified after this feature is added. Verified: `docker-compose.yml` is untouched by this feature; `./gradlew spotlessCheck test` and `npm run lint` still pass against the unmodified dev config.
- [ ] Push a commit to `main` and confirm CI publishes `:latest` and `:<sha>` images; push a `v0.0.1`-style tag and confirm CI additionally publishes that version tag. **Not verifiable from this feature branch** — requires merging to `main` first. Left open intentionally; do this after merge.
