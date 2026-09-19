# F016 — Logging

## Summary
Adds deliberate, consistent logging to both stacks. Today neither has any: the backend has zero log statements (only Spring/Hibernate/Flyway's own output), and `GlobalExceptionHandler` turns every unexpected exception into a generic 500 **without recording it anywhere** — so a real bug leaves no trace at all. The frontend has no `console.*` calls, no error boundary and no global error hooks, so an uncaught render error blanks the page silently.

The app is local, single-user and run on demand (PRD §7.3), so this is sized accordingly: **SLF4J/Logback on the backend, a thin logger over `console` on the frontend, a request id that ties the two together. No log aggregator, no JSON/structured output, no remote error reporting, no new runtime dependencies.** Infrastructure/cross-cutting only — no product or data-model changes.

## Scope
- Backend: log config per profile, a request-scoped id (MDC) with one access-log line per request, logging of unexpected and expected errors in `GlobalExceptionHandler`, a small set of business-event log lines in application services.
- Frontend: a single `logger` (the only place allowed to call `console.*`), Axios interceptors that log failed requests and send a request id, global + React error capture, an error boundary so a render crash doesn't blank the shell.
- Persistence: backend logs are also written to a size- and age-capped rolling file — `backend/logs/` in dev, a named Docker volume in prod — so they survive a terminal closing, `docker compose down` and image updates.
- Infra: nginx forwards/generates the request id and logs it; production compose gets stdout log rotation, the logs volume and a `LOG_LEVEL` knob.
- Docs: ADR 0011, `CLAUDE.md` rules per stack, CHANGELOG.
- Out of scope: see [Non-goals](#non-goals).

## Decisions

| Decision | Why |
|---|---|
| Plain SLF4J + Logback (Spring Boot's default, already on the classpath via `spring-boot-starter-web`). **No new dependency**, no `logback-spring.xml` unless a need appears. | Boot's `logging.*` properties cover levels, pattern and correlation. A hand-written XML file is one more thing to drift between profiles. |
| **Human-readable text in every profile**, not JSON. | Nothing consumes structured logs here — you read `docker compose logs` yourself. JSON is worse for that. If a collector is ever added, it's one property (`logging.structured.format.console`) plus dropping the correlation pattern. |
| Backend logs to **stdout and a rolling file**, configured entirely with Boot's `logging.file.name` + `logging.logback.rollingpolicy.*` properties (no XML). Caps: 50MB per file, 90 days, 300MB total, gzip archives. Prod writes the file to a **named Docker volume**; Docker's `json-file` driver still rotates stdout (10m × 3). | The app is run on demand and torn down between sessions. Docker's own logs die with the container (`docker compose down`, or a recreate for a new `IMAGE_TAG`), so a bug seen last week would be gone. A volume outlives the container; the caps keep a laptop from filling up. stdout is kept so `docker compose logs` still works. Rejected: a log stack (Loki/Grafana — too heavy and wants JSON), a DB appender (couples logs to the app DB, so a DB outage loses the log that explains it), host log drivers (`journald`/`syslog` — meaningless on Docker Desktop for Windows/Mac). |
| A **named volume** for the prod logs, not a bind mount. | The image's `/var/log/my-finances` is created owned by the non-root `spring` user and a new named volume inherits that ownership; a bind mount would not (UID mismatch on Linux). Trade-off: you read the files through Docker rather than straight from the host (see [Reading the logs](#reading-the-logs)). |
| nginx's access log stays stdout-only (rotated, not persisted). | Every request already gets an access line in the backend file with the same request id, so the persisted record is complete without a second volume. |
| A per-request id: accept a valid inbound `X-Request-Id`, otherwise generate one; put it in the SLF4J MDC as `requestId`, echo it in the response header. The frontend generates one per Axios request; nginx generates one if the browser didn't. | Lets one failing click be followed browser console → nginx → backend log. Cheap and the only "correlation" this app needs. |
| Request ids come from the existing `IdGenerator` port, not a fresh `UUID.randomUUID()`. | Backend `CLAUDE.md`/ADR 0005: ids never come from ad hoc `UUID.randomUUID()`. A request id isn't an entity id, but reusing the port keeps that rule greppable and the filter testable with `FakeIdGenerator`. |
| Inbound request id is validated (`^[A-Za-z0-9-]{1,64}$`); anything else is replaced. | It goes into log lines and a response header — an unvalidated value is a log-forging / header-injection vector. |
| **No logging in `domain/`.** Application services use plain `LoggerFactory.getLogger(X.class)` (a `private static final Logger log`), not Lombok `@Slf4j`. | ADR 0004 keeps the domain framework-free; F001's Lombok policy only permits Lombok on infra entities/DTOs, and this avoids widening it for a one-liner. |
| Application services log only **state-changing events with real information** (cascades, generated data, closures, new versions) at INFO. Plain CRUD gets no service-level line. | The access log line already says `POST /api/categories -> 201`. Per-method "creating category…" lines are noise that rots. |
| **What we author never logs money amounts, descriptions, notes or entity names — ids and counts only.** | Logs are a second copy of the data if you paste them into an issue. See the caveat in [What we log](#what-we-log-and-what-we-never-do) about Postgres echoing rows in constraint errors. |
| Frontend logger is `src/utils/logger.ts`; ESLint `no-console` (error) everywhere except that file. | One choke point for level control and a future sink; keeps stray `console.log` out of PRs. |
| Frontend does **not** ship logs to the backend (no `POST /api/client-logs`). | A new write endpoint on an unauthenticated API, needing rate limiting and log-forging sanitizing, to solve a problem a local single user doesn't have — they can open devtools. Revisit if the app ever runs somewhere the browser console isn't reachable. |

## Backend

### Configuration
- `application.yml` (identical in every environment, per backend `CLAUDE.md`): `logging.pattern.correlation: "[%X{requestId:-}] "` so every line carries the request id (blank outside a request, e.g. startup). Verify this Boot 4.1 property name against the running app — the fallback is `logging.pattern.console`.
- `application.yml` also holds the rolling policy, which is identical everywhere: `logging.logback.rollingpolicy.max-file-size: 50MB`, `max-history: 90` (days), `total-size-cap: 300MB`, and the default gzip archive naming. It only takes effect where a file name is set.
- `application-dev.yml`: `logging.level.com.chm.myfinances: DEBUG`, `logging.file.name: logs/backend.log` (relative to the working directory, i.e. `backend/logs/` under `./gradlew bootRun`).
- `application-prod.yml`: `logging.level.com.chm.myfinances: INFO`, `logging.file.name: /var/log/my-finances/backend.log`. The level is overridable without a rebuild through Boot's relaxed binding env var `LOGGING_LEVEL_COM_CHM_MYFINANCES` (wired to `LOG_LEVEL` in compose, see [Infra](#infra)).
- Each env-specific value stays out of the base file (backend `CLAUDE.md` config rule), so a profile can't silently inherit the other's path.
- **Tests must not write log files.** `spring.profiles.default: dev` means an un-profiled test run would pick up dev's `logging.file.name`. `build.gradle`'s `tasks.named('test')` sets the system property `logging.file.name` to an empty string (system properties outrank config files, and Boot treats an empty name as "no file"). Verify a fresh `./gradlew test` creates no `backend/logs/`.
- `.gitignore`: add `backend/logs/`. The existing `*.log` rule does **not** cover rolled archives (`backend.log.2026-09-19.0.gz`).

#### Reading the logs
- Dev: `backend/logs/backend.log` (and stdout as before).
- Prod: the file lives in the `my-finances-logs-prod` volume. Read it without the app running: `docker run --rm -v my-finances-logs-prod:/logs alpine sh -c 'ls /logs; tail -n 200 /logs/backend.log'`; with it running: `docker compose -f docker-compose.prod.yml exec backend tail -n 200 /var/log/my-finances/backend.log`. Both go in backend `CLAUDE.md`.
- Persistence boundaries, stated so nobody is surprised: logs survive `stop`/`down`/image updates; they are removed by `docker compose down -v` (which also wipes the database) or `docker volume rm`; older than 90 days or beyond 300MB total are deleted by design (whichever limit is hit first — the size cap can evict logs younger than 90 days).
- SQL: `spring.jpa.show-sql` stays off. To debug queries, set `LOGGING_LEVEL_ORG_HIBERNATE_SQL=DEBUG` ad hoc. **Never** enable `org.hibernate.orm.jdbc.bind` (TRACE) — it prints bound parameters, i.e. amounts and descriptions. State this in backend `CLAUDE.md`.
- No `application-test.yml` is added (backend `CLAUDE.md`: tests get config from Testcontainers); test log noise stays at Boot's default.

### Request logging filter
`infrastructure/web/RequestLoggingFilter` — a `OncePerRequestFilter` `@Component` at `Ordered.HIGHEST_PRECEDENCE` so the MDC is set before anything else logs.
- Take `X-Request-Id` if present and valid; else `IdGenerator.newId().toString()`.
- `MDC.put("requestId", id)`, set the `X-Request-Id` response header, `MDC.remove` in `finally`.
- After the chain: one line, **method + path (no query string) + status + duration ms**, e.g. `GET /api/transactions -> 200 (12 ms)`. INFO normally; **DEBUG for `/actuator/**`** (the prod compose healthcheck hits it every 5s).
- No request/response bodies, ever. No query string (filters may carry ids/dates; the path is enough to correlate).
- CORS: the frontend's `X-Request-Id` request header triggers a preflight in dev (5173 → 8080); `WebConfig` already has `allowedHeaders("*")`, so no change. The frontend never *reads* the response header, so `exposedHeaders` is not needed.

### `GlobalExceptionHandler`
The gap this feature exists to close. Add a logger and take the `HttpServletRequest` as a parameter:
- Unexpected exception (the generic-500 branch): `log.error("Unhandled exception for {} {}", method, path, exception)` — full stack trace, before returning the unchanged generic body. The **response** contract does not change (no message/class name to the client).
- `@ResponseStatus` exceptions (the rethrown, expected ones): before rethrowing, `log.info("{} on {} {}", exception.getClass().getSimpleName(), method, path)` — class name only, **not the message**. The backend sends no body text for these (root `CLAUDE.md`), so the exception class in the log is the only place to see *which* 409 happened.
- Spring's own `ErrorResponse` exceptions (unmapped route, wrong method): not logged here; the access line's status is enough.
- Stays the single `@RestControllerAdvice` (backend `CLAUDE.md`).

### What we log, and what we never do
INFO business events in application services — ids and counts only:

| Where | Line |
|---|---|
| `RecurringOccurrenceCatchUpService.runCatchUp` | INFO `Recurring catch-up generated {n} pending occurrence(s) across {m} template(s)` **only when n > 0**; otherwise DEBUG (it runs before every pending-list request, so an INFO line each time is noise). |
| `AccountService.close` | INFO `Account {id} closed` (the cascade into template deactivation is the interesting part; also logged below). |
| `RecurringTemplateService.deactivateForAccount` | INFO `Deactivated {n} template(s) for closed account {id}`. |
| `RecurringTemplateService.confirmPending` / `dismissPending` | INFO `Pending occurrence {id} confirmed as transaction {txId}` / `dismissed`. |
| `RecurringTemplateService.setCap`, `BudgetService.setCap` | INFO `Template/Budget {id}: new version effective {yearMonth}` (or `replaced`, matching the existing in-place-replace rule) — no amount. |

Never log: amounts, `description`/`additionalNotes`, account/category/payment-method **names**, opening balances. Never log inside `domain/`. Never log in a loop over a whole table.

**Caveat, documented rather than solved:** the full stack trace at ERROR includes the exception message, and Postgres constraint-violation messages can echo the failing row (`Failing row contains (...)`). That is accepted — the logs live on the same machine as the database and the trace is what makes the bug fixable — but the file now keeps those traces for up to 90 days, so redact before pasting a log into a public issue. This is a note in backend `CLAUDE.md`, not code.

### Testing
- New test helper `testsupport/LogCapture`: attaches a Logback `ListAppender` to a given logger for the test's duration (Logback is already present via `spring-boot-starter-logging`), so unit tests assert on level + formatted message without a Spring context and independent of the configured level. (Boot's `OutputCaptureExtension` is only for the `@SpringBootTest` case and can't see DEBUG under default config.)
- `RequestLoggingFilterTest` (plain unit test, `MockHttpServletRequest/Response`, `FakeIdGenerator`): generates an id when absent; keeps a valid inbound id; **replaces** a malformed one (CR/LF, over-long, illegal characters); sets the response header; MDC is cleared afterwards even when the chain throws; access line has method/path/status but **not** the query string; `/actuator/health` logs at DEBUG.
- Extend `GlobalExceptionHandlerTest`: unexpected exception → one ERROR event carrying the throwable **and** the response body is still exactly the generic message (the no-leak invariant must not regress); `@ResponseStatus` exception → INFO with the class name and **without** the exception message; `ErrorResponse` exception → nothing logged by the handler.
- One assertion per business event above using `LogCapture` in the existing service unit tests (fakes, no Spring). For the catch-up service: INFO when it generated something, no INFO when it generated nothing.

## Frontend

### `logger` (`src/utils/logger.ts`)
Framework-free, so it lives in `src/utils` per the layout in frontend `CLAUDE.md`.
- API: `logger.debug|info|warn|error(message: string, ...context: unknown[])`. Prefix `[my-finances]`; delegates to the matching `console` method so devtools keep their stack traces and object inspection. Passing an `Error` as context is the normal way to log one.
- Level: `VITE_LOG_LEVEL` (`debug | info | warn | error | silent`) — `.env.development` `debug`, `.env.production` `warn`, `.env.test` `silent`. Typed in `src/vite-env.d.ts`. Unknown value → `warn`.
- Runtime override for the prod build (where the level is baked in): `localStorage['logLevel']` wins if set and valid — read inside `try/catch` (storage can throw/be empty), same defensiveness as the colour-mode hook. Also exports `setLogLevel(level)` for tests.
- ESLint: `no-console: 'error'`, with a file override turning it off for `src/utils/logger.ts` only.

### HTTP logging (`src/api/client.ts`)
Axios interceptors on the shared `apiClient` — the single place, so **no page/hook call site changes**:
- Request: set `X-Request-Id` to `crypto.randomUUID()` (stash it + start time on the request config, e.g. a `WeakMap`, not a mutated public field).
- Response success: `logger.debug('GET /categories 200 (12 ms)', { requestId })`.
- Response error: `logger.warn` for 4xx (expected conflicts/validation land here), `logger.error` for 5xx and for no-response failures (network down/CORS — status `0`, matching `ApiError`). Include method, relative URL (no base URL, no query string, no params), status, duration, request id.
- **Never log request or response bodies** — they are amounts and descriptions.
- The error interceptor **re-rejects the original error untouched**, so `unwrap()`/`ApiError`/`conflictMessage` behave exactly as today.

### Uncaught errors
- `src/main.tsx`: pass React 19's `createRoot` options `onUncaughtError`, `onCaughtError`, `onRecoverableError` → `logger.error` (with the component stack).
- `src/utils/globalErrorLogging.ts` (`installGlobalErrorLogging()`, called once from `main.tsx`): `window` `error` and `unhandledrejection` listeners → `logger.error`. Returns an uninstall function (for tests).
- `src/components/ErrorBoundary.tsx` (class component — React still requires one) wrapping the `<Routes>` **inside** `Layout` in `App.tsx`, so the nav/theme survive a page crash. Fallback: MUI alert "Something went wrong on this page" + a "Reload" button. It gets `key={location.pathname}` (via a tiny wrapper using `useLocation`) so navigating away resets it. It doesn't log itself: `createRoot`'s `onCaughtError` receives every error a boundary catches. This boundary is the one non-logging addition — without it there is nothing recoverable to log *around*, and the current behaviour is a blank page.

### Testing
- `logger.test.ts`: each method hits the right `console` method; level filtering incl. `silent`; invalid env value falls back; `localStorage` override wins, and a throwing `localStorage` doesn't break it. Spy with `vi.spyOn(console, …)`, restore in cleanup.
- `client.test.ts` (MSW): the request carries an `X-Request-Id` (a handler reads it); a 409 logs `warn` and still rejects as `ApiError` with the call-site `conflictMessage` (existing behaviour intact); a 500 and a `HttpResponse.error()` log `error`; the logged payload contains no body content (assert a distinctive body string is absent).
- `globalErrorLogging.test.ts`: dispatched `error` / `unhandledrejection` events reach `logger.error`; uninstall removes the listeners.
- `ErrorBoundary.test.tsx`: a throwing child shows the fallback and the sibling nav survives; "Reload" is present; changing `key` resets it. Silence React's own caught-error `console.error` in the test (`vi.spyOn(console, 'error')`).
- Note (frontend `CLAUDE.md`): run `npm run build`, not just `npm test` — test files have no Node types.

## Infra

### nginx (`frontend/nginx.conf`)
- At the top of the file (it's included in nginx's `http` context via `conf.d`): `map $http_x_request_id $req_id { default $http_x_request_id; "" $request_id; }` — keep the browser's id when present, else use nginx's own.
- In `location /api/`: `proxy_set_header X-Request-Id $req_id;`.
- A `log_format` that includes `$req_id`, applied with `access_log /dev/stdout <format>;` in the server block.

### `docker-compose.prod.yml` / `.env.example`
- A shared `x-logging` anchor (`driver: json-file`, `max-size: "10m"`, `max-file: "3"`) applied to all three services — this rotates *stdout* (what `docker compose logs` shows); the persisted copy is the backend's file below.
- New named volume `my-finances-logs-prod`, mounted at `/var/log/my-finances` on the `backend` service (declared under `volumes:` next to `my-finances-postgres-prod-data`).
- `backend/Dockerfile` runtime stage: `RUN mkdir -p /var/log/my-finances && chown spring:spring /var/log/my-finances` **before** `USER spring`, so the app can write there and a fresh named volume inherits that ownership.
- Backend `environment`: `LOGGING_LEVEL_COM_CHM_MYFINANCES: ${LOG_LEVEL:-INFO}`.
- `.env.example`: document optional `LOG_LEVEL` (`INFO` default; `DEBUG` when chasing something).
- Dev `docker-compose.yml` is untouched (ADR 0006).

## Docs
- **ADR 0011** — "Logging: SLF4J/Logback + a request id; no aggregator" (records the Decisions table's non-obvious choices: text over JSON, stdout + capped rolling file on a named volume rather than a log stack, no log shipping from the browser, no logging in the domain).
- Backend `CLAUDE.md`: a "Logging" section — levels per profile, where the file lives per profile and the two commands for reading the prod volume, the retention caps, the test-run file suppression, MDC `requestId`, never log amounts/descriptions/names, never in `domain/`, never enable bind-parameter logging, `LogCapture` for tests, the Postgres-row caveat.
- Frontend `CLAUDE.md`: "Logging" section — use `logger`, never `console.*`, the level env var + `localStorage` override, the interceptor owns HTTP logging (don't log at call sites), never log bodies.
- Root `CLAUDE.md`: one cross-stack bullet — the `X-Request-Id` contract (frontend sends per request, nginx fills if missing, backend validates and echoes) and the shared "no amounts/descriptions in logs" rule.
- `docs/features/README.md`: F016 row. `CHANGELOG.md`: `[Unreleased]` entry (`feat`).

## Non-goals
- Log aggregation, dashboards, alerting, tracing/OpenTelemetry, metrics.
- JSON/structured log output (revisit only if something starts consuming it).
- Frontend → backend log shipping / a client-logs endpoint (see Decisions), and any persistence of browser logs (e.g. a `localStorage` ring buffer). Browser logs are console-only; revisit both together if browser errors start going missing.
- A log stack (Loki/Grafana), host log drivers, or logging to the database.
- Audit logging of *user actions* as a product feature — this is diagnostic logging, not an audit trail.
- Changing any HTTP response body or status. The generic-500 contract and "backend never sends exception text" are untouched.
- Logging in the Postgres container beyond Docker's default.

## Open questions
1. **Frontend log shipping** — defaulted to *no*. Say so if you run the prod containers somewhere you can't open browser devtools and want browser errors in `docker compose logs`.
2. **Expose `/actuator/loggers`** (change log levels at runtime without a restart)? Currently only `health` is exposed; leaving it that way. Cheap to add to `application-dev.yml` only if wanted.

## Dependencies
F001 (backend skeleton, `IdGenerator`, `application-*.yml` layout, frontend `src/utils` layout), F014 (`application-prod.yml`, `docker-compose.prod.yml`, `nginx.conf`, the `/actuator/health` healthcheck), and the existing API client conventions from F002. Independent of F003–F013 in behaviour, but the business-event lines touch `AccountService`, `BudgetService` and `RecurringTemplateService` (F003, F006, F007) — all already merged.
