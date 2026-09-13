# F011 — Action Plan

**Depends on**: F003.

## Backend
- [ ] None — reuses F003's existing account list/create endpoints as-is.

## Frontend
- [ ] App-level "has any accounts" check on load (reuses `src/api/accounts.ts` from F003).
- [ ] Onboarding screen: account creation form with introductory framing, reusing F003's form component where practical.
- [ ] Route to the normal app shell after the first account is created.

## Verification
- [ ] On a fresh database, confirm the onboarding screen appears instead of the dashboard.
- [ ] After creating the first account, confirm the app transitions to the normal shell and doesn't show onboarding again on reload.
