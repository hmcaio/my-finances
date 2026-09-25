# Frontend (React / TypeScript / Vite)

Frontend-specific rules. Cross-stack conventions (bounded free text, money, the error-message contract, OpenAPI type generation) are in the root `CLAUDE.md`.

## Commands (run from `frontend/`)

```
npm install                # first time (.npmrc sets legacy-peer-deps for openapi-typescript)
npm run dev                # dev server, http://localhost:5173
npm run build              # typecheck + production build
npm run lint               # ESLint
npm run format / format:check   # Prettier (write Prettier only on files you touched)
npm test                   # Vitest, non-watch, CI-friendly
npm run generate-api-types # regenerate src/api/generated/schema.ts from the running backend's /v3/api-docs
```

## Layout

`src/api` (typed HTTP clients, one module per aggregate, each with a sibling `<area>Queries.ts` holding its TanStack Query key factory and hooks; `queryClient.ts`; generated types in `src/api/generated/`), `src/components` (shared UI), `src/features/<area>` (one folder per feature area, mapping to a route), `src/hooks` (`queryState`, the query-result adapter; `useHasAccounts`; `useDelayedFlag`; colour mode), `src/utils` (framework-free helpers shared across features, e.g. `nameLookup`), `src/mocks` (MSW handlers and their in-memory stores) and `src/test` (Vitest setup and helpers).

## API clients

All clients share one Axios instance (`src/api/client.ts`) configured from `VITE_API_BASE_URL` (`.env.development` / `.env.production`). That base URL already includes `/api`, so call sites use bare relative paths (`apiClient.get('/categories')`), never a hand-built `${API_BASE_URL}/api/...`. Errors are unwrapped through `unwrap()`/`ApiError` in `src/api/apiError.ts` (Axios throws on non-2xx). A `409` needs a call-site `conflictMessage` passed to `unwrap()` — the backend never sends exception text, so without it the user sees "Request failed with status 409" (see `categories.ts`, `accounts.ts`, `budgets.ts`). `defaultErrorMessage(err, statusOverrides?)` turns a caught error into UI text; use its `statusOverrides` for a page that special-cases one status (e.g. 404). Feature code never calls these HTTP functions itself: it uses the area's `<area>Queries` hooks (see "Data loading"), whose `mutationFn`s just call them, so `unwrap`/`conflictMessage` stay in `src/api/<area>.ts`.

## Shared components (`src/components`)

Use these instead of re-inlining the markup; each replaced copy-pasted code from every settings/list page.

- `ErrorAlert` — dismissible error banner; renders nothing for a `null` message. `AccountDetailPage`'s banner is intentionally _not_ this (non-dismissible, different spacing).
- `InlineEditActions` — the pencil → check/✕ trio for an inline-edit table row; `editLabel` is required (labels differ per page), `saveLabel` defaults to "Save", `saving` disables save/cancel.
- `ConfirmDialog` — destructive-action confirmation (delete/close/dismiss). A dialog containing a form is not a fit; keep those custom.
- `PaginationControls` — Previous/"Page X of Y"/Next for a `PagedModel`. `onPageChange` takes a functional updater (pass `setPage` directly) so rapid clicks stay correct against React's latest state; its `sx` overrides the default embedded-list spacing for standalone pages.
- `DataTableBody` — the `<TableBody>` for every list table: skeleton rows while loading, a "Could not load data (…)" row with Retry if the first fetch failed, real rows (fade-in) otherwise. Takes `state` (a `LoadState` from `useQueryState(query)`, or several merged with `combineLoadState`). `columns` must match the header; set `actionsColumn` when the last column holds icon buttons, so placeholder rows match the real row height.
- `LoadFailedNotice` — inline "could not load" text + Retry; for non-table blocks (the `BudgetsPage` report).

## Data loading

TanStack Query v5 for every read and write (ADR 0016, F019). No hand-rolled `useState` + `useEffect` + `.then()` fetching, and no `setData` patching after a write.

