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
- `LoadingTableRow` — placeholder row before a table's first fetch; `variant` is `'spinner'` or `'text'` (both treatments exist on purpose).

## Pages that embed other pages' widgets

A widget that fetches its own data on mount and takes no props (`PendingOccurrencesWidget`, kept prop-less so F012's dashboard can drop it in) won't refetch when the embedding page changes what it should show. The embedding page remounts it with a `key` counter it bumps after the relevant mutation (`RecurringTemplatesPage` does this after a cap edit or template creation).

## Testing

- Vitest is configured entirely through `vite.config.ts`'s `test` key (`environment: 'jsdom'`, `setupFiles: ['src/test/setup.ts']`) — there is no `vitest.config.ts`. `globals` is off, so `src/test/setup.ts` calls React Testing Library's `cleanup()` itself.
- Test files are colocated (`*.test.ts` / `*.test.tsx`) and query with accessibility-first RTL queries (`getByRole`, `getByLabelText`), not test IDs.
- MSW: one handler file per aggregate in `src/mocks/handlers/<aggregate>.ts` (mirroring `src/api`), combined in `src/mocks/handlers.ts`, served by `src/mocks/server.ts` and started/reset/closed by `src/test/setup.ts` — every test gets MSW automatically. Default handlers echo the request rather than mutating seed data, so per-test overrides use `server.use(...)`. Add a handler for every new endpoint, plus a `409` variant where it can conflict.
- `frontend/.env.test` sets `VITE_API_BASE_URL=/api` (relative, same-origin) so the Axios base URL and MSW's relative matching resolve against the same jsdom origin.
- **Test files have no Node types** (`@types/node` isn't a dependency). `process.env` in a test passes under Vitest but fails `npm run build` with TS2591 — always run the build, not just `npm test`. Pin an env var/timezone with `vi.stubEnv('TZ', ...)` and `vi.unstubAllEnvs()` in cleanup, and pin a non-UTC zone for any date/time test: CI runs in UTC, where a UTC-vs-local bug is invisible.
