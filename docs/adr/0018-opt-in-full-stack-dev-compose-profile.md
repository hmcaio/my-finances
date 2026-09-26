# 0018. Opt-in `full` compose profile runs the whole stack in dev

Status: Accepted
Date: 2026-09-26

## Context
Starting the dev stack took three steps in three terminals: `docker compose up -d`, `./gradlew bootRun`, `npm run dev`. ADR 0006 kept the backend and frontend out of the dev compose file because of Gradle-cache, file-watch and debugging friction on Windows, and because native runs are the fastest loop for a solo project. That friction is real but manageable, and a single command that brings everything up is worth having for a fresh checkout, a machine without a JDK/Node toolchain, or a quick look at a branch.

## Decision
- `docker-compose.yml` gains `backend` and `frontend` services behind the **`full` profile**: `docker compose --profile full up -d`. Plain `docker compose up -d` still starts only Postgres and pgAdmin, so the native workflow from ADR 0006 stays the default and unchanged.
- Source is bind-mounted for hot reload. Build output, the Gradle home, the Gradle project cache and `node_modules` live in named volumes so the Windows host's copies (different OS, different native modules) never leak into the containers.
- **File watching is polled, not event-driven**: inotify events don't cross a Windows bind mount. The frontend sets Vite's `usePolling` when `VITE_USE_POLLING=true` (set only by the compose file); the backend runs `backend/dev-run.sh`, which polls `src/` and reruns `./gradlew classes` so Spring DevTools restarts the app.
- The backend container overrides the dev profile's `127.0.0.1` bind and `localhost` datasource with `SERVER_ADDRESS` and `SPRING_DATASOURCE_URL`, without a new Spring profile. Both apps publish only on host loopback (PRD S7.1); the browser still calls the API at `localhost:8080` (`.env.development`), so no CORS or proxy change.
- ADR 0006 still holds for production: `docker-compose.prod.yml` is untouched and separate.

## Consequences
- One command for the whole stack; the same command on any machine with Docker.
- The `full` profile and native processes both bind 8080/5173, so use one or the other.
- Slower than native: first start downloads Gradle and npm dependencies into the volumes, and file changes take a couple of seconds to be noticed by polling. Debugging needs the optional JDWP port (commented in the compose file).
- `docker compose down -v` also deletes the Gradle and `node_modules` volumes, so the next start re-downloads them.