- **Hooks per area.** `src/api/<area>Queries.ts` exports the area's key factory (`categoryKeys.list()`, `transactionKeys.list(filter, page, size)`, all under the root `['api']` prefix from `queryClient.ts`) and its hooks (`useCategories()`, `useCreateCategory()`, ...). Pages and components use only those hooks: never a raw `useQuery`, never a hand-written key, and ESLint (`no-restricted-imports`) rejects a `src/features/**` file that imports an HTTP function (`getX`, `createX`, ...) from `src/api/<area>` (types, constants and `conflictMessage` constants are fine). Two components asking for the same hook share one request; that only works because keys come from the factory.
- **Defaults** (`createQueryClient` in `src/api/queryClient.ts`): `staleTime` 30 s, 5 min for reference data (categories, payment methods, institutions, investment taxonomy; `STALE_TIME.reference`), 0 for the pending-occurrences read (`STALE_TIME.none`: it triggers lazy catch-up, ADR 0003, so it is never served from cache); `gcTime` 10 min; refetch on window focus; `retry: false` (keeps the single-attempt UX and one F016 log line per failed request). In-memory only, no persistence. The React Query devtools are dev-only (a lazy import behind `import.meta.env.DEV` in `main.tsx`; check the production bundle with `npm run build` if you touch it).
- **Mutations invalidate everything.** A `MutationCache.onSuccess` calls `invalidateQueries()` for every successful mutation, so a write can never leave balances, net worth, allocation or budget-vs-actual stale, and there is nothing to remember per mutation. A failed mutation never invalidates. Opt out with `meta: { skipInvalidate: true }` only for a mutation that changes no data (`useDownloadExport`; `useDismissPendingOccurrence`, which refetches just the pending list itself). No optimistic updates and no `setQueryData`: rows update after the refetch (one local round trip). A hook that must wait for its refetch (`useCreateInstitution`, because `InstitutionSelect` selects the new row at once) returns it from `onSuccess`.
- **Writes and errors.** A page awaits `mutation.mutateAsync(...)` in `try/catch` and puts `defaultErrorMessage(err)` in its dismissible banner (`setError(null)` first), exactly as before; the message already carries the API function's `conflictMessage`. An edit mutation takes one argument object (`{ id, ...request }`) so `mutationFn` stays a single call.
- **Loading state.** `useQueryState(query, onError?, errorMessage?)` (`src/hooks/queryState.ts`) maps a query result to the `{ loading, loadError, reload }` that `DataTableBody`/`LoadFailedNotice` take: skeleton on `isPending` only (never on a background refetch or a paged/toggled change), stale rows kept when a refetch fails (it then reports only through `onError`, the page's banner setter), Retry = `refetch()`. `loading`/`loadError` describe the fetch itself: never derive "is it still loading" from the page's dismissible `error` banner, or dismissing the banner after a failed load brings the skeleton back. Paged and toggle-driven hooks (`useTransactions`, `useTransfers`, `useAccounts(includeClosed)`, `useNetWorthTrend`) set `placeholderData: keepPreviousData`, so the previous page stays visible while the next loads. A table that resolves ids to names (`nameLookup`) gets one state from `combineLoadState(lookupsState…, tableState)`, passed to `DataTableBody`: it stays loading until every list has arrived (rows never render with raw ids standing in for names) and reports the first failure; its Retry goes through the page's `retry()` (`setError(null)` + `combined.reload()`, passed as `onRetry`), which reloads only the sources that failed and clears the stale banner. Query data is `undefined` until loaded (was `null`); an `enabled: false` query stays `isPending`, so only use `loading` from one that is enabled.
- **Onboarding gate.** `useHasAccounts()` derives from `useAccounts(true)` (closed included): `null` while loading or failed (never "no accounts"), `false` only for a resolved empty list, `true` once the create-account mutation has invalidated it. Nothing is patched.
- **Embedded widgets need no coordination.** A widget that fetches its own data (`PendingOccurrencesWidget`, `NetWorthTrendChart`, `InvestmentAllocationChart`, the dashboard widgets) is refreshed by the same invalidation as everything else after any write, wherever it is mounted; there are no `key` counters or `reloadKey` props.

## Loading states

Use MUI `Skeleton`, not spinners or "Loading…" text. Gate every skeleton behind `useDelayedFlag` (150ms) — the local backend answers in a few ms, so an ungated skeleton just flashes — and wrap the content that replaces it in `fadeInSx` (`components/fadeIn.ts`). Both the fade and MUI's pulse animation honour `prefers-reduced-motion` (`theme.ts` overrides `MuiSkeleton`; MUI doesn't do this itself).

## Charts

No chart library: `ValueSeriesChart`, `InvestmentAllocationChart` and `NetWorthTrendChart` are hand-drawn SVG. Their React Compiler lint rules bite in charts specifically: no reassigning a `let` after render (compute running offsets with `reduce`/`slice`) and no `useMemo` over values the compiler can't preserve (compute plainly).

## Testing

