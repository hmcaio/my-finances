# F016 — Action Plan

**Depends on**: F001, F002, F014 (F003, F006, F007 code is touched, all already merged).

Suggested order: backend first (its request id must exist before the frontend's is worth sending), then frontend, then infra, then docs. One feature branch (`feature/f016-logging`), one commit per phase.

## Phase 0 — Decision record
- [x] Write `docs/adr/0011-logging-slf4j-request-id.md` (Context / Decision / Consequences per the ADR template) and add its row to `docs/adr/README.md`. Cover: text over JSON, stdout + capped rolling file on a named volume (and why not a log stack / DB appender / host drivers), no browser→backend shipping, no logging in `domain/`, request id contract.

## Phase 1 — Backend foundation
- [x] `application.yml`: add `logging.pattern.correlation: "[%X{requestId:-}] "`. Start the app and confirm the pattern renders (blank id at startup); if Boot 4.1 ignores the property, use `logging.pattern.console` with the default pattern plus `[%X{requestId:-}]` instead.
- [x] `application-dev.yml`: `logging.level.com.chm.myfinances: DEBUG`, `logging.file.name: logs/backend.log`. `application-prod.yml`: same level key at `INFO`, `logging.file.name: /var/log/my-finances/backend.log`.
- [x] `application.yml`: `logging.logback.rollingpolicy` — `max-file-size: 50MB`, `max-history: 90`, `total-size-cap: 300MB`. Start with `bootRun` and confirm `backend/logs/backend.log` appears and lines also still go to the console; temporarily set `max-file-size: 10KB` to watch a `.gz` archive roll, then restore it.
- [x] `build.gradle`: `tasks.named('test')` sets system property `logging.file.name` to `''`. Run `./gradlew test` from a clean tree and confirm no `backend/logs/` is created (if Boot doesn't treat the empty value as "no file", switch to an explicit non-file override).
- [x] `.gitignore`: add `backend/logs/` (the existing `*.log` rule misses the rolled `.gz` archives).
- [x] Add `testsupport/LogCapture` (Logback `ListAppender` attach/detach helper, `AutoCloseable`).
- [x] Test first: `RequestLoggingFilterTest` — id generated when absent (via `FakeIdGenerator`); valid inbound id kept; malformed inbound id (CR/LF, >64 chars, illegal chars) replaced; response header set; MDC cleared after, including when the chain throws; access line has method/path/status/duration and no query string; `/actuator/health` logs at DEBUG not INFO.
- [x] Implement `infrastructure/web/RequestLoggingFilter` (`OncePerRequestFilter`, `@Order(HIGHEST_PRECEDENCE)`, `IdGenerator` injected, id regex `^[A-Za-z0-9-]{1,64}$`).
- [x] Test first: extend `GlobalExceptionHandlerTest` — unexpected exception logs one ERROR with the throwable and the response body is unchanged (still exactly `{"message": "An unexpected error occurred"}`); `@ResponseStatus` exception logs INFO with the class simple name and **not** its message; `ErrorResponse` exception logs nothing from the handler.
- [x] Implement the logging in `GlobalExceptionHandler` (add `HttpServletRequest` parameter; keep the single-advice rule and the rethrow behaviour).
- [x] `./gradlew spotlessApply` then `./gradlew spotlessCheck test`.

## Phase 2 — Backend business events
Each: write the `LogCapture` assertion in the existing service unit test first (fakes, no Spring), then add the line. Plain `LoggerFactory.getLogger`, no Lombok `@Slf4j`, ids/counts only.
- [x] `RecurringOccurrenceCatchUpService.runCatchUp`: count generated occurrences and templates; INFO summary only when generated > 0, DEBUG otherwise. Confirm behaviour (which rows are saved, `lastGeneratedFor` advancing) is unchanged — the existing catch-up tests must pass untouched.
- [x] `AccountService.close`: INFO `Account {id} closed`.
- [x] `RecurringTemplateService.deactivateForAccount`: INFO with the number of templates deactivated (derive the count from what the method already iterates; don't add a query).
- [x] `RecurringTemplateService.confirmPending` / `dismissPending`: INFO with pending id (+ created transaction id on confirm).
- [x] `RecurringTemplateService.setCap` and `BudgetService.setCap`: INFO `new version` vs `replaced`, id and `effectiveFrom`, **no amount**.
- [x] Grep the diff for `amount`, `description`, `notes`, `name` inside log calls — none may appear. Confirm nothing was added under `domain/`.

## Phase 3 — Frontend
- [x] Test first: `src/utils/logger.test.ts` (method→console mapping, level filtering incl. `silent`, invalid env value → `warn`, `localStorage` override, throwing `localStorage` tolerated).
- [x] Implement `src/utils/logger.ts` (+ `setLogLevel`); add `VITE_LOG_LEVEL` to `.env.development` (`debug`), `.env.production` (`warn`), `.env.test` (`silent`) and to `src/vite-env.d.ts`.
- [x] ESLint: `no-console: 'error'` with an override for `src/utils/logger.ts` only. Run `npm run lint` and fix any existing `console.*` it finds (there are none today).
- [x] Test first: `src/api/client.test.ts` (MSW) — `X-Request-Id` present on the request; 409 → `warn` + still an `ApiError` with the `conflictMessage`; 500 and `HttpResponse.error()` → `error`; a distinctive response-body string never appears in the logged arguments.
- [x] Add request/response interceptors to `src/api/client.ts` (request id + timing via a `WeakMap`; re-reject the original error unchanged). Confirm every existing `src/api/*.test.ts` and page test still passes with zero edits.
- [x] Test first: `src/utils/globalErrorLogging.test.ts` (`error` + `unhandledrejection` reach `logger.error`; uninstall removes them).
- [x] Implement `src/utils/globalErrorLogging.ts`; call `installGlobalErrorLogging()` and pass `onUncaughtError` / `onCaughtError` / `onRecoverableError` to `createRoot` in `src/main.tsx`.
- [x] Test first: `src/components/ErrorBoundary.test.tsx` (fallback renders, sibling nav survives, Reload button, `key` change resets).
- [x] Implement `ErrorBoundary` and wrap `<Routes>` in `App.tsx` (inside `Layout`, keyed on `location.pathname`).
- [x] `npm run lint`, `npm test`, `npm run build`, `npm run format:check` (Prettier only on touched files). (`format:check` over the whole tree already flagged `frontend/CLAUDE.md` before this feature; every file touched here is clean.)

## Phase 4 — Infra
- [x] `frontend/nginx.conf`: `map` for the request id, `proxy_set_header X-Request-Id`, a `log_format` including it, `access_log /dev/stdout <format>`.
- [x] `backend/Dockerfile` runtime stage: `mkdir -p /var/log/my-finances && chown spring:spring /var/log/my-finances` before `USER spring`.
- [x] `docker-compose.prod.yml`: `x-logging` anchor (`json-file`, `10m` × 3) on all services (rotates stdout); new named volume `my-finances-logs-prod` (with an explicit `name:` so compose doesn't prefix it with the project name) mounted at `/var/log/my-finances` on `backend`; backend `LOGGING_LEVEL_COM_CHM_MYFINANCES: ${LOG_LEVEL:-INFO}`.
- [x] `.env.example`: document optional `LOG_LEVEL`.
- [x] Dev `docker-compose.yml` left untouched (ADR 0006) — confirm with `git diff --stat`.

## Phase 5 — Docs
- [ ] Backend `CLAUDE.md`: "Logging" section (levels per profile, file location per profile + the two prod read commands from the spec, retention caps and what deletes the volume, test-run file suppression, `requestId` MDC, what never to log, never in `domain/`, never bind-parameter logging, `LogCapture`, Postgres-row-in-stack-trace caveat).
- [ ] Frontend `CLAUDE.md`: "Logging" section (`logger` not `console`, level env + `localStorage` override, the interceptor owns HTTP logging, never log bodies, the `ErrorBoundary`).
- [ ] Root `CLAUDE.md`: cross-stack bullet for the `X-Request-Id` contract and the "no amounts/descriptions in logs" rule.
- [ ] `docs/features/README.md` row for F016; `CHANGELOG.md` `[Unreleased]` entry.

## Verification
- [ ] `./gradlew spotlessCheck test` (Docker running) and `npm run lint && npm test && npm run build` all green.
- [ ] Manual, dev: `curl -H 'X-Request-Id: abc-123' localhost:8080/api/categories` → backend log line carries `[abc-123]`; response has the `X-Request-Id` header; `curl -H $'X-Request-Id: bad\r\nX-Injected: 1' …` gets a *generated* id instead.
- [ ] Manual, dev: force a backend 500 (e.g. temporarily throw in a controller) → full stack trace in the backend log with a `requestId`; HTTP response body is still only `{"message": "An unexpected error occurred"}`. Revert the throw.
- [ ] Manual, dev: trigger a 409 (delete a category that a transaction uses) → INFO line with the exception class name and no message; browser console shows one `warn` with the same request id (search the backend log for it).
- [ ] Manual, dev: stop the backend, load a list page → browser console `error` with status `0`; "Could not load data" row + Retry behave as before.
- [ ] Manual, dev: throw from a page component → fallback appears with the nav intact, `logger.error` shows the component stack, navigating away clears it.
- [ ] Manual, prod smoke (root `CLAUDE.md` recipe): `docker compose -f docker-compose.prod.yml up -d`, use the app, `docker compose -f docker-compose.prod.yml logs backend frontend` shows the same request id in nginx and backend lines; `docker inspect` shows the `json-file` rotation options; `LOG_LEVEL=DEBUG` in `.env` turns on DEBUG lines after a recreate.
- [ ] Manual, prod smoke — persistence: after using the app, `docker compose -f docker-compose.prod.yml down` (no `-v`), `up -d` again, then read `/var/log/my-finances/backend.log` via the spec's `docker run --rm -v my-finances-logs-prod:/logs alpine …` command — the lines from before the `down` are still there, and the backend started without a permission error writing the file (proves the Dockerfile `chown` + named-volume ownership inheritance). `down -v` removes them (expected; note it in `CLAUDE.md`).
- [ ] No log line anywhere in the above contained an amount, description, note or entity name.
