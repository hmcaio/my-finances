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

`src/api` (typed HTTP clients, one module per aggregate; generated types in `src/api/generated/`), `src/components` (shared UI), `src/features/<area>` (one folder per feature area, mapping to a route), `src/utils` (framework-free helpers shared across features, e.g. `nameLookup`), `src/mocks` (MSW handlers) and `src/test` (Vitest setup).

## API clients

All clients share one Axios instance (`src/api/client.ts`) configured from `VITE_API_BASE_URL` (`.env.development` / `.env.production`). That base URL already includes `/api`, so call sites use bare relative paths (`apiClient.get('/categories')`), never a hand-built `${API_BASE_URL}/api/...`. Errors are unwrapped through `unwrap()`/`ApiError` in `src/api/apiError.ts` (Axios throws on non-2xx). A `409` needs a call-site `conflictMessage` passed to `unwrap()` — the backend never sends exception text, so without it the user sees "Request failed with status 409" (see `categories.ts`, `accounts.ts`, `budgets.ts`). `defaultErrorMessage(err, statusOverrides?)` turns a caught error into UI text; use its `statusOverrides` for a page that special-cases one status (e.g. 404).

## Shared components (`src/components`)

Use these instead of re-inlining the markup; each replaced copy-pasted code from every settings/list page.

- `ErrorAlert` — dismissible error banner; renders nothing for a `null` message. `AccountDetailPage`'s banner is intentionally *not* this (non-dismissible, different spacing).
- `InlineEditActions` — the pencil → check/✕ trio for an inline-edit table row; `editLabel` is required (labels differ per page), `saveLabel` defaults to "Save", `saving` disables save/cancel.
- `ConfirmDialog` — destructive-action confirmation (delete/close/dismiss). A dialog containing a form is not a fit; keep those custom.
- `PaginationControls` — Previous/"Page X of Y"/Next for a `PagedModel`. `onPageChange` takes a functional updater (pass `setPage` directly) so rapid clicks stay correct against React's latest state; its `sx` overrides the default embedded-list spacing for standalone pages.
- `DataTableBody` — the `<TableBody>` for every list table: skeleton rows while loading, a "Could not load data (…)" row with Retry if the first fetch failed, real rows (fade-in) otherwise. Takes `state` (the non-data part of a `useAsyncData`/`usePagedData` result, i.e. the rest-spread). `columns` must match the header; set `actionsColumn` when the last column holds icon buttons, so placeholder rows match the real row height.
- `LoadFailedNotice` — inline "could not load" text + Retry; for non-table blocks (the `BudgetsPage` report).

## Data loading

Fetch with `useAsyncData(fetcher, deps, { onError: setError })` (or `usePagedData` for a `PagedModel` endpoint), not a hand-rolled `useState` + `useEffect` + `.then().catch()`. Destructure `data`/`setData` for the page's optimistic edits and spread the rest (`...categoriesState`) into `DataTableBody`. `loading`/`loadError` describe the fetch itself — never derive "is it still loading" from the page's dismissible `error` banner state, or dismissing the banner after a failed load brings the skeleton back. A failed refetch over existing data keeps the stale rows and only reports through `onError`. A table that resolves ids to names (`nameLookup`) gets one state from `combineLoadState(lookupsState…, tableState)`, passed to `DataTableBody`: it stays loading until every list has arrived (rows never render with raw ids standing in for names — `nameLookup`'s id fallback is only a safety net) and reports the first failure. Its Retry goes through the page's `retry()` (`setError(null)` + `combined.reload()`, passed as `onRetry`), which reloads only the sources that failed and clears the stale banner.

## Loading states

Use MUI `Skeleton`, not spinners or "Loading…" text. Gate every skeleton behind `useDelayedFlag` (150ms) — the local backend answers in a few ms, so an ungated skeleton just flashes — and wrap the content that replaces it in `fadeInSx` (`components/fadeIn.ts`). Both the fade and MUI's pulse animation honour `prefers-reduced-motion` (`theme.ts` overrides `MuiSkeleton`; MUI doesn't do this itself).

## Pages that embed other pages' widgets

A widget that fetches its own data on mount and takes no props (`PendingOccurrencesWidget`, kept prop-less so F012's dashboard can drop it in) won't refetch when the embedding page changes what it should show. The embedding page remounts it with a `key` counter it bumps after the relevant mutation (`RecurringTemplatesPage` does this after a cap edit or template creation).

## Testing

- Vitest is configured entirely through `vite.config.ts`'s `test` key (`environment: 'jsdom'`, `setupFiles: ['src/test/setup.ts']`) — there is no `vitest.config.ts`. `globals` is off, so `src/test/setup.ts` calls React Testing Library's `cleanup()` itself.
- Test files are colocated (`*.test.ts` / `*.test.tsx`) and query with accessibility-first RTL queries (`getByRole`, `getByLabelText`), not test IDs.
- MSW: one handler file per aggregate in `src/mocks/handlers/<aggregate>.ts` (mirroring `src/api`), combined in `src/mocks/handlers.ts`, served by `src/mocks/server.ts` and started/reset/closed by `src/test/setup.ts` — every test gets MSW automatically. Default handlers echo the request rather than mutating seed data, so per-test overrides use `server.use(...)`. Add a handler for every new endpoint, plus a `409` variant where it can conflict.
- `frontend/.env.test` sets `VITE_API_BASE_URL=/api` (relative, same-origin) so the Axios base URL and MSW's relative matching resolve against the same jsdom origin.
- **Test files have no Node types** (`@types/node` isn't a dependency). `process.env` in a test passes under Vitest but fails `npm run build` with TS2591 — always run the build, not just `npm test`. Pin an env var/timezone with `vi.stubEnv('TZ', ...)` and `vi.unstubAllEnvs()` in cleanup, and pin a non-UTC zone for any date/time test: CI runs in UTC, where a UTC-vs-local bug is invisible.

## Logging

Rationale in ADR 0011. Browser logs are console-only — nothing is shipped to the backend.

- **Use `logger` (`src/utils/logger.ts`), never `console.*`** — ESLint `no-console` is an error everywhere except that file. `logger.error('what failed', err)`: pass an `Error` as context, not its message. **Never log request/response bodies, amounts or descriptions.**
- **Level**: `VITE_LOG_LEVEL` (`debug | info | warn | error | silent`; `.env.development` debug, `.env.production` warn, `.env.test` silent; unknown → `warn`). `localStorage.setItem('logLevel', 'debug')` in devtools overrides it at runtime, which is how you turn debug on in the prod build. Tests are silent by default: to assert on logging, `vi.spyOn(logger, 'warn')`, or `setLogLevel(...)` / `vi.stubEnv('VITE_LOG_LEVEL', ...)` to see real console output.
- **HTTP logging is owned by the interceptors in `src/api/client.ts`** — don't log failed requests at call sites or in hooks. They send a per-request `X-Request-Id` (the backend echoes it into its log, nginx into its own), log 4xx at `warn` and 5xx/no-response at `error`, and re-reject the original error untouched so `unwrap()`/`conflictMessage` behave as before.
- **Uncaught errors**: `installGlobalErrorLogging()` (window `error` + `unhandledrejection`) and `reactRootErrorHandlers` (`createRoot` options) are wired once in `main.tsx`. `ErrorBoundary` wraps the routed page inside `Layout` (`App.tsx`, keyed on the pathname so navigating away resets it) and deliberately doesn't log — `createRoot`'s `onCaughtError` already receives everything a boundary catches.
- Testing gotcha: dispatching an `ErrorEvent` that carries an `Error` on `window` when no listener of ours is attached makes Vitest report an unhandled test error.
