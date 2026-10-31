---
name: run-app
description: Launch my-finances in Docker (dev `full` profile, or the prod compose stack) behind a throwaway login and drive it headless with Playwright, to confirm a change works in the real app. Use to run, start or screenshot the app, or to check off a plan.md Verification item a human would click through; ends with the stack down and the scratch credential deleted.
---

# Run the app

Starts a disposable copy of the stack, drives it with `scripts/drive/driveApp.mjs`, and tears it down. The stack runs the checkout you are standing in (the dev compose file bind-mounts `./backend` and `./frontend`), so run from the branch under test. Every controller sits behind the login (F036, ADR 0029): there is no bypass, so the stack needs a credential, and this skill makes a throwaway one.

Shell state does not persist between Bash calls: write the scratch directory path down once and spell it out in each later command. Use the session scratchpad directory when the harness names one, otherwise `mktemp -d`; write it with forward slashes in Git Bash.

## 1. Precondition: this run's names and ports are free

The compose files build every pinned name and the dev host ports from `STACK_NAME` and four port variables ([`tooling.md`](../../../docs/conventions/tooling.md)), so this run uses `STACK_NAME=my-finances-drive` and its own ports and shares nothing with the developer's live stack, which stays untouched. Never set `STACK_NAME` to the default `my-finances`.

```
docker ps -a --filter name=my-finances-drive
```

(`-a`: a stopped container still holds its name.) Also confirm nothing listens on the ports this run publishes, 18080 (backend), 15173 (frontend) and 15432 (Postgres): `ss -ltn | grep -E ':(18080|15173|15432) '` (Git Bash: `netstat -ano | grep -E ':(18080|15173|15432) .*LISTENING'`).

Done when both print nothing. A row is a leftover of an earlier run (`my-finances-drive-*` containers: remove them) or a port taken by something else (pick other ports and use them below); the developer's containers (`my-finances-*` without `-drive`) are never touched.

Snapshot the volumes so teardown can prove it left none behind: `docker volume ls -q | sort > <scratch>/volumes.before`.

## 2. Scratch credential

The repo `.env` is the user's and stays unread and unwritten. The hash goes in an env file under the scratch directory:

```
PW=$(node -e "console.log(require('crypto').randomBytes(12).toString('hex'))")
printf 'STACK_NAME=my-finances-drive\nBACKEND_PORT=18080\nFRONTEND_DEV_PORT=15173\nPOSTGRES_PORT=15432\nAUTH_USERNAME=drive\n' > <scratch>/stack.env
(cd backend && AUTH_PASSWORD=$PW ./gradlew -q generateAuthHash) >> <scratch>/stack.env
printf 'AUTH_USERNAME=drive\nAUTH_PASSWORD=%s\nDRIVE_BASE_URL=http://localhost:15173\n' "$PW" > <scratch>/driver.env
```

`generateAuthHash` prints the ready-made single-quoted `AUTH_PASSWORD_HASH='$argon2...'` line on stdout (the single quotes keep Compose from expanding the `$`; `.env.example` explains). Needs a local JDK 25.

Done when `grep -cF "AUTH_PASSWORD_HASH='\$argon2id" <scratch>/stack.env` prints `1` (`-F` keeps the `$` literal: as a regex the same pattern printed `0` on a correct file).

## 3. Start the dev stack

```
docker compose -p my-finances-drive --env-file <scratch>/stack.env --profile full up -d postgres backend frontend
```

`-p` (the same value as `STACK_NAME`) gives the run its own project name, so its named volumes are separate from the developer's. `--env-file` replaces `.env` for this command. pgAdmin is left out (its port is not needed). The first start is slow: Gradle and `npm ci` run inside the containers, several minutes with fresh caches.

Wait until the backend reports healthy and Vite answers:

```
until [ "$(docker inspect -f '{{.State.Health.Status}}' my-finances-drive-backend)" = healthy ]; do sleep 5; done
until curl -sf -o /dev/null http://localhost:15173/; do sleep 3; done
```

