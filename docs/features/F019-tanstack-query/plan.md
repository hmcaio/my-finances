# F019 — Action Plan

**Depends on**: F001, F011, F015, F016.

One feature branch (`feature/f019-tanstack-query`), one commit per phase (per area in Phase 2). Everything stays green after each commit: old and new data layers coexist until Phase 3 deletes the old hooks. If review size becomes a problem, Phases 0–1 and the rest can go as separate PRs.

## Phase 0 — Decision record
- [x] `docs/adr/0016-tanstack-query-client-cache.md` and its row in `docs/adr/README.md`.
- [x] `docs/features/F019-tanstack-query/{spec,plan}.md` and the `docs/features/README.md` row.

## Phase 1 — Infrastructure
- [x] Add `@tanstack/react-query`, `@tanstack/react-query-devtools` (dev) and `@tanstack/eslint-plugin-query` (compatible with ESLint 10: peer range `^8.57 || ^9 || ^10`, `flat/recommended` enabled).
- [x] Test first: `queryClient` factory — defaults (`retry: false`, `gcTime` 10 min, `refetchOnWindowFocus`), a successful mutation invalidates cached queries, a failed one does not, `meta.skipInvalidate` opts out.
- [x] Implement `src/api/queryClient.ts` and wrap the app in `QueryClientProvider` in `src/main.tsx`; devtools dev-only and absent from the production bundle (confirm with `npm run build`).
- [x] `src/test/renderWithQueryClient.tsx` (fresh client per test, `retry: false`, `gcTime: 0`).
- [x] Adapter from a query result to the state `DataTableBody`/`LoadFailedNotice`/`combineLoadState` consume (skeleton on `isPending` only, stale rows kept on refetch error, `keepPreviousData` for paged reads, `useDelayedFlag` retained). Test first.
- [x] ESLint: forbid `src/features/**` importing `src/api/<area>` modules (allow `<area>Queries`, `apiError`, generated types).

## Phase 2 — Reference slice, then the remaining areas
Each area: test first (hook via MSW: loads, error, mutation success invalidates), add `<area>Queries.ts` (key factory, `useX`, `useMutation` hooks), move the area's pages/components onto it, keep the existing page tests passing with only the wrapper changed, then commit.
- [x] Categories (reference pattern; also proves dedup between the categories page and any component sharing the list).
- [x] Payment methods, institutions (including `InstitutionSelect`, sharing `institutionKeys.list()`).
- [x] Accounts, and the onboarding gate: `useHasAccounts` derived from the accounts list (`includeClosed: true`), `markHasAccounts` removed; `null` while loading or failed, `false` only for a resolved empty list, flips to `true` after the create-account mutation.
- [x] Investment categories/sub-categories, investment products, investments (trades, snapshots, allocation, value series).
- [x] Budgets, transactions (filters and paging), transfers.
- [x] Recurring templates; the pending-occurrences read with `staleTime: 0` (lazy catch-up side effect, ADR 0003), confirm/dismiss mutations.
- [x] Net worth, dashboard widgets and the export page. (The spec's "dashboard health check" no longer exists in the code base, so there is no `staleTime: 0` health query to build; the `key`-counter remounts on the dashboard and budgets pages are removed instead, since global invalidation replaces them.)
- [x] After each area: `npm run lint && npm test`.

## Phase 3 — Cleanup
- [x] Delete `useAsyncData`, `usePagedData`, their tests, `combineLoadState` if now unused, and any `setData`/`markHasAccounts` remains.
- [x] Grep to confirm no `useEffect`-based fetching and no direct `src/api/<area>` import from `src/features/**` remains.
- [x] Tests for the cache rules if not already covered: shared-key dedup (one request for two consumers), invalidation after success, none after failure.
- [x] `npm run lint && npm test && npm run build`.

## Phase 4 — Docs
- [x] `frontend/CLAUDE.md`: rewrite "Data loading" (hooks per area, key factories, global invalidation and `skipInvalidate`, `mutateAsync` + `try/catch` error handling with `defaultErrorMessage`, defaults, no optimistic updates, `renderWithQueryClient`), update the layout note and drop `useAsyncData`/`usePagedData` mentions.
- [x] Root `README.md` "Project status" entry.
- [x] `CHANGELOG.md` `[Unreleased]` entry (`**F019 — TanStack Query migration**`); the PR link is added in a follow-up commit once the PR is open (still to do).
- [x] Tick this plan.

## Verification
- [x] `npm run lint && npm test && npm run build` green. Every pre-existing page test passes; besides the wrapper change, tests that overrode a write endpoint with a fixed response now record the body and fall through to the (now stateful) default handler, and a few assertions moved to `waitFor` (see the report of this feature).
- [x] Manual, dev: navigating between pages that share a list (categories, accounts, institutions) makes one request per staleness window (check the Network tab and devtools). (Checked in headless Chromium (Playwright) against a throwaway DB: after the dashboard loaded, Accounts -> Budgets -> Dashboard -> Accounts -> Dashboard sent only the new endpoints once (`/institutions`, `/budgets`); accounts, categories and the rest were served from cache.)
- [x] Manual: create, edit and delete in each area — every view (balances, net worth, allocation, budget-vs-actual) reflects the change without a reload; a failed write (e.g. a 409) shows its `conflictMessage` and leaves cached data untouched. (Checked in headless Chromium: confirming a pending occurrence, and adding then deleting a transaction from the Transactions page, refreshed spend by category, budget vs. actual, balances and net worth with no reload (no navigation); a duplicate category name (409) showed its `conflictMessage`, sent only the POST and left the list unchanged. Not exercised: edits, and areas beyond recurring/transactions/categories.)
- [x] Manual: first-run onboarding still appears on an empty DB, disappears right after the first account is created, and never appears when the accounts request fails. (Checked in headless Chromium: onboarding appeared on an empty DB and disappeared right after the first account was created (POST, then one accounts refetch, no reload); with the accounts request failing the app showed "Could not load data" + Retry, never onboarding, and Retry loaded the app.)
- [x] Manual: stop the backend — lists with cached data keep their rows and show the error; first-load failure shows the "Could not load data" row with a working Retry. (Checked in headless Chromium by aborting requests rather than stopping the process: first-load failure on Institutions showed the error with a working Retry; a stale Accounts page whose refetch failed kept its 2 rows, showed "Network Error" and no skeleton.)
- [x] Manual: paging and filtering keep the previous page visible while the next loads; no skeleton flicker. (Checked in headless Chromium with the paged read delayed 2s: the previous page's rows stayed visible with no skeleton while the next page and a category filter loaded.)
- [x] Manual: pending recurring occurrences are refetched on every visit. (Checked in headless Chromium: `/recurring-templates/pending` was requested on every Dashboard visit (2 requests over 2 visits). The dashboard health check does not exist (see Decisions), so nothing to check there.)
- [x] Production build has no devtools code (`grep` of `dist/` for `ReactQueryDevtools`/`react-query-devtools`/`tsqd` finds nothing).
