# Frontend layout, routes and API clients

Read when adding a route, a feature folder, an API area or a shared module, or when looking for where a kind of file lives.

## Layout

- `src/api`: one folder per area, `src/api/<area>/`, holding the typed HTTP client `<area>.ts` and its sibling `<area>Queries.ts` with the TanStack Query key factory and hooks, plus their tests. The investment modules (categories, sub-categories, products, holdings, snapshots, allocation, value series, segments, allocation plan, FII allocation/dividends/portfolio, trade confirmation lines) share `src/api/investments/`. `src/api/core/` holds the shared plumbing: `client`, `apiError`, `queryClient`, `schemaDrift`. Generated types are in `src/api/generated/`.
- `src/components`: shared UI, grouped by role. `feedback/` for `ErrorAlert`, `ErrorBoundary`, `LoadFailedNotice`, `ConfirmDialog`, `fadeIn`; `table/` for `DataTableBody`, `PaginationControls`, `InlineEditActions`; `layout/` for `Layout`; `inputs/` for `MonthPicker` and `MonthNavigator`. Usage notes: [frontend-components.md](frontend-components.md).
- `src/features/<area>`: one folder per feature area, mapping to a route.
- `src/hooks`: `queryState` (the query-result adapter), `useHasAccounts`, `useDelayedFlag`, `colorMode/` for the provider and `useColorMode`.
- `src/utils`: framework-free helpers shared across features, e.g. `nameLookup`.
- `src/mocks`: MSW handlers and their in-memory stores. `src/test`: Vitest setup and helpers.

## Routes

**Every route in `App.tsx` is `React.lazy`-loaded** (issue #94: all pages were one eagerly-bundled >500kB chunk) behind one shared `<Suspense>` around `<Routes>`. Page components are named exports, not default, so each needs `lazy(() => import('./features/x/XPage').then((m) => ({ default: m.XPage })))`: a new route follows the same shape, not a bare `lazy(() => import(...))`. The login guard around the routes: [auth.md](auth.md).

## API clients

All clients share one Axios instance (`src/api/core/client.ts`) configured from `VITE_API_BASE_URL` (`.env.development` / `.env.production`). That base URL already includes `/api`, so call sites use bare relative paths (`apiClient.get('/categories')`), never a hand-built `${API_BASE_URL}/api/...`. Error unwrapping: [errors.md](errors.md). Feature code reaches clients through hooks: [frontend-data-loading.md](frontend-data-loading.md).
