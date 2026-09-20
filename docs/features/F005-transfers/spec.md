# F005 — Transfers

## Summary
`Transfer` between two accounts — most commonly paying a credit card statement from checking (PRD §5.5, §6.2 transfer part). Keeps spend and debt correctly separated instead of double-counting (PRD §1).

> **Extended by [F009](../F009-investment-buysell-snapshots/spec.md) ([ADR 0012](../../adr/0012-investments-as-accounts-and-transfers.md)):** a transfer between a cash account and an `INVESTMENT` account is how a buy or sell is recorded. `transfers` gains a nullable `investment_product_id` and record-only `quantity`, `unit_price` and `taxes`, the service gains investment-specific rules, and the transfer form and list gain the matching fields and a product filter. Ordinary transfers are unaffected.

## Scope
- `Transfer` entity: date, from-account, to-account, amount, mandatory description (max 150 chars), optional additional notes (max 500 chars).
- Two-sided balance effect, folded into `AccountBalanceQuery` (F003) alongside transactions.
- Transfer history view.
- Out of scope: any notion of a transfer being "categorized" or counted toward budgets — it explicitly isn't (PRD §5.5).

## Backend

### Domain
- `domain/transfer/Transfer.java`: id, `date`, `fromAccountId`, `toAccountId`, `amount` (positive), `description` (mandatory, max 150 chars), `additionalNotes` (nullable, max 500 chars). Bounded free-text fields, following the convention from F002/F003 (length check in the domain constructor/mutator, `@Size` on request DTOs, matching `varchar(n)` column) — see F004's Transaction, which adds the same two fields, for the concrete pattern.
- Invariant: `fromAccountId != toAccountId`. Both accounts must be open (not closed) at creation time — enforced via F003's `Account.isClosed()`.
- Balance effect (implemented in F003's `AccountBalanceQuery`, specified here since it's this feature's data driving it): the source account's balance decreases by `amount`. The destination account's effect depends on its type — an asset account's balance increases; a credit card account's owed-balance decreases. No category, no budget impact, no direct net-worth impact (an asset down and a liability down by the same amount nets to zero — PRD §5.5).
- No versioning, no editing after creation beyond a plain field update (like Transaction) — delete is a hard delete.

### Persistence
- `TransferJpaEntity extends AuditableEntity`; table `transfers`: `id uuid pk`, `date date not null`, `from_account_id uuid not null references accounts`, `to_account_id uuid not null references accounts`, `amount numeric not null`, `description varchar(150) not null`, `additional_notes varchar(500)`, plus audit columns. Check constraint `from_account_id <> to_account_id`.
- Migration `V7__transfers.sql` (not `V5` as originally planned — F004, which landed after this spec was written, already claimed `V5__transactions.sql` and `V6__transaction_description_and_notes.sql` by the time this feature was built; see V7's own migration header for the full story).

### API
- `POST /api/transfers`, `GET /api/transfers/{id}`, `PATCH /api/transfers/{id}`, `DELETE /api/transfers/{id}`.
- `GET /api/transfers?dateFrom=&dateTo=&accountId=` — filtered list; `accountId` matches either side (PRD §6.9 export filter semantics mirror this).

## Frontend
- Transfer creation form: date, from-account, to-account (both dropdowns from F003, closed accounts excluded, can't pick the same account twice), amount, description, optional additional notes.
- Transfer history list, filterable by account.
- Account detail view (F003) embeds transfer history alongside transaction history, both contributing to the same running-balance timeline.

## Dependencies
F001, F003 (accounts, balance calc).
