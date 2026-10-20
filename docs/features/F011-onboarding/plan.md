# F011 — Action Plan

**Depends on**: F003, F015, F017.

## Backend
- [x] None — reuses F003's account list/create endpoints and F017's institution endpoints as-is.

## Frontend
- [x] App-level "has any accounts" check on load (reuses `src/api/accounts.ts` from F003): `useHasAccounts` calls `getAccounts(true)`; while it loads (skeleton after 150ms) or has failed (`LoadFailedNotice` + Retry) neither onboarding nor the shell renders.
- [x] Onboarding screen: account creation form with introductory framing, reusing F003's form (extracted into `AccountCreateForm`, now shared with `AccountsPage`) and F017's `InstitutionSelect`.
- [x] Route to the normal app shell after the first account is created.

## Verification
- [x] On a fresh database, confirm the onboarding screen appears instead of the dashboard. (Verified against a throwaway Postgres + backend: `GET /api/accounts?includeClosed=true` returns `[]`; the UI switch is covered by `App.test.tsx`, no browser was driven.)
- [x] After creating the first account, confirm the app transitions to the normal shell and doesn't show onboarding again on reload. (Backend verified: after `POST /api/accounts` the same list is non-empty, so a reload passes the gate; UI transition covered by `App.test.tsx`.)
