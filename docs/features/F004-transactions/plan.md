# F004 — Action Plan

**Depends on**: F001, F002, F003, F015.

## Backend
- [x] Write tests first for `Transaction`'s domain rules: amount must be positive, `type` is captured at creation.
- [x] Add `domain/transaction/Transaction.java`, implementing the above to make those tests pass.
- [x] Add `TransactionJpaEntity` (extends `AuditableEntity`), repository (with filter query support), adapter.
- [x] Flyway migration `V5__transactions.sql` (see spec.md's migration-numbering note — F003 claimed `V4` first) with FKs and indexes; nullable `recurring_template_version_id` column ahead of F007.
- [x] Write tests for the create application service (rejects transactions against a closed account), then implement: create, edit, delete, filtered list.
- [x] Write tests for the updated `AccountBalanceQuery` — an expense reduces an asset account's balance and increases a credit card account's owed balance — then implement the change: sum real transactions instead of returning opening balance only.
- [x] REST controller + DTOs, filter query params.
- [x] Add the F002-deferred referenced-by-transaction delete guard (409) to `CategoryService`/`PaymentMethodService`, now that `transactions` exists to check against (CLAUDE.md's F002 status entry).

## Frontend
- [x] `src/api/transactions.ts`.
- [x] `src/features/transactions` — filterable list/table, create/edit form, delete.
- [x] Embed transaction list (pre-filtered) in F003's account detail view.

## Verification
- [ ] Create transactions on both an asset account and a credit card account, confirm balances move in the correct direction on each.
- [ ] Confirm a transaction can't be created against a closed account.
- [ ] Filter by each dimension (date range, category, account, payment method) independently and combined.
