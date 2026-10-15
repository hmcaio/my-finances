# F034 — Action Plan

**Depends on**: F002, F008, F017, F026 (all built). See [ADR 0028](../../adr/0028-fixed-taxonomy-values-and-onboarding-seeded-categories.md).

## Backend
- [x] Write tests for `InstitutionService`'s new `requireNotProtected` guard: rename on the built-in row rejected (409, `BuiltInInstitutionException`), rename on any other row still allowed, delete behavior unchanged, then implement.
- [x] Delete `create`/`rename` from `PaymentMethod`/`InvestmentCategory`/`InvestmentSubcategory`/`InvestmentSegment` domain classes; update/delete their now-dead unit tests.
- [x] Delete `create`/`rename`/`delete` (and their dedicated exceptions) from `PaymentMethodService`/`InvestmentCategoryService`/`InvestmentSubcategoryService`/`InvestmentSegmentService`; update/delete their now-dead tests, keep `findAll` tests. (`InvestmentSubcategoryService` had no standalone `findAll` - sub-categories are read only nested under their category - so it and its controller are deleted outright rather than kept empty; see spec/CLAUDE.md corrections.)
- [x] Delete the `POST`/`PATCH`/`DELETE` handlers from the four controllers; update/delete their now-dead controller tests, keep `GET` tests.
- [x] Flyway migration (next free `V` number): seed `investment_segments` with Lajes Corporativas, Shoppings, Logística, Papel (Recebíveis), Híbrido, Fiagro, Hospitalar, Residencial, Outros. Migration test confirming the fresh table ends up with exactly these nine rows and nothing else changes. (`V24` - `V23` was already taken by F032's audit log migration by the time this landed.)
- [ ] Regenerate `frontend/src/api/generated/schema.ts`.
- [x] Update root `CLAUDE.md`/backend `CLAUDE.md` if either documents the removed CRUD endpoints anywhere. (Neither documented the removed endpoints directly; backend `CLAUDE.md`'s seed-row/UNIQUE-constraint testing note is extended to cover `V24`'s new `investment_segments` seed.)

## Frontend
- [ ] `InstitutionsPage.tsx`: hide Rename alongside Delete for the built-in row (rendered-hidden, row-height parity).
- [ ] Strip add/rename/delete UI from `PaymentMethodsPage.tsx`, `InvestmentCategoriesPage.tsx`, `InvestmentSegmentsPage.tsx`; remove their now-dead write API client functions and MSW mutation handlers.
- [ ] Audit every picker for these four entities for an inline quick-create affordance; remove any found.
- [ ] `Layout.tsx`: new collapsible "System values" nav section (collapsed by default) holding Payment Methods / Investment Categories / Investment Segments; Categories/Institutions/Vehicles stay flat.
- [ ] New `en`/`pt-BR` translation keys for the group label and any removed-button copy cleanup.
- [ ] Update/delete tests for the three pages' now-removed mutation UI; add a test for the new collapsible nav group's default-collapsed state and toggle.

## Verification
- [ ] Attempt to rename or delete Institution's "No institution" row: both rejected (409); renaming/deleting any other institution still works.
- [ ] Confirm `PaymentMethod`/`InvestmentCategory`/`InvestmentSubcategory`/`InvestmentSegment` have no create/rename/delete affordance anywhere in the UI, and their REST endpoints reject `POST`/`PATCH`/`DELETE` (404/405, whichever Spring returns for a missing mapping).
- [ ] Fresh database: `investment_segments` has exactly the nine seeded rows.
- [ ] Settings nav: "System values" section renders collapsed on load, expands/collapses on click, contains exactly Payment Methods/Investment Categories/Investment Segments; Categories/Institutions/Vehicles remain flat top-level items.
- [ ] Existing transactions/products referencing a payment method, investment category/sub-category or segment still display correctly (read path unaffected by the write-path removal).