- Vitest is configured entirely through `vite.config.ts`'s `test` key (`environment: 'jsdom'`, `setupFiles: ['src/test/setup.ts']`) — there is no `vitest.config.ts`. `globals` is off, so `src/test/setup.ts` calls React Testing Library's `cleanup()` itself.
- Test files are colocated (`*.test.ts` / `*.test.tsx`) and query with accessibility-first RTL queries (`getByRole`, `getByLabelText`), not test IDs.
- MSW: one handler file per aggregate in `src/mocks/handlers/<aggregate>.ts` (mirroring `src/api`), combined in `src/mocks/handlers.ts`, served by `src/mocks/server.ts` and started/reset/closed by `src/test/setup.ts` — every test gets MSW automatically. Because every successful write refetches the active queries, default handlers are backed by an in-memory `createStore(seed)` (`src/mocks/store.ts`, restored by `resetStores()` after each test): a POST/PATCH/DELETE changes what the next GET returns, so a handler that only echoed the request would let the refetch bring the seed back over a just-created row. Per-test overrides use `server.use(...)`; to inspect a request body and keep the stateful behaviour, read `await request.clone().json()` and return nothing so the default handler runs. Add a handler for every new endpoint, plus a `409` variant where it can conflict.
- **`renderWithQueryClient` / `renderHookWithQueryClient`** (`src/test/renderWithQueryClient.tsx`) replace RTL's `render`/`renderHook` for anything using a `<area>Queries` hook (`renderWithRouter` already includes it): a fresh client per test with the app's mutation-invalidation rules, `retry: false` and `gcTime: 0`. Pass the same `client` to two hook renders to test cache reuse across mounts (and use `createQueryClient()` there, since `gcTime: 0` drops the cache on unmount). Hook tests go through MSW, not mocked API modules (see `src/api/*Queries.test.tsx`); the cache rules (shared-key dedup, invalidation after success, none after failure) are covered in `categoriesQueries.test.tsx`.
- `App.test.tsx` renders the real `BrowserRouter`, which reads the jsdom URL that a previous test's navigation leaves behind: reset it with `window.history.pushState({}, '', '/')` in `beforeEach`.
- `frontend/.env.test` sets `VITE_API_BASE_URL=/api` (relative, same-origin) so the Axios base URL and MSW's relative matching resolve against the same jsdom origin.
- **Test files have no Node types** (`@types/node` isn't a dependency). `process.env` in a test passes under Vitest but fails `npm run build` with TS2591 — always run the build, not just `npm test`. Pin an env var/timezone with `vi.stubEnv('TZ', ...)` and `vi.unstubAllEnvs()` in cleanup, and pin a non-UTC zone for any date/time test: CI runs in UTC, where a UTC-vs-local bug is invisible.

## Logging

Rationale in ADR 0011. Browser logs are console-only — nothing is shipped to the backend.

- **Use `logger` (`src/utils/logger.ts`), never `console.*`** — ESLint `no-console` is an error everywhere except that file. `logger.error('what failed', err)`: pass an `Error` as context, not its message. **Never log request/response bodies, amounts or descriptions.**
- **Level**: `VITE_LOG_LEVEL` (`debug | info | warn | error | silent`; `.env.development` debug, `.env.production` warn, `.env.test` silent; unknown → `warn`). `localStorage.setItem('logLevel', 'debug')` in devtools overrides it at runtime, which is how you turn debug on in the prod build. Tests are silent by default: to assert on logging, `vi.spyOn(logger, 'warn')`, or `setLogLevel(...)` / `vi.stubEnv('VITE_LOG_LEVEL', ...)` to see real console output.
- **HTTP logging is owned by the interceptors in `src/api/client.ts`** — don't log failed requests at call sites or in hooks. They send a per-request `X-Request-Id` (the backend echoes it into its log, nginx into its own), log 4xx at `warn` and 5xx/no-response at `error`, and re-reject the original error untouched so `unwrap()`/`conflictMessage` behave as before.
- **Uncaught errors**: `installGlobalErrorLogging()` (window `error` + `unhandledrejection`) and `reactRootErrorHandlers` (`createRoot` options) are wired once in `main.tsx`. `ErrorBoundary` wraps the routed page inside `Layout` (`App.tsx`, keyed on the pathname so navigating away resets it) and deliberately doesn't log — `createRoot`'s `onCaughtError` already receives everything a boundary catches.
- Testing gotcha: dispatching an `ErrorEvent` that carries an `Error` on `window` when no listener of ours is attached makes Vitest report an unhandled test error.
