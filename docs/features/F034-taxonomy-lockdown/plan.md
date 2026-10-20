# F034 — Action Plan

**Depends on**: F002, F008, F017, F026 (all built). See [ADR 0028](../../adr/0028-fixed-taxonomy-values-and-onboarding-seeded-categories.md).

## Backend
- [x] Write tests for `InstitutionService`'s new `requireNotProtected` guard: rename on the built-in row rejected (409, `BuiltInInstitutionException`), rename on any other row still allowed, delete behavior unchanged, then implement.
- [x] Delete `create`/`rename` from `PaymentMethod`/`InvestmentCategory`/`InvestmentSubcategory`/`InvestmentSegment` domain classes; update/delete their now-dead unit tests.
- [x] Delete `create`/`rename`/`delete` (and their dedicated exceptions) from `PaymentMethodService`/`InvestmentCategoryService`/`InvestmentSubcategoryService`/`InvestmentSegmentService`; update/delete their now-dead tests, keep `findAll` tests. (`InvestmentSubcategoryService` had no standalone `findAll` - sub-categories are read only nested under their category - so it and its controller are deleted outright rather than kept empty; see spec/CLAUDE.md corrections.)
- [x] Delete the `POST`/`PATCH`/`DELETE` handlers from the four controllers; update/delete their now-dead controller tests, keep `GET` tests.
- [x] Flyway migration (next free `V` number): seed `investment_segments` with Lajes Corporativas, Shoppings, Logística, Papel (Recebíveis), Híbrido, Fiagro, Hospitalar, Residencial, Outros. Migration test confirming the fresh table ends up with exactly these nine rows and nothing else changes. (`V24` - `V23` was already taken by F032's audit log migration by the time this landed.)
- [x] Regenerate `frontend/src/api/generated/schema.ts`.
- [x] Update root `CLAUDE.md`/backend `CLAUDE.md` if either documents the removed CRUD endpoints anywhere. (Neither documented the removed endpoints directly; backend `CLAUDE.md`'s seed-row/UNIQUE-constraint testing note is extended to cover `V24`'s new `investment_segments` seed.)

## Frontend
- [x] `InstitutionsPage.tsx`: hide Rename alongside Delete for the built-in row (rendered-hidden, row-height parity).
- [x] Strip add/rename/delete UI from `PaymentMethodsPage.tsx`, `InvestmentCategoriesPage.tsx`, `InvestmentSegmentsPage.tsx`; remove their now-dead write API client functions and MSW mutation handlers. (`InvestmentSubcategory`'s standalone API client/queries/mock-handler files are deleted outright too - same reasoning as the backend's controller/service removal: no GET of its own to keep, nested read only.)
- [x] Audit every picker for these four entities for an inline quick-create affordance; remove any found. (None found: `TransactionFormFields`'s payment-method select and `InvestmentProductForm`'s category/sub-category/segment selects are plain MUI `Select`/`MenuItem` with no create path - only `InstitutionSelect` ever had this pattern, and `Institution` is unaffected since it keeps full CRUD.)
- [x] `Layout.tsx`: new collapsible "System values" nav section (collapsed by default) holding Payment Methods / Investment Categories / Investment Segments; Categories/Institutions/Vehicles stay flat.
- [x] New `en`/`pt-BR` translation keys for the group label and any removed-button copy cleanup.
- [x] Update/delete tests for the three pages' now-removed mutation UI; add a test for the new collapsible nav group's default-collapsed state and toggle.
- [x] Add `InvestmentSegment` to `src/i18n/seedNames.ts`'s seed-name translation mechanism (new `investmentSegments` `SeedTable` + `INVESTMENT_SEGMENT_SEED_NAMES`, en/pt-BR keys for the nine `V24` names) and render through `translateSeedName` in `InvestmentSegmentsPage.tsx`. (Gap found after the initial implementation: the table had no seed data when F033 shipped, so it was never added then; now that `V24` seeds real content it follows the same pattern as the other five taxonomies. `InvestmentSegmentsPage.test.tsx` updated to assert translated text, resolved via a fixed `en` `t()` same as `test/errorMessages.ts`.)

## Verification
- [x] Attempt to rename or delete Institution's "No institution" row: both rejected (409); renaming/deleting any other institution still works. (`InstitutionServiceTest`, `InstitutionControllerTest`.)
- [x] Confirm `PaymentMethod`/`InvestmentCategory`/`InvestmentSubcategory`/`InvestmentSegment` have no create/rename/delete affordance anywhere in the UI, and their REST endpoints reject `POST`/`PATCH`/`DELETE` (404/405, whichever Spring returns for a missing mapping). (Per-controller tests assert the exact status; `InvestmentSubcategory`'s endpoints are gone entirely, so any method there is a plain 404.)
- [x] Fresh database: `investment_segments` has exactly the nine seeded rows. (`InvestmentSegmentSeedMigrationTest`.)
- [x] Settings nav: "System values" section renders collapsed on load, expands/collapses on click, contains exactly Payment Methods/Investment Categories/Investment Segments; Categories/Institutions/Vehicles remain flat top-level items. (`Layout.test.tsx`.)
- [x] Existing transactions/products referencing a payment method, investment category/sub-category or segment still display correctly (read path unaffected by the write-path removal). (Unchanged read paths; covered by the existing `InvestmentProductServiceTest`/`TransactionServiceTest` fixtures plus this PR's own read-only page tests.)
