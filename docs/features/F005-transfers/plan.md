# F005 — Action Plan

**Depends on**: F001, F003, F015.

## Backend
- [x] Write tests first for `Transfer`'s domain rules: `fromAccountId != toAccountId`, amount must be positive.
- [x] Add `domain/transfer/Transfer.java`, implementing the above to make those tests pass.
- [x] Add `TransferJpaEntity` (extends `AuditableEntity`), repository, adapter.
- [x] Flyway migration `V7__transfers.sql` with check constraint.
- [x] Write tests for the create application service (rejects closed-account transfers), then implement: create, edit, delete, filtered list.
- [x] Write tests for the updated `AccountBalanceQuery` covering asset→asset and asset→credit-card transfers (source decreases; destination increases balance or decreases owed amount depending on `AccountType`), then implement folding transfers into it.
- [ ] REST controller + DTOs.

## Frontend
- [ ] `src/api/transfers.ts`.
- [ ] `src/features/transfers` — create form, filterable history list.
- [ ] Embed transfer history in F003's account detail view alongside transactions.

## Verification
- [ ] Transfer from checking to a credit card account; confirm checking balance drops and the card's owed balance drops by the same amount, with no net-worth change.
- [ ] Transfer between two asset accounts; confirm one drops, the other rises.
- [ ] Confirm same-account and closed-account transfers are rejected.
