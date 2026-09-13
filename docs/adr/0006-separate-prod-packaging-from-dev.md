# 0006. Keep production Docker packaging separate from local dev tooling

Status: Accepted
Date: 2026-09-13

## Context
The app runs locally, on-demand, for a single user (PRD §7.3). Local dev (F001) runs Postgres in Docker but the backend and frontend natively (`./gradlew bootRun`, `npm run dev`) — containerizing them in dev adds Gradle-cache/file-watch/debugging friction, especially on Windows, for no real day-to-day benefit in a solo project. Production packaging (F014) still needs proper Docker images and a way to run the whole stack, for when the user wants a versioned, reproducible deployment rather than always running from source.

## Decision
Two entirely separate compose files: `docker-compose.yml` (dev, Postgres-only, untouched by F014) and `docker-compose.prod.yml` (three services — postgres, backend, frontend — pulling versioned images from GHCR rather than building locally). `backend/Dockerfile` and `frontend/Dockerfile` are multi-stage, built only in CI (or manually for a local smoke test), never as part of the everyday dev loop. The frontend's production nginx container reverse-proxies `/api/` to the backend container, keeping the browser same-origin in prod and avoiding CORS configuration.

## Consequences
- Dev and prod workflows can't accidentally interfere — no shared compose file, no profile/override gymnastics.
- CI/CD (GitHub Actions) only builds and publishes images on pushes to `main` and on version tags, not every branch commit, so `docker-compose.prod.yml` can pin a specific `IMAGE_TAG` for a reproducible run.
- Config divergence between dev and prod (datasource credentials, API base URL) is handled through Spring's `prod` profile and Vite's `.env.production`, not by branching application code.
