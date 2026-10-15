# F036 — Action Plan

**Depends on**: F001, F002, F018, F031.

One feature branch (`feature/f036-remote-access-authentication`), backend first (login must exist before the frontend has anything to call), then network/compose, then frontend, then docs.

## Phase 0 — Decision record
- [x] Write `docs/adr/0029-session-auth-and-generic-network-binding.md` and add its row to `docs/adr/README.md`.
- [x] Update `docs/PRD.md`: §3 non-goal reworded, new §6.15 Authentication, §7.1 Auth/Network exposure rewritten, §9's graduated future direction removed.

## Phase 1 — Backend: credential, hashing tool, `UserDetailsService`
- [x] Add `spring-boot-starter-security` (and whichever Bouncy Castle artifact `Argon2PasswordEncoder` needs) to `backend/build.gradle`, pinned explicitly.
- [x] `AUTH_USERNAME`/`AUTH_PASSWORD_HASH` env-backed configuration properties.
- [x] Test first: a `UserDetailsService` unit test — the configured username authenticates against the configured Argon2 hash; a wrong password fails; a wrong username fails without revealing which was wrong.
- [x] Implement the single-user `UserDetailsService`.
- [x] Add the one-off Gradle task that prints an Argon2 hash for a given plaintext password, using the same encoder configuration the app verifies with. Document its invocation in backend `CLAUDE.md` and the README.

## Phase 2 — Backend: `SecurityFilterChain`, login/logout/session endpoints
- [x] Test first (`@SpringBootTest`/`MockMvc`, per this repo's REST test convention): `POST /api/auth/login` with correct credentials sets a session cookie and returns 200; wrong credentials return 401 via the new `ProblemDetail` shape; `GET /api/auth/session` returns 401 unauthenticated, 200 once logged in; `POST /api/auth/logout` invalidates the session (`GET /api/auth/session` returns 401 again afterward); every other existing endpoint returns 401 without a session and succeeds with one.
- [x] Implement `SecurityFilterChain`: `permitAll` for `/api/auth/login` and `/actuator/health`, authenticated for everything else; the three auth endpoints themselves.
- [x] Wire CSRF: `CookieCsrfTokenRepository.withHttpOnlyFalse()`.
- [x] Test first: a state-changing request without a CSRF token/header is rejected; with the cookie-sourced token, it succeeds.
- [x] `./gradlew spotlessApply` then `./gradlew spotlessCheck test integrationTest` (Docker running).

## Phase 3 — Backend: error wiring, rate limiting
- [x] Test first: hitting any endpoint unauthenticated returns the `unauthenticated` `ProblemDetail` slug (401); a request past an authorization boundary (if any exists yet) returns `access-denied` (403) — both in the standard `type`/`title`/`status`/`instance` shape, `detail` null.
- [x] Implement `ProblemDetailAuthenticationEntryPoint`/`ProblemDetailAccessDeniedHandler`, registered on the `SecurityFilterChain`. Add both new slugs to wherever `ProblemTypeSlugs`'s fixed constants are documented.
- [x] Test first: N failed logins from the same source within the configured window trip a cooldown; the next attempt (even with correct credentials) returns 429 with the `too-many-login-attempts` slug; after the window elapses, a correct login succeeds again.
- [x] Implement the in-memory per-IP `LoginAttemptTracker`, wired into the login failure/success path.
- [x] Confirm login attempts are logged as outcome/count only — grep the diff for any line that could log the username or password; there must be none.
- [x] `./gradlew spotlessCheck test integrationTest`.

## Phase 4 — Backend: dev CORS
- [ ] Update `WebConfig`: `allowCredentials(true)` with an explicit configured dev origin, replacing the current `allowedOriginPatterns("http://localhost:*")` wildcard (incompatible with credentials).
- [ ] Manual check, dev (`docker compose --profile full up -d`): confirm a cross-origin login from the Vite dev server to the backend actually sets and later sends the session cookie, and that the CSRF cookie/header pairing works end to end.

## Phase 5 — Deployment/network
- [ ] Add `BIND_ADDR` (default `127.0.0.1`) to `docker-compose.yml`'s `full` profile (`backend`, `frontend`) and `docker-compose.prod.yml`'s `frontend`, replacing the hardcoded `127.0.0.1` prefixes. Document it in `.env.example`.
- [ ] Pin `docker-compose.yml`'s `postgres` (`"5432:5432"` → `"127.0.0.1:5432:5432"`) and `pgadmin` (`"5050:80"` → `"127.0.0.1:5050:80"`) unconditionally — not controlled by `BIND_ADDR`.
- [ ] Manual check: `docker compose --profile full up -d` with `BIND_ADDR` unset still behaves exactly as before (loopback-only); setting it to another local interface IP makes the app (but not Postgres/pgAdmin) reachable there.

## Phase 6 — Frontend
- [ ] Test first: a route-guard test — an unauthenticated session-check redirects to `/login` and stores the attempted path; a successful login redirects back to it.
- [ ] Add `/login` route (lazy-loaded, matching the existing per-route convention), the route guard, and `src/api/auth/authQueries.ts` (`useLogin`, `useLogout`, a session-check hook).
- [ ] `apiClient`: add `withCredentials: true`, `withXSRFToken: true`.
- [ ] Add the `unauthenticated`, `access-denied`, and `too-many-login-attempts` entries to `PROBLEM_TYPE_MESSAGES` (`apiError.ts`).
- [ ] Logout action in `Layout`'s nav.
- [ ] Test first: MSW handler for `/api/auth/*`; a test asserting a 401 from any API call (not just the login form) redirects to `/login`.
- [ ] `npm run lint && npm test && npm run build && npm run format:check`.

## Phase 7 — Docs
- [ ] Root `CLAUDE.md`: `BIND_ADDR` env var, "every controller sits behind authentication by default" note.
- [ ] Backend `CLAUDE.md`: `infrastructure/auth` package shape, the `AuthenticationEntryPoint`/`AccessDeniedHandler` exception to the single-`GlobalExceptionHandler` rule (with the filter-chain-runs-before-MVC-dispatch reason), the two new fixed slugs, the hash-generation Gradle task invocation.
- [ ] Frontend `CLAUDE.md`: `/login` route + route-guard pattern, `apiClient`'s new `withCredentials`/`withXSRFToken` config.
- [ ] `docs/features/README.md`: F036 row. Root `README.md`'s "Documented and next up" → "Built" once this ships.
- [ ] README: document `BIND_ADDR`, the Postgres/pgAdmin loopback pinning, and the hash-generation task's exact invocation for setting `AUTH_PASSWORD_HASH`.

## Verification
- [ ] `./gradlew spotlessCheck test integrationTest` (Docker running) and `npm run lint && npm test && npm run build && npm run format:check` all green.
- [ ] Manual, dev (`full` profile): fresh login works; wrong password fails without revealing which field was wrong; 5+ rapid wrong attempts trip the 429 cooldown; logout clears the session; closing and reopening the browser within 30 days stays logged in; every existing page/API call requires login first.
- [ ] Manual: set `BIND_ADDR` to another local network interface's IP, confirm the app is reachable from a second device on that interface and still requires login there; confirm Postgres/pgAdmin are **not** reachable from that second device regardless of `BIND_ADDR`.
- [ ] Manual, prod stack (`docker-compose.prod.yml`): login works same-origin through nginx with no CORS issues; `/actuator/health` still answers unauthenticated (healthcheck keeps passing).
- [ ] Confirm no log line anywhere (backend or frontend) ever contains the username or password, across a mix of successful and failed login attempts.
