# F004 — Action Plan

**Depends on**: F001, F002, F003.

## Backend
- [ ] Add `domain/transaction/Transaction.java` (positive-amount invariant, denormalized `type`).
- [ ] Add `TransactionJpaEntity` (extends `AuditableEntity`), repository (with filter query support), adapter.
- [ ] Flyway migration `V4__transactions.sql` with FKs and indexes; nullable `recurring_template_version_id` column ahead of F007.
- [ ] Application services: create (rejecting closed accounts), edit, delete, filtered list.
- [ ] Update F003's `AccountBalanceQuery` to sum real transactions instead of returning opening balance only.
- [ ] REST controller + DTOs, filter query params.
- [ ] Tests: closed-account rejection, balance calc correctness for both asset and credit-card account types, filter combinations.

## Frontend
- [ ] `src/api/transactions.ts`.
- [ ] `src/features/transactions` — filterable list/table, create/edit form, delete.
- [ ] Embed transaction list (pre-filtered) in F003's account detail view.

## Verification
- [ ] Create transactions on both an asset account and a credit card account, confirm balances move in the correct direction on each.
- [ ] Confirm a transaction can't be created against a closed account.
- [ ] Filter by each dimension (date range, category, account, payment method) independently and combined.
