# 0016. Client-side query cache with TanStack Query and coarse invalidation; no server cache until measured

Status: Accepted
Date: 2026-09-24

## Context
The frontend fetched with a hand-written `useAsyncData`/`usePagedData` pair over plain Axios: no request deduplication, no reuse between pages (every navigation refetched, and each page fetched its own copy of shared lists like categories), and every write was a bare API call followed by a manual `setData` patch. The backend has no cache and sends no cache headers.

The app is local, single-user and small (PRD §7.3), so there is no measured latency problem. The motivation is preventive: fewer redundant requests, less hand-written fetch state, and one consistent place for invalidation. The data is financial and a user's own write must show up immediately, so a wrong-but-fast cache is the real risk.

## Decision
- **TanStack Query (v5) for all reads and writes**, replacing `useAsyncData`/`usePagedData`. Frontend only.
- **Layering**: each `src/api/<area>.ts` keeps the HTTP functions (including `unwrap`/`conflictMessage`); a sibling `<area>Queries.ts` exports the `useX`/`useMutation` hooks and that area's query-key factory under a root `['api']` prefix. Pages use only those hooks, never raw keys or the API modules; ESLint enforces it.
- **Coarse invalidation**: a global `MutationCache.onSuccess` invalidates every query after any successful mutation (opt-out via `meta.skipInvalidate`). Failed mutations never invalidate. No optimistic updates.
- **One cache layer.** No `ETag`/`Cache-Control` and no in-process or Redis cache on the backend. Conditional GETs would only save body transfer on localhost, and a second layer would bring a second invalidation problem.
- **Short staleness, in memory only**: `staleTime` 30s for lists and reports, 5 min for reference data, 0 for endpoints with side effects or time dependence (the pending-occurrences read that triggers lazy catch-up, ADR 0003). `retry: false`, refetch on window focus, no persistence to browser storage.

## Consequences
- Shared reads (categories, accounts, institutions) are fetched once per staleness window regardless of how many components use them.
- Any successful write refetches every active query. That is deliberately wasteful: at this data size it is cheap, and it removes the class of bug where a derived view (net worth, balances, allocation, budget-vs-actual) misses an invalidation. Refine to per-key invalidation only where a cost is measured.
- `retry: false` keeps the existing single-attempt error UX (Retry buttons, `conflictMessage`) and one log line per failed request (ADR 0011).
- The onboarding gate derives from the cached account list instead of a patched boolean, so it can't drift from the data (F011).
- Server-side caching stays available later, and is warranted only by a measured slow computed report. If the app is ever served over a real network (the future VPS scope feature), revisit `ETag` and persistence together with authentication.
- A new dependency (`@tanstack/react-query`, plus dev-only devtools and an ESLint plugin) and a one-time migration touching about 21 read sites and 22 write sites.
