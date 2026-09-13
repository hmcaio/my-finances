# F004 — Transactions

## Summary
`Transaction` CRUD, filtering, and its effect on an account's running balance (PRD §5.3, §6.1). The core ledger entry of the system.

## Scope
- `Transaction` entity: date, amount, category, account, type (income/expense), payment method, optional note, optional recurring-template-version link.
- List/filter by date range, category, account, payment method.
- Wiring into `AccountBalanceQuery` (F003) so running balance reflects real activity.
- Out of scope: the recurring-template-generated flow itself (F007 creates transactions via this feature's application service, not a separate path).

## Backend

### Domain
- `domain/transaction/Transaction.java`: id, `date`, `amount` (positive `BigDecimal`), `categoryId`, `type` (derived from the category's type at creation time — see below), `accountId`, `paymentMethodId`, `recurringTemplateVersionId` (nullable), `note` (nullable).
- `type` is captured on the transaction at creation (denormalized, per PRD §5.3) rather than always re-read from the category, so that a category's type — already immutable per F002 — has a stable, redundant check available; this is a data-integrity belt-and-suspenders, not a new mutable field.
- Sign convention: `amount` is always stored positive; whether it increases or decreases the account's balance is derived from `type` and the account's `AccountType` (F003) at read time — an expense reduces an asset account's balance and increases a credit card account's owed balance (PRD §5.3, §5.4). This logic lives in `AccountBalanceQuery` (F003), not duplicated here.
- Editing a transaction (amount/date/category/account/payment method/note) is a plain in-place update — no versioning (unlike Budget/RecurringTemplate). Deleting is a hard delete (a transaction has no downstream history that would be orphaned by removing it).

### Persistence
- `TransactionJpaEntity extends AuditableEntity`; table `transactions`: `id uuid pk`, `date date not null`, `amount numeric not null`, `category_id uuid not null references categories`, `type text not null`, `account_id uuid not null references accounts`, `payment_method_id uuid not null references payment_methods`, `recurring_template_version_id uuid references recurring_template_versions` (nullable; FK added once F007 exists — nullable column can be added now and left unpopulated), `note text`, plus audit columns.
- Migration `V4__transactions.sql`. Indexes on `account_id`, `category_id`, `date` (all are filter/aggregation dimensions used here and by F006/F010/F013).
- Reject inserting a transaction against a closed `Account` (enforced in the application service, calling into F003's `Account.isClosed()`).

### API
- `POST /api/transactions`, `GET /api/transactions/{id}`, `PATCH /api/transactions/{id}`, `DELETE /api/transactions/{id}`.
- `GET /api/transactions?dateFrom=&dateTo=&categoryId=&accountId=&paymentMethodId=` — filtered list, paginated.
- `GET /api/accounts/{id}/balance?asOf=` (exposed here or in F003 — implemented in F003's `AccountBalanceQuery`, but only becomes meaningful once this feature's data exists).

## Frontend
- Transaction list/table with filter controls (date range, category, account, payment method).
- Create/edit transaction form: date, amount, category (dropdown from F002), account (dropdown from F003, closed accounts excluded), payment method (dropdown from F002), optional note.
- Delete with confirmation.
- Account detail view (F003) embeds this feature's list, pre-filtered to that account.

## Dependencies
F001, F002 (categories, payment methods), F003 (accounts, balance calc target).
