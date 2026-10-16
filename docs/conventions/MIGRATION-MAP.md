# CLAUDE.md migration map (one-time artifact; delete after review)

Where every bullet of the three original `CLAUDE.md` files went. "Old" line numbers refer to the files at commit `4463e51` (root 60 lines, backend 89, frontend 123). Paths are relative to the repo root; `conv/` = `docs/conventions/`. "Check" = a deterministic test/lint rule now enforcing it (the prose was shrunk to a pointer or deleted). Where text changed meaning-neutrally it is noted.

## Root `CLAUDE.md`

| Old | Substance | New location |
|---|---|---|
| L3 | Guidance intro; stack files load automatically | `CLAUDE.md` intro (last sentence) |
| L7 | Read PRD first; why-decisions already resolved | `CLAUDE.md` Source of truth, bullet 1 |
| L9-11 | ADR/features/git-log locations | `CLAUDE.md` Source of truth, bullet 2 |
| L15 | Project shape (local, single-user, `BIND_ADDR`, run on demand, stack, DDD) | `CLAUDE.md` intro. "one shared login, F036 - no accounts" is carried by the login rule in `CLAUDE.md` + `conv/auth.md` (ADR 0029) |
| L19-24 | Compose command block | `CLAUDE.md` Commands (verbatim) |
| L25 | `full` profile rationale, native alternative, one-or-the-other ports, `down` behaviour | one-or-the-other: `CLAUDE.md` Commands; rationale + `down`: `conv/tooling.md` Dev stack |
| L26 | pgAdmin note (in `docker-compose.yml` comments); stack commands live in stack files | `conv/tooling.md` Dev stack; stack-files pointer in `CLAUDE.md` Commands |
| L28 | Prod packaging, local smoke test, shared compose project name replaces dev Postgres, external backups volume | `conv/tooling.md` Production packaging (trigger in `CLAUDE.md` Conventions pointer) |
| L30 | `verify.sh` is the one definition of checks; stage prerequisites; bats locally; `e2e.sh`; change check in script | "one definition", "change there": `CLAUDE.md` Commands; bats + `e2e.sh`: `conv/tooling.md`; stage list, default stages, run-all-stages, `npm ci`/Docker/playwright prerequisites: **deleted, duplicated at `scripts/verify.sh` header (lines 2-16)** |
| L32 | CI runs which commands, on which events | **deleted, duplicated at `.github/workflows/ci.yml`** (jobs call `scripts/verify.sh <stage>`; `build-and-push` on `main` and `v*` tags); the images-on-main/tags fact is also in `conv/tooling.md` |
| L36 | Conventional Commits, branching, never commit to develop/main, push only when asked | `CLAUDE.md` Workflow, bullet 1 |
| L37 | CHANGELOG entries ship with the PR (all clauses) | `CLAUDE.md` Workflow, bullet 2 (tightened, every clause kept) |
| L38 | `/implement-feature`, `/audit-and-fix` | `CLAUDE.md` Workflow, bullet 3 |
| L42 | Tool versions pinned everywhere; `check-versions.sh`; sibling list; full patch tags | rule: `CLAUDE.md` Rules for every task; sibling list + postgres/backup detail: `conv/tooling.md` Pinned versions. Check: `scripts/check-versions.sh` (unchanged) |
| L43 | Login in every profile; credential env vars + hash; `.env` single quotes; open endpoints; Swagger needs session; `BIND_ADDR`; Postgres pinned to loopback; `permitAll` for new open path | login/credential/quoting/`permitAll`: `CLAUDE.md` Rules for every task; open endpoints, Swagger, `BIND_ADDR`, Postgres pin, `@Validated` messages: `conv/auth.md` Deployment surface |
| L44 | Time zone explicit; JVM zone from `TZ`; test zones; override with `vi.stubEnv` | pin-a-non-UTC-zone rule: `CLAUDE.md` Rules for every task; mechanism (`TZ`, `Clock.systemDefaultZone()`, `build.gradle`, `vite.config.ts`): `conv/tooling.md` API types and time zone |
| L46 | Free text bounded at every layer; constants; per-entity list; F022 notes exception | rule: `CLAUDE.md` Rules for every task; constants/entity list/exception: `conv/data-constraints.md` |
| L47 | Money `numeric(19,2)`; positivity at three layers | `CLAUDE.md` Rules for every task; example columns in `conv/data-constraints.md` |
| L48-52 | Five money/percentage exceptions (trade lines `19,8`; snapshot `>= 0`; nullable cap tombstone; fuel scales; target percentage) | `conv/data-constraints.md` Money columns (verbatim); tombstone also in `conv/versioned-history-and-recurring.md` |
| L53 | Problem Details contract; `type` derivation; `detail` null; frontend map + fallback | `CLAUDE.md` Rules for every task; mechanics: `conv/errors.md`. The parenthetical about replacing `conflictMessage` is **deleted, duplicated at ADR 0026 L29** |
| L54 | API types generated; login first; pinned operation ids; never overload handler names; regenerate after rebase; CI build; `YearMonth` | regenerate rule: `CLAUDE.md` Rules for every task; rest: `conv/tooling.md` API types and time zone; "never overload a handler name": **Check `ArchitectureTest.controllerHandlerMethodNamesAreUnique` (new)**; login-first: `conv/auth.md` Frontend |
| L56 | Request id contract (browser -> nginx -> backend); logs ids and counts only; audit_log exception; every write use case calls `AuditLog` | request id: `conv/logging.md`; ids-and-counts + audit exception: `CLAUDE.md` Rules for every task (ids-and-counts also duplicated at ADR 0011 L17); "every new write use case calls the audit port in the same transaction": `backend/CLAUDE.md` + `conv/transactions-and-audit.md` (Check: existing `ArchitectureTest.writeUseCasesDependOnAuditLog`) |
| L58 | Keeping these files useful | `CLAUDE.md` Keeping these files useful (rewritten to the new structure) |

