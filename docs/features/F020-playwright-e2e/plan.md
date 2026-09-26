# F020 — Action Plan

**Depends on**: F001, F015. Must ship before F021.

One feature branch (`feature/f020-playwright-e2e`), one commit per phase.

## Phase 1 — Install and configure
- [x] Add `@playwright/test` (dev dependency) and the `e2e` script.
- [x] `playwright.config.ts`: `webServer` running the Vite dev server, three Chromium projects (`mobile` 390x844, `tablet` 768x1024, `desktop` 1280x800), HTML reporter, traces on first retry.
- [x] Exclude `e2e/` from Vitest and from `tsc` build output; add `e2e/tsconfig` if needed; ESLint covers the new folder.

## Phase 2 — Mock layer and helpers
- [x] `e2e/support/mockApi.ts`: `page.route` handler for `/api/` paths, built from `src/mocks` fixtures (MSW `getResponse`); unmocked `/api` requests fail the test.
- [x] `expectNoHorizontalOverflow`, `expectNavMode` helpers, with a self-check spec (test first).

## Phase 3 — Smoke suite
- [x] `e2e/smoke.spec.ts`: landing route loads, `Layout` present, no overflow, no console errors, on all three projects. Today the dashboard overflows below 1200px, so the overflow test is `test.fail` on `mobile`/`tablet` until F021 (which must remove that marker).

## Phase 4 — CI
- [x] `e2e` job in `.github/workflows/ci.yml`: install Chromium with cached browser dir, run `npm run e2e`, upload report and traces on failure.
- [x] Job runs on every push/PR after `test-frontend` (`needs`), and `build-and-push` needs it, so a red e2e run blocks publishing images.

## Phase 5 — Docs
- [x] `frontend/CLAUDE.md` section (run command, mock-by-route rule, geometry-only rule, helper location).
- [x] Root `README.md` "Project status" entry.
- [x] Tick this plan.

## Verification
- [x] `npm run e2e` green on all three projects locally.
- [x] `npm run lint && npm test && npm run build` green.
- [ ] CI `e2e` job green on the PR; a deliberately overflowing page makes it fail (checked once, not committed).
