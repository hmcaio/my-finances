# F002 — Action Plan

**Depends on**: F001.

## Backend
- [ ] Write tests first for the domain rules: `Category` type is immutable after creation; delete is blocked when referenced by a transaction (add this case once F004's transaction table exists).
- [ ] Add `domain/category/Category.java` and `CategoryType` enum, and `domain/paymentmethod/PaymentMethod.java`, implementing the above to make those tests pass.
- [ ] Add JPA entities (`CategoryJpaEntity`, `PaymentMethodJpaEntity`) extending `AuditableEntity`, Spring Data repositories, and adapters implementing domain repository ports.
- [ ] Flyway migration: create `categories` and `payment_methods` tables, seed starter data.
- [ ] Write tests for the application-service delete guard (`409` when referenced), then implement: create/rename/delete for both (until F004 exists, delete is unconditionally allowed — add the guard, and its test, when F004 lands).
- [ ] REST controllers + DTOs for both resources.

## Frontend
- [ ] `src/api/categories.ts`, `src/api/paymentMethods.ts` — typed client functions.
- [ ] `src/features/categories` — list/add/rename/delete UI.
- [ ] `src/features/paymentMethods` — list/add/rename/delete UI.
- [ ] Surface `409` delete-conflict responses with an actionable message.

## Verification
- [ ] Create, rename, delete a category and a payment method end to end through the UI.
- [ ] Confirm attempting to change a category's type after creation is rejected.
