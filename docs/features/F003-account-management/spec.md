# F003 — Account Management

## Summary
`Account` CRUD, types, opening balance, running balance calculation, and closing (PRD §5.4, §6.2 minus the transfer parts, which are F005). This is the central entity almost every other feature references.

## Scope
- `Account` entity: name, institution, type, opening balance/date, closed date.
- Running balance calculation (depends on `Transaction` from F004 and `Transfer` from F005 — the formula is specified here, but full correctness lands once those features exist; this feature can compute balance from opening balance alone until then).
- Closing an account, including auto-deactivating dependent `RecurringTemplate`s (that logic lives in F007, invoked from here via a domain event or port so F003 doesn't need to know F007's internals up front).
- Out of scope: transfers themselves (F005), transactions themselves (F004).

## Backend

### Domain
- `domain/account/Account.java`: id, `name`, `institution` (nullable), `type` (`AccountType`: `CHECKING`, `SAVINGS`, `CASH_WALLET`, `CREDIT_CARD`), `openingBalance`, `openingBalanceDate`, `closedDate` (nullable).
- Invariants: `openingBalance`/`openingBalanceDate` set once at creation, immutable afterward (changing history retroactively would silently rewrite every past balance/net-worth calculation — if the user made a data-entry mistake, the fix is deleting and recreating the account before any activity exists, not editing it after the fact). `close()` behavior method sets `closedDate`; a closed account exposes `isClosed()` and rejects any attempt to post new activity to it (enforced here at the domain level, not just at the API layer).
- Running balance is **not** stored on the entity — it's computed by an application-layer query (`AccountBalanceQuery` or similar) that sums transactions (F004) and transfers (F005) on top of the opening balance, as of a given date. Until F004/F005 exist, this query trivially returns the opening balance.

### Persistence
- `AccountJpaEntity extends AuditableEntity`; table `accounts`: `id uuid pk`, `name varchar(100) not null`, `institution varchar(100)`, `type text not null`, `opening_balance numeric(19,2) not null`, `opening_balance_date date not null`, `closed_date date`, plus audit columns. `name`/`institution` are bounded via `NameConstraints.MAX_NAME_LENGTH` (domain constructor/mutator check + `@Size(max=...)` on the request DTOs + `varchar(100)` column), the same pattern F002 uses for `Category`/`PaymentMethod` — a deliberate minor extension of that convention, since `Account.name`/`institution` are also free text entered by the user, even though `Account` itself isn't a flat taxonomy entity.
- Migration `V4__accounts.sql` (not `V3` as originally planned — `V3` was claimed by F002's `V3__bound_name_column_lengths.sql`, a security-audit fix migration added after F002 shipped and before F003 started).

### API
- `POST /api/accounts` — create (name, institution, type, opening balance, opening balance date).
- `GET /api/accounts` — list, with a query param to include/exclude closed accounts (default: exclude, per PRD §5.4 — closed accounts drop out of "create new" pickers and the live widget but stay browsable).
- `GET /api/accounts/{id}` — detail, including computed running balance as of "now" (or an optional `asOf` query param).
- `PATCH /api/accounts/{id}` — edit name/institution only (type and opening balance/date are immutable per the domain invariant above).
- `POST /api/accounts/{id}/close` — sets `closedDate`; publishes an "account closed" event/port call so F007 can deactivate dependent recurring templates without F003 depending on F007's package.
- No `DELETE` endpoint — accounts are never hard-deleted (PRD §5.4/§8), only closed.

## Frontend
- Account list view: name, institution, type, current running balance; toggle to show/hide closed accounts.
- Create/edit account form (type and opening balance/date fields disabled once the account exists).
- Close-account action with a confirmation dialog explaining it's not reversible through the UI (no "reopen" flow specified by the PRD) and that any recurring bills on it will stop.
- Account detail view: running balance, transaction/transfer history (populated once F004/F005 exist).

## Dependencies
F001. (F004 and F005 depend on this feature; this feature has a forward reference to F007 for the close-cascade, implemented as an event/port to avoid a hard dependency in the other direction.)
