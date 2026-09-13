# F002 — Action Plan

**Depends on**: F001.

## Backend
- [ ] Add `domain/category/Category.java` and `CategoryType` enum.
- [ ] Add `domain/paymentmethod/PaymentMethod.java`.
- [ ] Add JPA entities (`CategoryJpaEntity`, `PaymentMethodJpaEntity`) extending `AuditableEntity`, Spring Data repositories, and adapters implementing domain repository ports.
- [ ] Flyway migration: create `categories` and `payment_methods` tables, seed starter data.
- [ ] Application services: create/rename/delete for both, with delete blocked (`409`) when referenced by transactions (query needed once F004 exists — until then, delete is unconditionally allowed; add the guard when F004 lands, or stub the check now against an empty transactions table).
- [ ] REST controllers + DTOs for both resources.
- [ ] Tests: category type immutability after creation, delete-blocked-when-referenced behavior (once F004's transaction table exists to reference).

## Frontend
- [ ] `src/api/categories.ts`, `src/api/paymentMethods.ts` — typed client functions.
- [ ] `src/features/categories` — list/add/rename/delete UI.
- [ ] `src/features/paymentMethods` — list/add/rename/delete UI.
- [ ] Surface `409` delete-conflict responses with an actionable message.

## Verification
- [ ] Create, rename, delete a category and a payment method end to end through the UI.
- [ ] Confirm attempting to change a category's type after creation is rejected.
