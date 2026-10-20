# F002 — Action Plan

**Depends on**: F001.

## Backend
- [x] Write tests first for the domain rules: `Category` type is immutable after creation. Delete-blocked-when-referenced-by-a-transaction was deferred here — F004's transaction table didn't exist yet — and lands as part of F004 (see that feature's `CategoryServiceTest`/`PaymentMethodServiceTest`).
- [x] Add `domain/category/Category.java` and `CategoryType` enum, and `domain/paymentmethod/PaymentMethod.java`, implementing the above to make those tests pass.
- [x] Add JPA entities (`CategoryJpaEntity`, `PaymentMethodJpaEntity`) extending `AuditableEntity`, Spring Data repositories, and adapters implementing domain repository ports.
- [x] Flyway migration: create `categories` and `payment_methods` tables, seed starter data.
- [x] Write tests for the application-service create/rename/delete for both. The `409`-when-referenced delete guard itself was deferred, same reason as above — now added in F004 (`CategoryInUseException`/`PaymentMethodInUseException`).
- [x] REST controllers + DTOs for both resources.

## Frontend
- [x] `src/api/categories.ts`, `src/api/paymentMethods.ts` — typed client functions.
- [x] `src/features/categories` — list/add/rename/delete UI.
- [x] `src/features/paymentMethods` — list/add/rename/delete UI.
- [x] Surface `409` delete-conflict responses with an actionable message. (`unwrap()` in `src/api/apiError.ts` special-cases a 409 into a friendly "reassign them first" message via each client function's `conflictMessage` argument — see `src/api/categories.ts`/`paymentMethods.ts`. The guard itself started backed only by this frontend plumbing with no real 409 to trigger it; F004 added the real backend guard that now actually returns one.)

## Verification
- [x] Create, rename, delete a category and a payment method end to end through the UI. Done via a Playwright-driven browser session against the real dev server + backend + Postgres: added "UI Smoke Test", renamed it to "UI Smoke Test Renamed", deleted it, confirmed 0 remaining and zero browser console errors. Also exercised directly via curl (create/rename/delete, 201/200/204, 400 on blank name, 404 on unknown id).
- [x] Confirm attempting to change a category's type after creation is rejected. Confirmed via curl: `PATCH /api/categories/{id}` with body `{"name":"...","type":"INCOME"}` on an EXPENSE category returns 200 with `type` still `EXPENSE` — `UpdateCategoryRequest` has no `type` field, so Jackson silently drops the extra property and the type never changes. Also covered by `CategoryControllerTest.patchWithATypeFieldDoesNotChangeType()`.
