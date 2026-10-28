# Frontend

React + TypeScript SPA for My Finances, built with Vite and Material UI. TanStack Query ([ADR 0016](../docs/adr/0016-tanstack-query-client-cache.md)) for every read/write, React Router for navigation, Axios through one centralized API client.

See [CLAUDE.md](CLAUDE.md) for the command list and the rules every frontend task needs, and [docs/conventions/](../docs/conventions/README.md) for the folder layout, data-loading/testing conventions and the responsive-layout rules ([ADR 0019](../docs/adr/0019-responsive-layout-strategy.md)). The product spec is [docs/PRD.md](../docs/PRD.md); the repo root [README.md](../README.md) covers running the whole stack together.

## Running it

Needs the backend running (`./gradlew bootRun` from `backend/`, or the whole stack via `docker compose --profile full up -d` from the repo root) and the Node version pinned in `.nvmrc`.

```
npm install   # first time
npm run dev   # http://localhost:5173
```

## Testing

```
npm run lint          # ESLint
npm run format:check  # Prettier
npm test              # Vitest + React Testing Library + MSW
npm run build          # typecheck + production build — also catches issues npm test alone misses (no Node types in tests)
npm run e2e            # Playwright layout checks (mobile/tablet/desktop), needs `npx playwright install chromium` once
```

API types are generated, not hand-written: `npm run generate-api-types -- --offline` regenerates `src/api/generated/schema.ts` from the backend's OpenAPI spec with no running backend, login or password (it needs Docker and a JDK: a Gradle task boots the app against a throwaway Postgres for one request). Against a running backend, `AUTH_PASSWORD=... npm run generate-api-types` signs in first with `AUTH_USERNAME`, or the one in the repo `.env`. Rerun it after pulling or rebasing a branch that touched backend controllers.
