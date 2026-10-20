# F015 — Frontend Test Tooling

## Summary
Sets up automated frontend testing: Vitest (test runner, Vite-native — reads `vite.config.ts` directly rather than a separate config file), React Testing Library (component tests), and MSW (mocks API calls at the network level, intercepting real `fetch`/Axios requests instead of mocking modules). Unblocks F014's CI `test-frontend` job, which currently skips gracefully because no `test` script exists yet (it checks for one at runtime rather than hardcoding `npm test`). Also backfills real test coverage for F002 (already merged to `develop` without any frontend tests, since this tooling didn't exist yet) — F002's components and API clients are the first real proof the pipeline works, not a throwaway smoke test.

## Scope
- Vitest installed and configured via `vite.config.ts`'s `test` key (jsdom environment), so it shares Vite's existing path/plugin resolution instead of a second config file that could drift from it.
- React Testing Library (`@testing-library/react`, `@testing-library/jest-dom`, `@testing-library/user-event`).
- MSW (`msw`), Node-mode `setupServer` — intercepts requests at the network level so components under test call the real `apiClient` (F002's centralized Axios instance, `src/api/client.ts`) exactly as they would in production, receiving mocked responses instead of hitting a real backend.
- Mock handler organization: one file per aggregate under `src/mocks/handlers/` (mirrors `src/api`'s one-module-per-aggregate convention), combined into `src/mocks/handlers.ts`, consumed by one `src/mocks/server.ts` (`setupServer(...handlers)`) wired into Vitest's global setup — every test file gets it automatically, no per-file import needed.
- A `"test"` script in `package.json` (non-watch, CI-friendly) so F014's CI `test-frontend` job's existing runtime check picks it up with no CI workflow change required.
- Real test coverage for F002's frontend: `src/mocks/handlers/categories.ts` and `paymentMethods.ts`, API-client tests for `src/api/categories.ts`/`paymentMethods.ts`, and component tests for `CategoriesPage`/`PaymentMethodsPage` — this is both the feature's real deliverable and its own proof the tooling actually works end to end, not a separate contrived smoke test.
- Out of scope: any F003+ domain testing (each of those features writes its own tests against this tooling, per their own updated `plan.md`).

## Frontend

### Test runner (Vitest)
- `vitest` (`jsdom` as the test environment; `@vitest/ui` optional, not required).
- Config lives in `vite.config.ts`'s `test` key: `environment: 'jsdom'`, `setupFiles: ['src/test/setup.ts']`. No separate `vitest.config.ts` — Vitest reads the Vite config directly, so there's exactly one place build/test config can drift from each other, not two.
- `src/test/setup.ts`: imports `@testing-library/jest-dom`'s matchers, and starts/resets/closes the MSW server around the test lifecycle (`beforeAll`/`afterEach: server.resetHandlers()`/`afterAll: server.close()`) — this is what makes every test file get MSW automatically without importing it itself.

### Component tests (React Testing Library)
- `@testing-library/react`, `@testing-library/jest-dom`, `@testing-library/user-event`.
- Convention: test files colocated next to what they test, named `*.test.tsx` (e.g. `src/features/categories/CategoriesPage.test.tsx`). Query via RTL's accessibility-first queries (`getByRole`, `getByLabelText`) rather than test-id soup, per RTL's own philosophy — worth stating explicitly since it's easy to default to a less accessible query style otherwise.

### API mocking (MSW)
- `msw`, Node-mode `setupServer` (not the browser service-worker mode — that's for running the app itself against mocks in dev/Storybook, not needed here).
- `src/mocks/handlers/<aggregate>.ts` — one file per aggregate (e.g. `categories.ts`, `paymentMethods.ts`), each exporting an array of `http.get/post/patch/delete(...)` handlers matching that aggregate's real endpoints (F002's `/api/categories`, `/api/payment-methods`, etc.), including a handler variant returning `409` so the delete-conflict message path (F002 spec's `conflictMessage`) has something real to test against even though the backend can't produce a real `409` yet (the referenced-by-transaction guard is deferred to F004).
- `src/mocks/handlers.ts` — combines every aggregate's handlers into one array (`export const handlers = [...categoriesHandlers, ...paymentMethodsHandlers, ...]`).
- `src/mocks/server.ts` — `setupServer(...handlers)`, imported once by `src/test/setup.ts`.
- F003+ each add their own `src/mocks/handlers/<aggregate>.ts` and append to `handlers.ts`'s combined array — the only shared-file touch is that one-line addition, so independently-built features don't collide on handler content.

### F002 backfill (this feature's primary test content)
- **API-client tests** (`src/api/categories.test.ts`, `src/api/paymentMethods.test.ts`): each of `getCategories`/`createCategory`/`renameCategory`/`deleteCategory` (and the payment-method equivalents) against the matching MSW handler — success paths, and the `409` → `conflictMessage` mapping (`apiError.ts`'s `unwrap`/`ApiError`) using the `409` handler variant above.
- **Component tests** (`src/features/categories/CategoriesPage.test.tsx`, `src/features/paymentMethods/PaymentMethodsPage.test.tsx`): render the seeded list from the MSW-mocked `GET`, add a new entry (fill the form, submit, assert it appears), rename inline, delete (assert it's removed), and the `409`-conflict case surfacing the actionable message in the UI rather than a generic error.

## Dependencies
F001 (Vite/React/TypeScript scaffolding this installs into) and F002 (the components/API clients this backfills tests for — already merged to `develop`).
