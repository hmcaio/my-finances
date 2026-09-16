# F002 — Categories & Payment Methods

## Summary
CRUD for `Category` and `PaymentMethod`, the two simplest reference entities in the system (PRD §5.1, §5.2, §6.3). Establishes the pattern later "flat, user-editable taxonomy" entities (e.g. `InvestmentCategory`, F008) reuse.

## Scope
- `Category`: id, name, type (`INCOME`/`EXPENSE`).
- `PaymentMethod`: id, name.
- Predefined starter data for both, seeded via Flyway (not created through the API at first run).
- Delete/reassignment behavior when a category or payment method is referenced by existing transactions.
- Out of scope: any transaction logic itself (F004).

## Backend

### Domain
- `domain/category/Category.java`: id (`UUID`), `name` (non-blank), `type` (`CategoryType` enum: `INCOME`, `EXPENSE`). Rename is allowed at any time; type is immutable after creation (changing income/expense type on a category with existing transactions would silently corrupt budget/net-worth math — reject the change instead of allowing it).
- `domain/paymentmethod/PaymentMethod.java`: id (`UUID`), `name` (non-blank).
- Both are simple entities with no internal invariants beyond a non-blank name capped at 100 characters (`domain/shared/TextFieldConstraints.MAX_NAME_LENGTH`) — no rich behavior needed.

### Persistence
- `infrastructure/persistence/category/CategoryJpaEntity.java` extends `AuditableEntity` (F001); table `categories` (`id uuid primary key`, `name varchar(100) not null`, `type text not null`, plus audit columns).
- `infrastructure/persistence/paymentmethod/PaymentMethodJpaEntity.java` extends `AuditableEntity`; table `payment_methods` (`id uuid primary key`, `name varchar(100) not null`, plus audit columns).
- Flyway migration `V2__categories_and_payment_methods.sql` creates both tables (originally with unbounded `text` name columns) and seeds the starter data:
  - Categories (expense): Groceries, Rent, Utilities, Subscriptions, Transport, Dining, Health, Other.
  - Categories (income): Salary, Other Income.
  - Payment methods: Debit Card, Credit Card, PIX, Cash.
  - (Exact starter lists are the PRD's own open item, §8 — the above is a reasonable default, adjust freely at implementation time.)
- Flyway migration `V3__bound_name_column_lengths.sql` (security-audit follow-up) narrows both `name` columns from `text` to `varchar(100)`, matching the domain-layer length invariant and the DTOs' `@Size(max = 100)`.

### API
- `GET /api/categories`, `POST /api/categories`, `PATCH /api/categories/{id}` (name only, type immutable), `DELETE /api/categories/{id}`.
- `GET /api/payment-methods`, `POST /api/payment-methods`, `PATCH /api/payment-methods/{id}`, `DELETE /api/payment-methods/{id}`.
- Delete behavior (resolving PRD §6.3's flagged open edge case): deleting a `Category` or `PaymentMethod` that is referenced by any `Transaction` (or, for categories, any `Budget`/`RecurringTemplate`) is rejected with `409 Conflict` rather than blocked silently or cascaded — the API returns which entity is blocking deletion so the frontend can offer reassignment. Reassignment itself (bulk-move transactions to another category before deleting) is a frontend-orchestrated sequence of existing PATCH-transaction calls (F004), not a new bulk endpoint.

## Frontend
- Settings-style list views for Categories and Payment Methods: table with name (+ type, for categories), inline rename, add-new form, delete button.
- Delete button on an in-use category/payment method surfaces the 409 response as "N transactions use this — reassign them first" rather than a generic error.
- These lists are consumed as dropdown options by F004 (Transactions), F006 (Budgets), F007 (Recurring Templates) — expose a simple typed API client function (`getCategories()`, `getPaymentMethods()`) other features' frontend code reuses rather than duplicating fetch calls.

## Dependencies
F001 (scaffolding conventions: IdGenerator, AuditableEntity).
