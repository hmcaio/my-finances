# Frontend (React / TypeScript / Vite)

Cross-stack rules (bounded text, money, errors, auth, logging, API types) are in the root `CLAUDE.md`.

## Commands (run from `frontend/`)

```
npm install                # first time (.npmrc sets legacy-peer-deps for openapi-typescript)
npm run build              # typecheck + production build; run it, not just `npm test` (test files have no Node types: `process.env` passes Vitest but fails with TS2591)
npm run format / format:check   # Prettier (write Prettier only on files you touched)
npm test                   # Vitest, non-watch, CI-friendly
npm run e2e                # Playwright layout checks (mobile/tablet/desktop)
npm run generate-api-types -- --offline  # regenerate src/api/generated/schema.ts: Docker + JDK, no backend, no login
npm run generate-api-types               # the same from a running backend's /v3/api-docs (needs AUTH_PASSWORD)
```

## Rules for every frontend task

- **TanStack Query v5 carries every read and write, through the area's `src/api/<area>/<area>Queries` hooks** (ESLint rejects raw `useQuery`/`useMutation` in `src/features` and `src/components`, and HTTP-function imports in `src/features`). Every successful mutation invalidates all queries, so rows update after the refetch: no `setData`, `setQueryData` or optimistic patching.
- **Every user-visible string goes through `react-i18next`** (English + Brazilian Portuguese, full coverage at ship), and numbers, dates and money through `src/i18n/format.ts`, not `.toFixed()` or a hardcoded locale.

## Conventions by area

Read the file when you touch the area.

- A route (named-export pages behind `React.lazy`), feature folder, API area or shared module, and where a kind of file lives → `docs/conventions/frontend-structure.md`.
- A read, write, paged list, self-fetching widget, loading skeleton or load-failure state → `docs/conventions/frontend-data-loading.md`.
- A list page, error banner, confirmation, month field or pagination (the shared components to reuse) → `docs/conventions/frontend-components.md`.
- A list table, form dialog, filter row, inline row edit, or any layout that must fit 360px → `docs/conventions/frontend-responsive.md`.
- A chart or donut → `docs/conventions/frontend-charts.md`.
- A user-visible string, translation namespace, seeded-name or enum label, formatted number/date/money → `docs/conventions/frontend-i18n.md`.
- A Vitest test, MSW handler (every new endpoint needs one) or seed fixture → `docs/conventions/frontend-testing.md`.
- A Playwright spec, or a jsdom test that renders `Layout` → `docs/conventions/frontend-e2e.md`.
- A top-level data-fetching component, the login guard, session handling → `docs/conventions/auth.md`.
