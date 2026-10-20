# 0011. Logging: SLF4J/Logback + a request id; no aggregator

Status: Accepted
Date: 2026-09-19

## Context
Neither stack had deliberate logging. The backend had zero log statements of its own, and `GlobalExceptionHandler` turned every unexpected exception into a generic 500 without recording it anywhere, so a real bug left no trace. The frontend had no `console.*` calls, no error boundary and no global error hooks, so an uncaught render error blanked the page silently.

The app is local, single-user and run on demand (PRD §7.3, ADR 0003, ADR 0006): torn down between sessions, one reader of the logs, no ops team. Anything heavier than "read the logs yourself" is unjustified, but a container's own logs die with it (`docker compose down`, or a recreate for a new `IMAGE_TAG`), so a bug seen last week would be gone.

## Decision
- **SLF4J + Logback, configured with Spring Boot's `logging.*` properties only.** No new dependency and no `logback-spring.xml`, so there is no second file to drift between profiles.
- **Human-readable text in every profile, not JSON.** Nothing consumes structured logs here; the reader is a person running `docker compose logs`. If a collector is ever added, it is one property (`logging.structured.format.console`) plus dropping the correlation pattern.
- **stdout plus a size- and age-capped rolling file** (`logging.file.name` + `logging.logback.rollingpolicy.*`: 50MB per file, 90 days, 300MB total, gzip archives). In prod the file lives on a named Docker volume, which outlives the container; Docker's `json-file` driver still rotates stdout (10m x 3) so `docker compose logs` keeps working. nginx's access log stays stdout-only, because the backend file already has an access line per request carrying the same request id.
- **A per-request id.** The frontend sends `X-Request-Id` (`crypto.randomUUID()`) on every Axios request; nginx generates one if the browser did not; the backend accepts a valid inbound value (`^[A-Za-z0-9-]{1,64}$`), otherwise generates one from the `IdGenerator` port (ADR 0005), puts it in the SLF4J MDC as `requestId`, and echoes it in the response header. The validation is what stops the value being a log-forging / header-injection vector.
- **No logging in `domain/`.** Application services use plain `LoggerFactory.getLogger`, not Lombok `@Slf4j` (ADR 0004 keeps the domain framework-free; Lombok stays off everything but infra entities/DTOs per ADR 0005). Services log only state-changing events with real information (cascades, generated data, new versions), at INFO; plain CRUD is covered by the access line.
- **Authored log lines carry ids and counts only**: never amounts, descriptions, notes or entity names.
- **The frontend does not ship logs to the backend.** A single `logger` (the only place allowed to call `console.*`) writes to the browser console; Axios interceptors log failed requests, and global/React error hooks capture uncaught errors.

## Alternatives considered
- **A log stack (Loki/Grafana, ELK)**: far too heavy for one user on one machine, and wants JSON.
- **A database appender**: couples the logs to the app's DB, so a database outage would lose the log that explains it.
- **Host log drivers (`journald`, `syslog`)**: meaningless on Docker Desktop for Windows/macOS.
- **A bind mount for the prod log directory**: a new named volume inherits the image's `chown` to the non-root `spring` user, a bind mount does not (UID mismatch on Linux). The trade-off is reading the files through Docker rather than straight from the host.
- **A `POST /api/client-logs` endpoint**: a new write endpoint on an unauthenticated API that would need rate limiting and log-forging sanitizing, to solve a problem a single local user does not have (they can open devtools). Revisit together with any persistence of browser logs if the app ever runs somewhere the browser console is unreachable.

## Consequences
- Every unexpected exception is now recorded with a full stack trace and a request id; expected `@ResponseStatus` errors log their exception class name (never the message), which is the only place to see *which* 409 happened, because the backend never sends exception text. The HTTP response contract is unchanged.
- One failing click can be followed browser console -> nginx -> backend log by request id.
- Logs survive `stop`/`down`/image updates, and are removed by `docker compose down -v` (which also wipes the database), `docker volume rm`, or by design once older than 90 days or beyond the 300MB total cap.
- Stack traces include exception messages, and Postgres constraint-violation messages can echo the failing row. That is accepted (the logs sit on the same machine as the database, and the trace is what makes the bug fixable), but the file keeps those traces for up to 90 days, so redact before pasting a log into a public issue. Bind-parameter logging (`org.hibernate.orm.jdbc.bind`) must never be enabled.
- Browser errors are console-only and go missing if devtools are not open at the time.
- The frontend gains an `ErrorBoundary` so a page render crash no longer blanks the shell; this is the one non-logging addition.
