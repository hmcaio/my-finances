# F015 — Action Plan

**Depends on**: F001, F002.

## Frontend
- [ ] Add Vitest (`vitest`) and `jsdom`; configure via `vite.config.ts`'s `test` key (`environment: 'jsdom'`, `setupFiles: ['src/test/setup.ts']`).
- [ ] Add React Testing Library (`@testing-library/react`, `@testing-library/jest-dom`, `@testing-library/user-event`).
- [ ] Add MSW (`msw`); set up `src/mocks/server.ts` (Node `setupServer`) and `src/mocks/handlers.ts` (combined array).
- [ ] Add `src/test/setup.ts`: import `@testing-library/jest-dom` matchers, wire the MSW server's `beforeAll`/`afterEach: resetHandlers()`/`afterAll: close()` lifecycle.
- [ ] Add `"test": "vitest run"` to `package.json`; verify F014's CI `test-frontend` job picks it up with no workflow changes needed.
- [ ] Add `src/mocks/handlers/categories.ts` and `src/mocks/handlers/paymentMethods.ts` (success-path handlers for every endpoint, plus a `409` variant for the delete-conflict case), appended into `src/mocks/handlers.ts`.
- [ ] Write API-client tests: `src/api/categories.test.ts`, `src/api/paymentMethods.test.ts` — every function's success path, plus the `409` → `conflictMessage` mapping.
- [ ] Write component tests: `src/features/categories/CategoriesPage.test.tsx`, `src/features/paymentMethods/PaymentMethodsPage.test.tsx` — list render, add, rename, delete, and the `409`-conflict message surfacing in the UI.

## Verification
- [ ] `npm run test` passes locally — all F002 backfill tests green.
- [ ] Push this branch and confirm `.github/workflows/ci.yml`'s frontend test step actually runs the new tests (not skipping) instead of gracefully no-oping.
- [ ] `npm run lint` and `npm run build` still pass, unaffected by the new devDependencies/config.