Done when both loops exit. A backend that never turns healthy has a startup message that names what to fix: `docker logs --tail 50 my-finances-drive-backend`.

## 4. Drive

One-time setup: `cd frontend && npm ci && npx playwright install chromium`.

```
set -a; . <scratch>/driver.env; set +a
DRIVE_SHOTS_DIR=<scratch>/shots node scripts/drive/driveApp.mjs login
```

`login` is the smoke flow: signed-out redirect to `/login`, a wrong password, sign-in, logout. For the flow your change touches, write a script in `scripts/drive/` that imports the helpers from `driveApp.mjs` (`launch`, `signIn`, `expectPath`, `screenshot`, `watchPage`), or add an entry to its `flows`. The header comment lists the environment variables.

Done when the flow exits 0 and you have opened the screenshots with Read and seen the page you expected (a blank frame is a failed launch). Report what you saw per `plan.md` Verification item.

Facts about this app that save time:

- A fresh database shows the onboarding screen on first load.
- Dev serves the SPA on `localhost:<FRONTEND_DEV_PORT>` and calls the API on `localhost:<BACKEND_PORT>` cross-origin (5173 and 8080 by default; 15173 and 18080 here). A direct `fetch` from the page needs `credentials: 'include'` plus an `X-XSRF-TOKEN` header copied from the `XSRF-TOKEN` cookie (clicking through the UI sends both itself); a mutation without the header gets 403.
- Every wrong password counts toward the five-attempt lockout (429); `docker restart my-finances-drive-backend` clears it.
- Logs hold ids and counts only (ADR 0011); follow one request by its `X-Request-Id`.

## 5. Prod-stack variant

For checks that need the nginx, same-origin setup or the backup sidecar. Build the three `:local` images, create the keygen recipient and write the env file as `docs/conventions/tooling.md` (Production packaging) describes, with these differences so the run stays disposable:

- Env file in `<scratch>`, copied from `.env.example` (shape only) with the scratch `AUTH_*` values, `STACK_NAME=my-finances-drive`, `IMAGE_TAG=local`, a free `FRONTEND_PORT` (for example 18081), and `BACKUP_DIR` set to an absolute path under `<scratch>` (the backups volume is external, and `BACKUP_DIR` keeps it unmounted).
- `STACK_NAME` also moves the logs volume (`my-finances-drive-logs-prod`), so the run never writes into the developer's real logs.
- `docker compose -f docker-compose.prod.yml -p my-finances-drive-prod --env-file <scratch>/prod.env up -d`, then drive with `DRIVE_BASE_URL=http://localhost:<FRONTEND_PORT>`.
- Tear down with plain `down` (then the volume sweep in step 6), then `docker rmi` the three `ghcr.io/hmcaio/my-finances-*:local` images this run built.

## 6. Teardown (the completion criterion for the whole skill)

```
docker compose -p my-finances-drive --env-file <scratch>/stack.env --profile full down -v
```

(prod: the same with `-f docker-compose.prod.yml -p my-finances-drive-prod --env-file <scratch>/prod.env` and no `-v`.) Then remove any volume this run created that `down` left (for prod: the `my-finances-drive-logs-prod` volume and the `my-finances-drive-prod_` data volume), and run the checks:

```
docker ps -a --filter name=my-finances-drive                       # prints no rows
docker volume ls -q | sort | comm -13 <scratch>/volumes.before -   # prints nothing
```

If the stack ran from a worktree, it left root-owned `backend/logs` and `frontend/node_modules` in it: clear them as `docs/conventions/tooling.md` says before removing the worktree. Last, view any screenshots you still need, delete the scratch directory (env files, screenshots), and confirm `ls <scratch>` reports no such directory.

Done when all three checks pass. Run teardown on failure paths too: a stack left up holds ports and names for the next run.
