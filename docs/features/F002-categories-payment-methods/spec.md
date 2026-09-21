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
- Both are simple entities with no internal invariants beyond a non-blank name capped at 100 characters (`domain/shared/TextFieldConstraints.MAX_NAME_LENGTH`) — no rich behavior needed. Uniqueness of `name` is an application-layer concern (`CategoryService`/`PaymentMethodService` check `existsByName`/`existsByNameAndIdNot` on create/rename, throwing `CategoryNameAlreadyExistsException`/`PaymentMethodNameAlreadyExistsException`, 409, exact match/case-sensitive) rather than a domain-constructor check — added in the post-F007 schema audit, see the header of `V10__db_constraint_hardening.sql`.

### Persistence
- `infrastructure/persistence/category/CategoryJpaEntity.java` extends `AuditableEntity` (F001); table `categories` (`id uuid primary key`, `name varchar(100) not null`, `type text not null`, plus audit columns).
- `infrastructure/persistence/paymentmethod/PaymentMethodJpaEntity.java` extends `AuditableEntity`; table `payment_methods` (`id uuid primary key`, `name varchar(100) not null`, plus audit columns).
- Flyway migration `V2__categories_and_payment_methods.sql` creates both tables (originally with unbounded `text` name columns) and seeds the starter data:
  - Categories (expense): Groceries, Rent, Utilities, Subscriptions, Transport, Dining, Health, Other (renamed "Other Expense" and made built-in by `V14`, see below).
  - Categories (income): Salary, Other Income (built-in from `V14`).
  - Payment methods: Debit Card, Credit Card, PIX, Cash.
  - (Exact starter lists are the PRD's own open item, §8 — the above is a reasonable default, adjust freely at implementation time.)
- Flyway migration `V3__bound_name_column_lengths.sql` (security-audit follow-up) narrows both `name` columns from `text` to `varchar(100)`, matching the domain-layer length invariant and the DTOs' `@Size(max = 100)`.
- Flyway migration `V10__db_constraint_hardening.sql` (post-F007 schema audit) adds `UNIQUE (name)` to both tables, backing the application-layer duplicate-name guard above as defense in depth.
- Flyway migration `V14__builtin_categories.sql` (issue #29) adds `categories.built_in boolean NOT NULL DEFAULT false` and `UNIQUE (type) WHERE built_in`, so there is exactly one built-in row per type: "Other Expense" (`EXPENSE`) and "Other Income" (`INCOME`). Same idea as F017's "No institution", but the rows already existed, so the migration adopts them instead of only inserting: a row already named "Other Expense" is flagged, else the seeded `Other` is flagged and renamed "Other Expense" (kept as `Other` if an `INCOME` row holds that name); a missing or user-renamed seed row gets a fresh built-in row (suffixed " (built-in)" only if the name is taken by the other type). "Other Income" is adopted or inserted the same way.

### Built-in categories
- `Category.isBuiltIn()` (read-only): `create` always yields `false`, only `reconstitute` carries the flag in. A built-in category **can be renamed but never deleted**; `type` stays immutable. `CategoryService.delete` checks, in order: unknown id (404), built-in (409, `BuiltInCategoryException`), referenced by a transaction/budget/recurring template (409, `CategoryInUseException`). Nothing else treats it specially (no picker default, no reassign-on-delete).

### API
- `GET /api/categories` (each item `{ id, name, type, builtIn }`), `POST /api/categories`, `PATCH /api/categories/{id}` (name only, type immutable), `DELETE /api/categories/{id}`.
- `GET /api/payment-methods`, `POST /api/payment-methods`, `PATCH /api/payment-methods/{id}`, `DELETE /api/payment-methods/{id}`.
- Delete behavior (resolving PRD §6.3's flagged open edge case): deleting a `Category` or `PaymentMethod` that is referenced by any `Transaction` (or, for categories, any `Budget`/`RecurringTemplate`) is rejected with `409 Conflict` rather than blocked silently or cascaded — the API returns which entity is blocking deletion so the frontend can offer reassignment. Reassignment itself (bulk-move transactions to another category before deleting) is a frontend-orchestrated sequence of existing PATCH-transaction calls (F004), not a new bulk endpoint. `CategoryService.delete()`'s guard landed transaction-only with F004 (that table existing first) and was only widened to also check `Budget`/`RecurringTemplate` in the post-F007 schema audit, once F006/F007 existed to check against — until then, a category with a budget or recurring template but no transactions yet was incorrectly still deletable.

## Frontend
- Settings-style list views for Categories and Payment Methods: table with name (+ type, for categories), inline rename, add-new form, delete button. The categories list shows expenses before income, each type's built-in row first, and no delete button on built-in rows.
- Delete button on an in-use category/payment method surfaces the 409 response as "N transactions use this — reassign them first" rather than a generic error.
- These lists are consumed as dropdown options by F004 (Transactions), F006 (Budgets), F007 (Recurring Templates) — expose a simple typed API client function (`getCategories()`, `getPaymentMethods()`) other features' frontend code reuses rather than duplicating fetch calls.

## Dependencies
F001 (scaffolding conventions: IdGenerator, AuditableEntity).
