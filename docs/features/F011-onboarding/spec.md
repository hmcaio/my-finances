# F011 — Onboarding

## Summary
First-run flow: create at least one account, setting its opening balance and date (PRD §6.7). A thin frontend flow — no new backend entity or endpoint beyond what F003 already provides.

## Scope
- Detect "no accounts exist yet" state and route the user into account creation before showing the rest of the app.
- Out of scope: any other setup step (categories/payment methods are pre-seeded by F002, not part of onboarding).

## Backend
- No new endpoints. `GET /api/accounts` (F003) with an empty result is the signal the frontend uses to decide whether onboarding is needed. No "onboarding completed" flag is persisted — the app is always in "onboarding state" whenever zero accounts exist, and always past it once at least one exists, which is simpler and can't drift out of sync with reality.

## Frontend
- App-level check on load: if `GET /api/accounts` (including closed) returns empty, render the onboarding flow instead of the normal app shell.
- Onboarding flow: a focused version of F003's create-account form (name, optional institution via F017's `InstitutionSelect` — a fresh install has none, so the inline "Add “X”" create is the path a new user takes — type, opening balance, opening balance date) with framing copy explaining this is the starting point for tracking.
- On successful creation, transition into the normal app (dashboard, etc.).

## Dependencies
F003 (account creation), F017 (institution picker).
