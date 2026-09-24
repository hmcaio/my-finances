# F019 — TanStack Query Migration (queries, mutations, caching)

## Summary
Replaces the frontend's hand-written data layer (`useAsyncData`, `usePagedData`, direct API calls plus `setData` patching) with TanStack Query v5, for both reads and writes. Caching, request deduplication and invalidation come with it. Frontend only: no backend, API or data-model change. Decision record: [ADR 0016](../../adr/0016-tanstack-query-client-cache.md).

Motivation is preventive (no measured latency problem in a local single-user app): fewer redundant requests, less bespoke fetch state, one consistent place for invalidation. The hard requirement is correctness: a user's own write must show up immediately and no view may show stale money after it.

## Scope
- `QueryClient` setup, provider, defaults, devtools, test helper.
- Per-area query/mutation hooks and key factories; migration of all pages and shared components to them.
- Deleting `useAsyncData`, `usePagedData`, `markHasAccounts`, the `reloadKey`/`key`-counter remounts and every fetch-in-`useEffect`.
- ESLint guardrails, tests, `frontend/CLAUDE.md`, ADR 0016, CHANGELOG.
- Out of scope: see [Non-goals](#non-goals).

## Decisions

| Decision | Why |
|---|---|
| Feature-level hooks per area (`useCategories()`, `useCreateCategory()`), not raw `useQuery` in pages and not wrappers around the old hooks. | Pages stop knowing about keys and API modules; one place per area to change. Keeping `useAsyncData` as a wrapper would leave two data layers. |
| Each `src/api/<area>.ts` keeps the HTTP functions; a sibling `<area>Queries.ts` holds hooks and the key factory. | API modules stay framework-free and testable with MSW as today; hooks are the React-facing layer. |
| Query keys come from a per-area factory (`categoryKeys.list()`, `transactionKeys.list(filters, page)`) under a root `['api']` prefix. Pages never write a raw key. | Explicit keys are what make deduplication work (two components asking for `categoryKeys.list()` share one request); derived keys would silently fragment the cache. |
| Global invalidation: `new MutationCache({ onSuccess })` calls `queryClient.invalidateQueries()` for every successful mutation; `meta: { skipInvalidate: true }` opts out. Failed mutations never invalidate. | One place, can't be forgotten in a new mutation. Coarse on purpose: one transfer changes balances, net worth, allocation and budget-vs-actual, and per-key invalidation is where stale-data bugs would come from. The data is small enough that refetching everything active is cheap. |
| `unwrap`/`conflictMessage` stay in the `src/api` functions; a `mutationFn` just calls them. Pages read `mutation.error` and use `defaultErrorMessage(err, statusOverrides?)`. | The backend never sends exception text (root `CLAUDE.md`), so the call-site message contract is unchanged and no new error abstraction is needed. |
| No optimistic updates. | After a success, global invalidation refetches and rows update within one local round trip. Avoids rollback bugs. Revisit only if a screen feels laggy. |
| Defaults: `staleTime` 30s (lists, reports), 5 min (reference data: categories, payment methods, institutions, investment taxonomy), 0 (pending-occurrences read); `gcTime` 10 min; `refetchOnWindowFocus` on; `retry: false`. | `retry: false` keeps the single-attempt UX (Retry button, `conflictMessage`) and one F016 log line per failed request. The pending-occurrences read has a side effect (lazy catch-up, ADR 0003) and must not be served from cache. |
| In-memory cache only; no `persistQueryClient`. | Persisting puts financial data in browser storage as a second uncontrolled copy and creates staleness after a restore (F018); a reload against a local server is fast. |
| UX parity is the contract: skeleton only on first load (`isPending`), stale rows kept when a refetch fails, previous page kept while the next loads (`placeholderData: keepPreviousData`), `useDelayedFlag` retained, `DataTableBody`/`LoadFailedNotice` fed through a small adapter from the query result. | The loading/error behaviour is already documented and tested (`frontend/CLAUDE.md`); the migration must not change what users see. Existing page tests are the parity check. |
| The onboarding gate (`useHasAccounts`, F011) derives from the cached accounts list (`includeClosed: true`); `markHasAccounts` is deleted. `hasAccounts` stays `null` while loading or failed, never `false`. | The gate can't drift from the data, and a failed check must never show onboarding. Creating the first account is a mutation, so global invalidation flips the gate. |
| `InstitutionSelect` migrates too. (`DashboardPage` no longer has a health check: F012's dashboard composes widgets, so there is nothing to migrate there.) | `InstitutionSelect` then shares `institutionKeys.list()` with the institutions page and sees inline-created rows through invalidation. No fetch-in-`useEffect` may remain. |
| ESLint guardrails: `@tanstack/eslint-plugin-query` (if compatible with the repo's ESLint 10; otherwise the rule below plus docs), and a rule forbidding `src/features/**` from importing `src/api/<area>` modules directly (only `<area>Queries`). | Enforces the layering so the migration doesn't erode. |
| Devtools in dev builds only, tree-shaken from production. | Cache inspection during migration; no production cost. |

## Structure

```
src/api/queryClient.ts        QueryClient factory: defaults, MutationCache invalidation
src/api/<area>.ts             HTTP functions (unchanged contract)
src/api/<area>Queries.ts      key factory + useX / useMutation hooks per area
src/components/...            DataTableBody / LoadFailedNotice adapters from query results
src/test/renderWithQueryClient.tsx   fresh QueryClient per test
```

Reference pattern is built for categories first, then the other areas follow it: accounts, institutions, payment methods, investment categories and products, investments (trades, snapshots, allocation, value series), budgets, recurring templates, transactions, transfers, net worth, dashboard, onboarding.

## Testing
- `renderWithQueryClient`: fresh `QueryClient` per test (`retry: false`, `gcTime: 0`); existing MSW page tests should pass with only the wrapper changed.
- New tests for the three cache rules: shared-key deduplication (two consumers, one request), a successful mutation invalidates cached queries, a failed mutation does not.
- Hook tests via MSW instead of mocked API modules where practical; adapter tests for the `DataTableBody` state mapping (first load, refetch error keeps rows, keepPreviousData paging).
- Onboarding gate: `null` while loading and on failure, `false` only for a resolved empty list, flips to `true` after the create-account mutation.
- Pending-occurrences read is fetched fresh every mount (`staleTime: 0`).
- `npm run lint && npm test && npm run build` green.

## Docs
- ADR 0016 and its README row; `docs/features/README.md` row.
- `frontend/CLAUDE.md`: rewrite the "Data loading" section (hooks per area, key factories, global invalidation, `skipInvalidate`, `mutation.error` handling, defaults, no optimistic updates, tests via `renderWithQueryClient`) and the layout note about `<area>Queries.ts`. Remove references to `useAsyncData`/`usePagedData`.
- Root `README.md`: "Project status" entry once built.
- `CHANGELOG.md` `[Unreleased]` entry (`**F019 — TanStack Query migration**`, user-visible: fewer reloads and faster navigation). No `Upgrade:` line (no compose, env or config change).

## Non-goals
- Any backend change: no `ETag`/`Cache-Control`, in-process cache or Redis (no measured slow report; revisit if one appears).
- Persisting the cache to `localStorage`/IndexedDB.
- Prefetching, cross-tab sync, offline support.
- Optimistic updates.
- Per-key invalidation (only where a cost is measured later).
- UI redesign: loading, error and empty states look and behave as today.
- Independent of F018 (backups).

## Open questions
None.

## Dependencies
F001 (frontend skeleton), F015 (Vitest, RTL, MSW), F016 (request logging and the Axios client interceptors that stay as they are), F011 (the onboarding gate). Touches every feature area's frontend but changes no behaviour they specify.
