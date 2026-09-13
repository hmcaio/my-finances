# F005 — Action Plan

**Depends on**: F001, F003.

## Backend
- [ ] Add `domain/transfer/Transfer.java` (distinct-accounts invariant, positive amount).
- [ ] Add `TransferJpaEntity` (extends `AuditableEntity`), repository, adapter.
- [ ] Flyway migration `V5__transfers.sql` with check constraint.
- [ ] Application services: create (rejecting closed accounts, same-account transfers), edit, delete, filtered list.
- [ ] Update F003's `AccountBalanceQuery` to fold in transfers (source decreases; destination increases or decreases owed balance depending on `AccountType`).
- [ ] REST controller + DTOs.
- [ ] Tests: same-account rejection, closed-account rejection, balance effect correctness for asset→asset and asset→credit-card transfers.

## Frontend
- [ ] `src/api/transfers.ts`.
- [ ] `src/features/transfers` — create form, filterable history list.
- [ ] Embed transfer history in F003's account detail view alongside transactions.

## Verification
- [ ] Transfer from checking to a credit card account; confirm checking balance drops and the card's owed balance drops by the same amount, with no net-worth change.
- [ ] Transfer between two asset accounts; confirm one drops, the other rises.
- [ ] Confirm same-account and closed-account transfers are rejected.
