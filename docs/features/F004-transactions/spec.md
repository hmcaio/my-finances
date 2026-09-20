# F004 — Transactions

## Summary
`Transaction` CRUD, filtering, and its effect on an account's running balance (PRD §5.3, §6.1). The core ledger entry of the system.

## Scope
- `Transaction` entity: date, amount, category, account, type (income/expense), payment method, mandatory description (max 150 chars), optional additional notes (max 500 chars), optional recurring-template-version link.
- List/filter by date range, category, account, payment method.
- Wiring into `AccountBalanceQuery` (F003) so running balance reflects real activity.
- F002's referenced-by-transaction delete guard (409 when a category/payment method is in use), deferred there until this feature's `transactions` table existed to check against (see F002's spec.md) — `CategoryService`/`PaymentMethodService.delete()` now reject via `CategoryInUseException`/`PaymentMethodInUseException`.
- Out of scope: the recurring-template-generated flow itself (F007 creates transactions via this feature's application service, not a separate path).

## Backend

### Domain
- `domain/transaction/Transaction.java`: id, `date`, `amount` (positive `BigDecimal`), `categoryId`, `type` (derived from the category's type at creation time — see below), `accountId`, `paymentMethodId`, `recurringTemplateVersionId` (nullable), `description` (mandatory, max 150 chars), `additionalNotes` (nullable, max 500 chars).
- `type` is captured on the transaction at creation (denormalized, per PRD §5.3) rather than always re-read from the category, so that a category's type — already immutable per F002 — has a stable, redundant check available; this is a data-integrity belt-and-suspenders, not a new mutable field.
- Sign convention: `amount` is always stored positive; whether it increases or decreases the account's balance is derived from `type` and the account's `AccountType` (F003) at read time — an expense reduces an asset account's balance and increases a credit card account's owed balance (PRD §5.3, §5.4). This logic lives in `AccountBalanceQuery` (F003), not duplicated here.
- Editing a transaction (amount/date/category/account/payment method/description/additional notes) is a plain in-place update — no versioning (unlike Budget/RecurringTemplate). Deleting is a hard delete (a transaction has no downstream history that would be orphaned by removing it).
- `description`/`additionalNotes` are bounded free-text fields added after this feature's initial ship (originally a single optional `note`), via `domain/shared/TextFieldConstraints.MAX_DESCRIPTION_LENGTH`/`MAX_ADDITIONAL_NOTES_LENGTH` (150/500) — the same shared constants class as F002/F003's `MAX_NAME_LENGTH`, since these are the same bounded-free-text convention applied to narrative rather than taxonomy fields. Transfer (F005) and RecurringTemplate's `description` (F007) reuse the same constants.

### Persistence
- `TransactionJpaEntity extends AuditableEntity`; table `transactions`: `id uuid pk`, `date date not null`, `amount numeric not null`, `category_id uuid not null references categories`, `type text not null`, `account_id uuid not null references accounts`, `payment_method_id uuid not null references payment_methods`, `recurring_template_version_id uuid references recurring_template_versions` (nullable; FK added once F007 exists — nullable column can be added now and left unpopulated), `description varchar(150) not null`, `additional_notes varchar(500)`, plus audit columns.
- Migration `V5__transactions.sql` — not `V4`, as this spec originally said before F003 landed: F003 already claimed `V4__accounts.sql` (after F002's own out-of-band `V3__bound_name_column_lengths.sql` claimed `V3`), same renumbering story F003's own spec.md documented for its `V3`→`V4` move. Indexes on `account_id`, `category_id`, `date` (all are filter/aggregation dimensions used here and by F006/F010/F013).
- Migration `V6__transaction_description_and_notes.sql` (post-ship follow-up): adds the mandatory `description` column (backfilled `''` for any pre-existing row, then the default is dropped) and renames/narrows the original `note text` column into `additional_notes varchar(500)`.
- Reject inserting a transaction against a closed `Account` (enforced in the application service, calling into F003's `Account.isClosed()`).
- Extended by [F008](../F008-investment-accounts-products/spec.md) ([ADR 0012](../../adr/0012-investments-as-accounts-and-transfers.md)): also reject a transaction against an `INVESTMENT` account (409, `AccountTypeNotAllowedException`), on create and edit — money moves in and out of those accounts through transfers, and their value comes from snapshots. The transaction form's account dropdown excludes them.

### API
- `POST /api/transactions`, `GET /api/transactions/{id}`, `PATCH /api/transactions/{id}`, `DELETE /api/transactions/{id}`. `PATCH` is a full-replace body (every editable field required), matching F002/F003's existing update-endpoint convention rather than a partial patch.
- `GET /api/transactions?dateFrom=&dateTo=&categoryId=&accountId=&paymentMethodId=&page=&size=&sort=` — filtered, paginated list (Spring Data `Pageable`/`Page`, default 20/page, most recent first), returned as a `PagedModel` (`{content, page: {size, number, totalElements, totalPages}}`) rather than a raw `Page` — Spring's recommended envelope shape, and the first paginated list endpoint in this codebase, so this is now the convention F006+'s own list endpoints should follow.
- No separate `GET /api/accounts/{id}/balance?asOf=` endpoint was added: F003's existing `GET /api/accounts/{id}?asOf=` already returns the computed balance embedded in `AccountResponse.balance` (via `AccountBalanceQuery`), and now that this feature's transactions exist for it to sum, that endpoint is what makes the balance meaningful — a dedicated `/balance` sub-resource would just duplicate it.

## Frontend
- Transaction list/table with filter controls (date range, category, account, payment method).
- Create/edit transaction form: date, amount, category (dropdown from F002), account (dropdown from F003, closed accounts excluded), payment method (dropdown from F002), description (required), optional additional notes.
- Delete with confirmation.
- Account detail view (F003) embeds this feature's list, pre-filtered to that account.

## Dependencies
F001, F002 (categories, payment methods), F003 (accounts, balance calc target).