## `backend/CLAUDE.md`

| Old | Substance | New location |
|---|---|---|
| L3 | Intro; cross-stack pointer | `backend/CLAUDE.md` L3 |
| L7-14 | Gradle commands | `backend/CLAUDE.md` Commands. `./gradlew build` line dropped (standard Gradle lifecycle, not a rule) |
| L15 | Swagger UI / OpenAPI URLs | `backend/CLAUDE.md` Commands (+ "need a session", from root L43) |
| L19 | Packages; `IdGenerator` port, ADR 0005; `TextFieldConstraints`; `AuditableEntity` | `conv/backend-structure.md` Layering; `backend/CLAUDE.md`. Checks: `onlyRandomUuidGeneratorCallsUuidRandomUuid` (existing), `noGeneratedValueIds` (new), `everyEntityExtendsAuditableEntity` (existing) |
| L20 | Domain never imports another aggregate (one exception); cross-aggregate checks in application service | Check: `domainAggregatesDoNotDependOnEachOther` (existing); exception + service-level checks: `conv/backend-structure.md`, `backend/CLAUDE.md` |
| L21 | Use-case service shape | `conv/backend-structure.md` Shapes |
| L22 | Repository adapter shape; Lombok allowed in infra | `conv/backend-structure.md` Shapes. Check: `jpaRepositoriesAreNotPublic` (existing) |
| L23 | Query object; static utility variant (`FuelRatiosQuery`) | `conv/backend-structure.md` Shapes |
| L24 | Domain port for cross-feature side effect | `conv/backend-structure.md` Shapes |
| L25 | Read-only port without domain aggregate (`BackupStatusPort`) | `conv/backend-structure.md` Shapes |
| L26 | `INVESTMENT` account opening balance/date `null`; branch on type | `conv/accounts-and-investments.md` (pointer trigger in `backend/CLAUDE.md`) |
| L27 | `NetWorthQuery` date range rule; single forward pass; investment per-date path | `conv/accounts-and-investments.md` |
| L28 | Never pass `LocalDate.MAX`/`MIN` to a repository query | `conv/accounts-and-investments.md`. Not made a check: `LatestInvestmentSnapshotQuery` legitimately uses `LocalDate.MAX` in memory |
| L29 | Reports: ports only, unpaged fakes, `CsvWriter` types, `Arrays.asList` for nullable cells | `conv/csv-output.md` |
| L30 | Update endpoints `PATCH` full replace | `conv/backend-structure.md` Endpoints |
| L31 | List endpoints `PagedModel` vs plain list; in-memory filtering | `conv/backend-structure.md` Endpoints |
| L32 | Versioned history rules | `conv/versioned-history-and-recurring.md` |
| L33 | Replace child table via bulk `@Modifying @Query`, not derived delete | `conv/backend-structure.md` Shapes (repository adapter sub-bullet) |
| L34 | Recurring catch-up: pure generator, non-transactional `runCatchUp`, `insertIfAbsent` | `conv/versioned-history-and-recurring.md` |
| L38 | `infrastructure/auth` classes and behaviour | `conv/auth.md` Backend |
| L39 | Problem-detail entry point/handler; fixed slugs; byte-identical responses | `conv/auth.md` Backend |
| L40 | Auth never logs username/password/hash/address; `@Validated` property messages | `conv/auth.md` (Backend bullet; `@Validated` clause in Deployment surface) |
| L41 | `MockMvcSupport.build` authenticated; no `csrf()`/`user()`; test credential | `conv/auth.md` Backend. Check: **`TestConventionsTest` (new)** |
| L42 | CORS dev-only | `conv/auth.md` Deployment surface |
| L46 | One `GlobalExceptionHandler`; status resolution; `include-*: never` | `conv/errors.md` Backend. Checks: `onlyOneRestControllerAdviceExists`, `onlyGlobalExceptionHandlerHandlesExceptions` (existing) |
| L47 | Derived `type`/`title`; duplicated classes collapse; fixed 500 constants; `detail` null | `conv/errors.md` Backend |
| L48 | `errors` member for bean validation | `conv/errors.md` Backend |
| L49 | `@AssertTrue` is a field-level violation; `codeForAssertTrue` | `conv/errors.md` Backend |
| L50 | New expected error = `@ResponseStatus` exception; 409/400/404; `@ApiResponse`; explicit `200` | `conv/errors.md` Backend |
| L51 | Name uniqueness; sub-category per parent; product global; `CategoryService.delete` guard | `conv/backend-structure.md` Uniqueness and delete guards |
| L52 | `AccountService.delete` and `AccountUsageChecker`; new FK table must be added | `conv/accounts-and-investments.md` (pointer trigger in `backend/CLAUDE.md`) |
| L53 | Find-then-insert race -> expected conflict exception | `conv/errors.md` Backend |
| L54 | Splitting a method drops invariants (F009/F022/F027, issue #113) | `CODING_STANDARDS.md` Behaviour preserved across a refactor (story kept as the example) |
| L58 | Multi-write use case must be `@Transactional`; trade confirmation; cascades | headline: `backend/CLAUDE.md` Rules; detail: `conv/transactions-and-audit.md`. The "see the Errors bullet" cross-reference now points to `CODING_STANDARDS.md` |
| L60 | Audit makes every write use case `@Transactional`; overloads; `AuditOrigin` | `conv/transactions-and-audit.md` Audit log. Check: `writeUseCasesDependOnAuditLog` (existing) |
| L64 | Config rule: `application.yml` vs profile ymls; no `application-test.yml` | `backend/CLAUDE.md` Rules (verbatim, tightened) |
| L68 | Boot 4.x removed test slices; hand-built `MockMvc`/`ObjectMapper` | `conv/backend-testing.md` |
| L69 | Two tiers, `integration` tag | `conv/backend-testing.md`. Check: `TestTaggingTest` (existing) |
| L70 | Fakes for application logic | `conv/backend-testing.md` |
| L71 | Boundary tests must not be `@Transactional` | `conv/backend-testing.md` |
| L72 | Seed rows collide (V2/V12/V13/V14/V24), `institutionId` for accounts, `INVESTMENT` account construction | `conv/backend-testing.md` |
| L73 | Testing a migration against pre-existing data; non-pooled datasource | `conv/backend-testing.md` |
| L74 | Concurrency tests | `conv/backend-testing.md` |
| L78 | Logging rationale; Boot properties only | `conv/logging.md` header |
| L80 | Where logging is configured; rolling caps; env overrides | `conv/logging.md` Backend |
| L81 | Reading prod logs | `conv/logging.md` Backend |
| L82 | Tests never write a log file | `conv/logging.md` Backend |
| L83 | `RequestLoggingFilter`, request id validation, `IdGenerator` | `conv/logging.md` Backend |
| L84 | `GlobalExceptionHandler` logging unchanged by F031 | `conv/logging.md` Backend |
| L85 | Never log amounts/descriptions/names/opening balances; never log in `domain/`; plain `Logger`; INFO for state changes; no logging in loops | ids-and-counts: `CLAUDE.md` Rules + `CODING_STANDARDS.md` Logs and data exposure; rest: `conv/logging.md` Backend (Log statements). Check: `domainClassesDoNotLog` (existing) |
| L86 | Spring log lines leak values; `DefaultHandlerExceptionResolver`, `log-resolved-exception` | `conv/logging.md` Backend |
| L87 | SQL logging; never enable bind logging | `conv/logging.md` Backend; reviewed in `CODING_STANDARDS.md` |
| L88 | Known accepted leak in ERROR traces | `conv/logging.md` Backend |
| L89 | `LogCapture` | `conv/logging.md` Backend |

## `frontend/CLAUDE.md`

| Old | Substance | New location |
|---|---|---|
| L3 | Intro; cross-stack pointer | `frontend/CLAUDE.md` L3 |
| L8, L12-15 | `npm install`, `format`, `test`, `e2e`, `generate-api-types` | `frontend/CLAUDE.md` Commands |
| L9, L11 | `npm run dev`, `npm run lint` | dropped: restate `package.json` scripts (ESLint role named in `CODING_STANDARDS.md`) |
| L10 | `npm run build` typecheck + production build | `frontend/CLAUDE.md` Commands (+ the TS2591 gotcha from L90) |
| L20 | `src` layout map | `conv/frontend-structure.md` Layout |
| L22 | Lazy routes, named-export pages | `conv/frontend-structure.md` Routes (+ trigger in `frontend/CLAUDE.md`) |
| L26 | `/login` route guard, `AuthenticatedApp` | `conv/auth.md` Frontend |
| L27 | Session is a query; 401 handling; `skipSessionExpiry` | `conv/auth.md` Frontend |
| L28 | `withCredentials`/`withXSRFToken`; `resolveBaseUrl` | `conv/auth.md` Frontend |
| L29 | `generate-api-types` logs in first | `conv/auth.md` Frontend |
| L30 | Auth mocks and tests | `conv/auth.md` Frontend |
| L34 | Shared Axios instance; base URL includes `/api`; `unwrap()`/`PROBLEM_TYPE_MESSAGES`; hooks only | Axios/base URL: `conv/frontend-structure.md` API clients; `unwrap`/map: `conv/errors.md` Frontend |
| L36 | `VALIDATION_CODE_MESSAGES`; `fieldErrors` unused | `conv/errors.md` Frontend |
| L42-46 | `ErrorAlert`, `InlineEditActions`, `ConfirmDialog`, `PaginationControls`, `DataTableBody` | `conv/frontend-components.md` |
| L47 | `ResponsiveTable`, `ResponsiveDialog`, `ResponsiveFilterBar` | `conv/frontend-responsive.md` Components |
| L48 | `sx` bare number is a fraction | `conv/frontend-responsive.md` |
| L49 | No hard-coded non-shrinking widths (360px) | `conv/frontend-responsive.md`; reviewed in `CODING_STANDARDS.md` |
| L50 | Breakpoints only via `useBreakpointBand`; viewport helpers | `conv/frontend-responsive.md`. Check: **ESLint `no-restricted-imports` for `useMediaQuery` (new)** |
| L51 | Row editing pattern; dialog-at-every-size; when to skip a mobile dialog | `conv/frontend-responsive.md` Row editing pattern |
| L52-53 | `LoadFailedNotice`, `MonthPicker` | `conv/frontend-components.md` |
| L57 | TanStack Query for every read/write; no hand-rolled fetching | `frontend/CLAUDE.md` Rules + `conv/frontend-data-loading.md` |
| L59 | Hooks per area; key factory; ESLint on HTTP functions | `conv/frontend-data-loading.md`. Checks: existing `no-restricted-imports` pattern; **raw `useQuery`/`useMutation` import ban in `src/features` (new)** |
| L60 | Query defaults and `STALE_TIME`; devtools | `conv/frontend-data-loading.md` |
| L61 | Mutations invalidate everything; `skipInvalidate`; no optimistic updates | `frontend/CLAUDE.md` Rules + `conv/frontend-data-loading.md` |
| L62 | Writes and errors (`mutateAsync`, `defaultErrorMessage`, one-argument edit) | `conv/frontend-data-loading.md`. The sentence "the message already carries the API function's `conflictMessage`" was stale since F031/ADR 0026 (the argument no longer exists) and is replaced by "resolves from the backend's problem `type`" |
| L63 | `useQueryState`, `combineLoadState`, `keepPreviousData` | `conv/frontend-data-loading.md` |
| L64 | Onboarding gate (`useHasAccounts`) | `conv/frontend-data-loading.md` |
| L65 | Embedded widgets need no coordination | `conv/frontend-data-loading.md` |
| L69 | Skeletons, `useDelayedFlag`, `fadeInSx`, reduced motion | `conv/frontend-data-loading.md` Loading skeletons |
| L73 | Charts are hand-drawn SVG (list) | `conv/frontend-charts.md` |
| L75-78 | React Compiler rules; donut reuse; FII exception; legend `Button` `whiteSpace` | `conv/frontend-charts.md` |
| L82-89 | Vitest config, RTL queries, MSW stores, seed-row visibility, `renderWithQueryClient`, `App.test` reset, `.env.test`, `getByRole` behind dialog | `conv/frontend-testing.md` |
| L90 | No Node types; `vi.stubEnv`; non-UTC zone | TS2591/build: `frontend/CLAUDE.md` Commands; stubEnv/zone: `conv/frontend-testing.md` |
| L94 | E2E overview, ports, report dirs | `conv/frontend-e2e.md` |
| L96-97 | Mock by route; geometry only | `conv/frontend-e2e.md` |
| L98 | `Layout` nav at `lg`; `matchMedia` stub | `conv/frontend-e2e.md` (cross-linked from `conv/frontend-responsive.md`) |
| L99-100 | `mockApi` glob; devtools off under Playwright | `conv/frontend-e2e.md` |
| L104 | Frontend logging rationale | `conv/logging.md` Frontend |
| L106 | Use `logger`, not `console.*`; no bodies/amounts | `conv/logging.md` Frontend. Check: existing ESLint `no-console` |
| L107 | Log level env, `localStorage` override | `conv/logging.md` Frontend |
| L108 | HTTP logging owned by interceptors | `conv/logging.md` Frontend |
| L109 | Uncaught error logging, `ErrorBoundary` | `conv/logging.md` Frontend |
| L110 | `ErrorEvent` test gotcha | `conv/logging.md` Frontend |
| L114 | i18n overview (en/pt-BR, full coverage) | `frontend/CLAUDE.md` Rules + `conv/frontend-i18n.md` |
| L116-123 | Locale provider; namespaces; formatter; seed names; loosely typed `t`; `i18next.t()` outside React; native month picker; migration gaps | `conv/frontend-i18n.md` (verbatim) |

## New checks (rules that used to be prose)

| Check | Replaces |
|---|---|
| `ArchitectureTest.noGeneratedValueIds` | "ids never come from `@GeneratedValue`" (backend L19; second half of ADR 0005) |
| `ArchitectureTest.controllerHandlerMethodNamesAreUnique` | "never overload a handler method name within a controller" (root L54) |
| `TestConventionsTest.testsDoNotUseSecurityCsrfOrUserPostProcessors` | "do not use `csrf()`/`user()`" (backend L41) |
| ESLint `no-restricted-imports`: `useMediaQuery` outside `useBreakpointBand`/`Layout` | "never a raw `useMediaQuery`" (frontend L50) |
| ESLint `no-restricted-imports`: `useQuery` family in `src/features` | "never a raw `useQuery`" (frontend L59) |

Rules already enforced before this change (kept as checks, prose removed): no logging in `domain..` (`domainClassesDoNotLog`), single advice and no stray `@ExceptionHandler`, `UUID.randomUUID()` confinement, entities extend `AuditableEntity`, package-private `JpaRepository`, domain framework independence, write use cases depend on the audit port, `@SpringBootTest` tagging (`TestTaggingTest`), `no-console`, version pinning (`check-versions.sh`).
