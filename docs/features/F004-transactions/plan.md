# F004 — Action Plan

**Depends on**: F001, F002, F003.

## Backend
- [ ] Write tests first for `Transaction`'s domain rules: amount must be positive, `type` is captured at creation.
- [ ] Add `domain/transaction/Transaction.java`, implementing the above to make those tests pass.
- [ ] Add `TransactionJpaEntity` (extends `AuditableEntity`), repository (with filter query support), adapter.
- [ ] Flyway migration `V4__transactions.sql` with FKs and indexes; nullable `recurring_template_version_id` column ahead of F007.
- [ ] Write tests for the create application service (rejects transactions against a closed account), then implement: create, edit, delete, filtered list.
- [ ] Write tests for the updated `AccountBalanceQuery` — an expense reduces an asset account's balance and increases a credit card account's owed balance — then implement the change: sum real transactions instead of returning opening balance only.
- [ ] REST controller + DTOs, filter query params.

## Frontend
- [ ] `src/api/transactions.ts`.
- [ ] `src/features/transactions` — filterable list/table, create/edit form, delete.
- [ ] Embed transaction list (pre-filtered) in F003's account detail view.

## Verification
- [ ] Create transactions on both an asset account and a credit card account, confirm balances move in the correct direction on each.
- [ ] Confirm a transaction can't be created against a closed account.
- [ ] Filter by each dimension (date range, category, account, payment method) independently and combined.
