# F036 — Remote Access & Authentication

## Summary
Lets the app be safely reached from somewhere other than `localhost` on the machine running it (a home LAN, a private overlay network, eventually a VPS), by adding two independent things ([ADR 0029](../../adr/0029-session-auth-and-generic-network-binding.md)):

1. **Authentication** — a single shared, Argon2-hashed credential gates every page and API call via a Spring Security session cookie. The app stays single-user (PRD §4): no `User` aggregate, no per-user data scoping, no new table on any existing aggregate.
2. **Generic network exposure** — a new `BIND_ADDR` environment variable replaces the hardcoded `127.0.0.1` port bindings for the backend/frontend in both compose files, so whichever private-network tool is actually used (Tailscale is the expected real choice, but nothing in the app assumes it) just points `BIND_ADDR` at its own interface IP. Postgres/pgAdmin get pinned to loopback-only regardless of `BIND_ADDR`, closing a pre-existing gap where they're reachable from any interface today.

Required in both dev and prod — no profile-based auth bypass, since the dev stack can itself be the thing exposed over the private network while developing.

## Scope
- Backend: Spring Security session-cookie login/logout/session-check endpoints, Argon2 credential verification, CSRF, custom 401/403 `ProblemDetail` wiring, per-IP login rate limiting, a one-off hash-generation Gradle task.
- Backend/compose: `WebConfig` CORS gains `allowCredentials` for dev's cross-origin cookie; `/actuator/health` stays reachable without a session (the Docker healthcheck has no cookie). Everything else - including Swagger UI and `/v3/api-docs` - needs a session, so `npm run generate-api-types` logs in first.
- Infra: `BIND_ADDR` env var threaded through `docker-compose.yml` (`full` profile) and `docker-compose.prod.yml`; `postgres`/`pgadmin` pinned to `127.0.0.1` unconditionally.
- Frontend: dedicated `/login` route, route guard, axios session/CSRF cookie config, logout action.
- Docs: ADR 0029 (already written), this spec/plan, PRD §3/§6.15/§7.1/§9 edits (already made), root/backend/frontend `CLAUDE.md` updates, `docs/features/README.md` row, root `README.md` status line.
- Out of scope: see [Non-goals](#non-goals).

## Decisions

| Decision | Why |
|---|---|
| Single shared credential (env-provided username + Argon2 hash), not a `users` table. | This is a lock on the door, not a multi-user system — no existing aggregate gets an owner column, no existing query gets a per-user filter. |
| Session-cookie login via Spring Security, not HTTP Basic or a `localStorage` bearer token. | Logout support and centrally-expirable sessions, which scale better if this box is ever more exposed later; an `HttpOnly` cookie isn't readable by injected JS, cheap insurance alongside the existing CSP (`frontend/nginx.conf`). |
| Argon2, not BCrypt, for the password hash. | User's explicit preference; both are supported by Spring Security with comparable integration cost. |
| A one-off Gradle task (not a runtime HTTP endpoint) turns a typed password into its Argon2 hash. | Mirrors F018's `backup-keygen` precedent for setup-time tooling; a hashing endpoint would be needless runtime attack surface for a one-time operation. |
| 30-day idle session timeout, in-memory session store (no Redis). | The access control that matters is the network layer, not an aggressive session policy, for a personal app; a dev DevTools restart may drop a session, accepted as "just re-login." |
| CSRF via `CookieCsrfTokenRepository.withHttpOnlyFalse()` + axios's built-in `withXSRFToken`. | Zero custom interceptor code — the pinned `axios@1.20.0` already supports reading the `XSRF-TOKEN` cookie and attaching `X-XSRF-TOKEN` itself. |
| A dedicated `AuthenticationEntryPoint`/`AccessDeniedHandler` pair builds `ProblemDetail` bodies directly, rather than extending `GlobalExceptionHandler`. | Spring Security's filter chain throws these exceptions before Spring MVC dispatch — `@ControllerAdvice` architecturally never sees them. New slugs: `unauthenticated` (401), `access-denied` (403). |
| Per-IP in-memory login-attempt tracker, rejecting with `429` past a threshold. | Small, self-contained (no new dependency), meaningful defense-in-depth against a compromised device on the same private network hammering the login endpoint. |
| `BIND_ADDR` env var (default `127.0.0.1`) replaces every hardcoded loopback port mapping for backend/frontend in **both** compose files. | One generic knob, not a Tailscale-specific one — whichever overlay tool is used just sets this; TLS termination is an equally separate, generic concern, not assumed to come from the network tool. |
| `postgres`/`pgadmin` pinned to `127.0.0.1` **unconditionally**, independent of `BIND_ADDR`. | They currently publish on every interface (`"5432:5432"`, `"5050:80"`, no host-IP prefix) — left as-is, the moment `BIND_ADDR` exposes the app, the database and its admin UI (plaintext dev credentials) would be reachable on the same network, bypassing this entire feature. |
| No dev-profile authentication bypass. | The dev stack (`docker-compose.yml`'s `full` profile) can itself be exposed over the private network while developing — bypassing auth there would leave exactly that case unauthenticated. |
| No CORS change needed in prod. | nginx already reverse-proxies `/api/` to the backend same-origin (ADR 0006) — cookie auth needs no cross-origin handling there. Dev needs `allowCredentials(true)` with an explicit origin list (replacing the current wildcard pattern - an exact list also stops any other page on `localhost` from making cookie-carrying calls), but not HTTPS/`SameSite=None` — Vite/backend are cross-origin but same-site, so `SameSite=Lax` still sends the cookie. |
| `/v3/api-docs` and Swagger UI are behind the login like everything else; `generate-api-types` signs in. | The alternative was a dev-only `permitAll` for the docs, which is a profile-based auth bypass for a route of the very stack that may be network-exposed. A ~100-line Node script that does the SPA's own login sequence keeps the "no bypass" rule literal. |
| Rate-limit key is the socket address, or `X-Real-IP` where `myfinances.auth.client-ip-header` is set (prod only). | Behind nginx every client otherwise shares the nginx container's address (one global bucket). Prod's backend is reachable only through nginx, which overwrites `X-Real-IP` with the real peer, so trusting it there cannot be forged; dev never sets it. |
| The session cookie is persistent (`Max-Age` 30 days), not a browser-session cookie. | "Closing and reopening the browser within 30 days stays logged in" is a requirement; Tomcat's idle timeout alone would still log you out with the browser. |

## Backend

### Dependencies
- `spring-boot-starter-security` (not present in `build.gradle` today — confirmed via repo check).
- Whatever Bouncy Castle artifact `Argon2PasswordEncoder` needs at runtime — confirm the exact `org.bouncycastle:*` coordinate during implementation and pin it per the root `CLAUDE.md` version-pinning rule if it's a container-relevant version (it isn't, but still pin the Gradle dependency version explicitly).

### Credential configuration
- Two env vars: `AUTH_USERNAME` and `AUTH_PASSWORD_HASH` (the Argon2 hash, never the plaintext password), bound into `myfinances.auth.*` (`application.yml`, `${AUTH_USERNAME:}`/`${AUTH_PASSWORD_HASH:}`) and validated at startup by `AuthProperties` (`@Validated`): a missing username/hash, or a hash that is not `$argon2...` (a plaintext password, or one mangled by Compose expanding its `$`s), fails the boot with a message naming the variable and the fix. The hash goes in `.env` single-quoted.
- `SingleUserDetailsService` (not a `@Component`; built as a bean by `SecurityConfig`) with exactly one user from those two properties — no JPA entity, no table. The same `AuthProperties` record carries the rate-limit knobs (`max-failed-attempts` 5, `failure-window` 15m, `lockout-duration` 15m) and the optional `client-ip-header`.

### Hash generation tooling
- `./gradlew -q generateAuthHash` (a `JavaExec` task running `infrastructure/auth/AuthHashGenerator`): reads the password from the `AUTH_PASSWORD` environment variable if set, otherwise prompts on stdin — so the plaintext is never a command-line argument and never lands in shell history (`read -rs AUTH_PASSWORD && export AUTH_PASSWORD` types it silently). It builds its encoder from `AuthPasswordEncoder.create()`, the same factory the runtime bean uses, and prints a ready-to-paste `AUTH_PASSWORD_HASH='...'` line (stdout; prompt/notes on stderr). Never exposed as an HTTP endpoint. `-Ppassword=` was rejected precisely because it puts the plaintext in shell history and the process list.

### `SecurityFilterChain`
- `/api/auth/login`, `/actuator/health` (needed unauthenticated by the Docker healthcheck, which carries no session) are `permitAll`; everything else under `/api/**` requires an authenticated session.
- `POST /api/auth/login` (`infrastructure/web/auth/AuthController`, JSON body `{username, password}`) — authenticates via `AuthenticationManager` through `SessionAuthenticator` (which also rotates the session id and CSRF token, the part of `formLogin` a controller must do itself), establishes the session on success; on failure, increments the per-client attempt tracker and answers `401` by calling the same entry point the filter chain uses (so a wrong username and a wrong password are byte-identical), or `429` (`TooManyLoginAttemptsException`, an ordinary `@ResponseStatus` exception) if the client is already in a cooldown.
- `POST /api/auth/logout` — invalidates the session (`204`); needs a session like everything else, so an expired one answers `401` and the frontend redirects as it always does.
- `GET /api/auth/session` — `200 {authenticated: true}` if authenticated, `401` (via the same entry point) otherwise; this is what the frontend calls on boot to decide whether to show the app or the login screen.
- CSRF: `CookieCsrfTokenRepository.withHttpOnlyFalse()`, applied to every state-changing request as Spring Security does by default (GET/HEAD/OPTIONS/TRACE exempt). `/api/auth/login` is reachable without a prior CSRF token exchange only in the sense that the frontend's boot-time `GET /api/auth/session` call already receives the `XSRF-TOKEN` cookie before the user ever submits the login form — no special CSRF carve-out needed for login itself.

### Error wiring
- `infrastructure/auth/ProblemDetailAuthenticationEntryPoint` (`implements AuthenticationEntryPoint`) and `infrastructure/auth/ProblemDetailAccessDeniedHandler` (`implements AccessDeniedHandler`) each write a `ProblemDetail`-shaped body directly to the response through the package-private `ProblemDetailResponseWriter` (hand-built JSON: four fixed members, only `instance` needs escaping; status 401/403, `type`/`title` from the new fixed `ProblemTypeSlugs.UNAUTHENTICATED_*`/`ACCESS_DENIED_*` constants, `instance` from the request path, no `detail` — same contract as every other error response, ADR 0026). In this single-role app the 403 is always a failed CSRF check.
- Both new fixed slugs are documented beside `INTERNAL_ERROR_TYPE` in `ProblemTypeSlugs` and in `backend/CLAUDE.md`'s Authentication section; `too-many-login-attempts` is derived from `TooManyLoginAttemptsException` like every other slug.

### Rate limiting
- `infrastructure/auth/LoginAttemptTracker`: an in-memory `ConcurrentHashMap` keyed by the client address (`ClientAddressResolver`: the socket's remote address, or the `X-Real-IP` header in prod, validated as an address-shaped value), recording failure count + window start + lockout end, all on the injected `Clock`. 5 failures within 15 minutes trip a 15-minute cooldown; further attempts during the cooldown get `429` with the `too-many-login-attempts` slug instead of being evaluated against the credential at all; once it elapses the client starts from zero, and a successful login clears its record.
- No persistence — a backend restart clears the tracker, consistent with the session-store decision above.

### CORS (`WebConfig`)
- Dev needs `allowCredentials(true)` with the Vite dev server's exact origin configured instead of the previous `allowedOriginPatterns("http://localhost:*")` wildcard. Decided: a list property, `myfinances.cors.allowed-origins`, set only in `application-dev.yml` (`http://localhost:5173`); the dev compose backend overrides it through `MYFINANCES_CORS_ALLOWED_ORIGINS` to `http://localhost:5173,http://${BIND_ADDR:-127.0.0.1}:5173`, so the dev stack reached on a `BIND_ADDR` interface works without a second knob. With the property empty (prod, tests under another profile) `WebConfig` registers no mapping at all. (`allowedOriginPatterns` *can* be combined with credentials; the exact list is chosen because the wildcard would let any other page on `localhost` make cookie-carrying calls.)
- Prod is unaffected — same-origin via nginx, no CORS exposure either way.

### Logging
- Login success/failure logged at INFO as a count/outcome only (e.g. "login attempt failed" with no identifying detail beyond what `RequestLoggingFilter` already attaches via the request id) — never the username or password (ADR 0011, unchanged).

## Deployment / Network

### `BIND_ADDR`
- New env var, default `127.0.0.1`. Applied to:
  - `docker-compose.yml`'s `full` profile: `backend` (`127.0.0.1:8080:8080` → `${BIND_ADDR:-127.0.0.1}:8080:8080`) and `frontend` (`127.0.0.1:5173:5173` → `${BIND_ADDR:-127.0.0.1}:5173:5173`); the backend also receives `AUTH_USERNAME`/`AUTH_PASSWORD_HASH` (`${...:-}`, so a Postgres-only `docker compose up -d` without them still works) and the CORS origins above.
  - `docker-compose.prod.yml`'s `frontend` (`127.0.0.1:${FRONTEND_PORT:-8081}:80` → `${BIND_ADDR:-127.0.0.1}:${FRONTEND_PORT:-8081}:80`); its `backend` receives `AUTH_USERNAME`/`AUTH_PASSWORD_HASH` with `${...:?}`, so `up` refuses to start without them.
- Backend's own `server.address` (`application-dev.yml`, currently `127.0.0.1`) stays as-is for native `bootRun` — `BIND_ADDR` is a compose-level port-mapping concern, not a JVM bind-address one; native dev was never in scope for remote access (that always goes through the `full` compose profile or the prod stack).
- `.env.example` documents `BIND_ADDR`, `AUTH_USERNAME` and `AUTH_PASSWORD_HASH` (with the single-quote requirement and the hash-generation command) alongside the other compose-read env vars.
- Reaching the *dev* stack from another device additionally needs the SPA to call the API on the host it was served from: in dev only, `apiClient` swaps `localhost` in `VITE_API_BASE_URL` for the page's own hostname (`resolveBaseUrl`, `src/api/core/client.ts`), because the session cookie is scoped to that host. Use the interface IP in the URL, not a hostname Vite's `allowedHosts` doesn't know.

### Postgres / pgAdmin lockdown (dev only)
- `docker-compose.yml`: `postgres`'s `"5432:5432"` → `"127.0.0.1:5432:5432"`; `pgadmin`'s `"5050:80"` → `"127.0.0.1:5050:80"`. Unconditional — not controlled by `BIND_ADDR` at all, since there's no legitimate case for either being reachable from outside the host.
- `docker-compose.prod.yml` already doesn't publish Postgres's port — no change needed there.

## Frontend

### Routing
- New lazy-loaded `/login` route (`React.lazy`, matching every other route's convention in `App.tsx`).
- A route guard (wrapping the existing authenticated app shell) calls `GET /api/auth/session` on mount; unauthenticated → redirect to `/login`, storing the originally-requested path; authenticated → render as normal. A 401 from any subsequent API call (session expired mid-use) redirects the same way.

### API client (`src/api/core/client.ts`)
- `apiClient` gains `withCredentials: true` and `withXSRFToken: true`; axios handles the CSRF cookie/header pairing itself. (Plus the dev-only base-URL host swap described under Deployment.)
- The session is a TanStack query (`SESSION_QUERY_KEY`); a 401 from *any* other query or mutation flips it to signed-out and drops the cached app data (`createQueryClient`'s `QueryCache`/`MutationCache` `onError`), which is how mid-use expiry redirects with no per-call-site code. The login mutation opts out (`meta.skipSessionExpiry`) because its 401 means "wrong password".
- The guard sits above the whole app shell (`App.tsx`: `/login` and `*` -> `RequireAuth` -> `AuthenticatedApp`), so the onboarding accounts check, the backup banner and every page hook wait for a confirmed session.

### Login page
- A simple username/password form posting to `useLogin()` (new `src/api/auth/authQueries.ts`, matching the area-hook convention), redirecting to the stored "next" path (or the dashboard) on success, showing the existing `ErrorAlert` pattern on failure (including the `429` rate-limit case via the new `too-many-login-attempts` entry in `PROBLEM_TYPE_MESSAGES`).
- Logout action in `Layout`'s app bar (an icon button next to the language/theme toggles), calling `useLogout()`; the guard then redirects to `/login`. Known gap: the first-run onboarding screen renders outside `Layout`, so it has no logout button.

## Docs
- **ADR 0029** (already written) — the authentication mechanism, credential storage, CSRF, error wiring, rate limiting, and the generic `BIND_ADDR` network-binding decision, including every alternative considered (HTTP Basic, a `localStorage` token, a `users` table, pinning to Tailscale specifically, a dev bypass).
- **PRD** (already edited): §3 non-goal reworded (no more "no authentication"/"bound to localhost only" claim), new §6.15 Authentication, §7.1 Auth/Network exposure rewritten, §9's "Remote/LAN access with authentication" future direction removed (graduated into real scope).
- Root `CLAUDE.md`: note the new `BIND_ADDR` env var alongside the other cross-stack version/env-var conventions, and that every controller now sits behind authentication by default (plus the single-quoting requirement for the hash and the `permitAll` rule).
- Backend `CLAUDE.md`: document the `infrastructure/auth` package, the `AuthenticationEntryPoint`/`AccessDeniedHandler` exception to the "one `GlobalExceptionHandler`" rule (with the architectural reason — Spring Security's filter chain runs before MVC dispatch), the two new fixed slugs, and the hash-generation Gradle task.
- Frontend `CLAUDE.md`: the `/login` route, the route-guard pattern, and `apiClient`'s new `withCredentials`/`withXSRFToken` config.
- `docs/features/README.md`: F036 row. Root `README.md`'s "Documented and next up" line.
- `CHANGELOG.md` entry deferred to the implementing PR (ADR 0007) — this is a `feat` with an `Upgrade:` sub-line (new required env vars, changed port-binding behavior).

## Non-goals
- Multi-user accounts, per-user data scoping, or any `User` aggregate — explicitly a shared single credential (see [Decisions](#decisions)).
- Password reset / "forgot password" flows, or any email integration — the credential is rotated by regenerating the hash and restarting the app.
- Pinning to, or bundling setup for, any specific VPN/overlay-network product (Tailscale, Headscale, WireGuard, ZeroTier, Netmaker, Nebula, ...) — `BIND_ADDR` is the only interface this feature defines; choosing and configuring the actual network tool is an operator/README concern, not app code.
- TLS termination — a separately-chosen, equally generic operator concern (e.g. Caddy/certbot), not provided or assumed by this feature.
- Any change to native `bootRun`/`npm run dev` (outside the `full` compose profile) for remote reachability — those were never exposed beyond loopback and aren't being changed here.
- An external/persistent session store (Redis or similar) — in-memory sessions are accepted, including the dev-restart caveat (see Decisions).

## Dependencies
F001 (backend skeleton, compose files), F002 (existing `apiError.ts`/`unwrap()` convention the new rate-limit slug plugs into), F018 (the `backup-keygen` precedent this feature's hash-generation tooling follows), F031 (RFC 9457 `ProblemDetail` contract this feature's new entry point/handler must match). Touches every existing controller (all sit behind authentication by default) with no change to any of their business logic.
