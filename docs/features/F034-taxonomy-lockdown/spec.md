# F034 — Taxonomy Lockdown

## Summary
Closes the Institution rename gap the same way F002 already closed it for Category (commit `986714f`), and removes user customization entirely from `PaymentMethod`, `InvestmentCategory`, `InvestmentSubcategory` and `InvestmentSegment` — all four become fixed, migration-seeded reference lists with no create/rename/delete of any kind. `InvestmentSegment` gets real seed data for the first time (today's `V20` creates the table with no rows). Decision record: [ADR 0028](../../adr/0028-fixed-taxonomy-values-and-onboarding-seeded-categories.md).

## Scope
- `Institution`: block rename and delete on the `built_in` row ("No institution"), mirroring `CategoryService`'s guard. No schema change.
- `PaymentMethod`: remove `POST`/`PATCH`/`DELETE` endpoints and the `create()`/`rename()` domain mutators. Content stays exactly today's four rows (Debit Card, Credit Card, PIX, Cash) — unchanged, just locked.
- `InvestmentCategory` / `InvestmentSubcategory`: same removal. Content stays exactly today's seeded set — unchanged, just locked.
- `InvestmentSegment`: same removal, plus a new seed migration (today's table is empty): Lajes Corporativas, Shoppings, Logística, Papel (Recebíveis), Híbrido, Fiagro, Hospitalar, Residencial, Outros.
- Frontend: the three now-read-only Settings pages (Payment Methods, Investment Categories, Investment Segments) move into a new collapsible "System values" nav section (collapsed by default, not persisted across loads), while `Institution` and `Category` stay flat, fully-editable nav items — only specific rows on those two are protected, the rest of each list is still real user data.
- Any existing custom row a local dev database already has in one of the four locked tables is left untouched (no delete) — just no longer editable or creatable going forward.
- Out of scope: Category's reseed/onboarding change (F035 — a materially different kind of change, its own spec); any content change to PaymentMethod/InvestmentCategory/InvestmentSubcategory (kept identical to today); `Vehicle` (already a protected-on-delete-only taxonomy, untouched by this ADR).

## Decisions
See [ADR 0028](../../adr/0028-fixed-taxonomy-values-and-onboarding-seeded-categories.md) for the full rationale. Summary:
- **Institution gets a guard, not a rewrite** — same shape as `CategoryService.requireNotProtected`, applied to the one entity that still had the pre-986714f bug (rename allowed on a built-in row).
- **Full removal, not a flag, for the four locked entities** — every row becomes protected, so a flag distinguishing protected-from-not carries no information. Deleting the mutation surface entirely is less code than guarding it.
- **No existing-row deletion** — any custom row already in a local dev database (none expected on a fresh install) stays, rather than risking an FK violation against something that references it, for zero real benefit.
- **New collapsible nav group, not full removal of the pages** — the three fixed lists stay visible (useful since the app still picks from them elsewhere), just without mutation controls, grouped together so the flat nav doesn't grow for lists nobody edits anymore.
- **`InvestmentSegment`'s seed list is a product content choice**, not implied by any existing data (the table has none today) — a common B3 FII sector list, confirmed with the user rather than invented silently.

## Backend

### Domain
- `domain/institution/Institution.java`: no field change. `InstitutionService` gains a private `requireNotProtected(Institution)` guard (mirrors `CategoryService`'s), checked at the top of both `rename()` and `delete()`, throwing the existing `BuiltInInstitutionException` (409) for `institution.isBuiltIn()` — today it's thrown only from `delete()`; `rename()` currently has no check at all.
- `domain/paymentmethod/PaymentMethod.java`, `domain/investmentcategory/InvestmentCategory.java`, `domain/investmentsubcategory/InvestmentSubcategory.java`, `domain/investmentsegment/InvestmentSegment.java`: delete the `create(...)` and `rename(...)` mutator methods from each. Each class keeps only its fields, a `reconstitute(...)` factory (still needed to rebuild the domain object from a persisted row for `findAll()`), and whatever read accessors it already has. No new flag field on any of them.

### Application
- `PaymentMethodService`, `InvestmentCategoryService`, `InvestmentSegmentService`: delete `create`, `rename`, `delete` and every exception that existed only to back them (`PaymentMethodNameAlreadyExistsException`, `PaymentMethodInUseException`, and the equivalent pairs for the other two) — keep only `findAll()` (and, for `InvestmentCategoryService`, whatever read method already composes categories with their nested sub-categories). `InstitutionService`: add `requireNotProtected`, called from both `rename` and `delete` before their existing checks.
- `InvestmentSubcategoryService`: deleted outright, not reduced to a `findAll()` — it never had a standalone read method (sub-categories are read only nested under `InvestmentCategoryService`'s list), so once `create`/`rename`/`delete` are gone the class has nothing left to serve. Its dedicated exceptions go with it; `InvestmentSubcategoryNotFoundException` stays (`InvestmentProductService` still throws it).

### Persistence
- No migration for `PaymentMethod` / `InvestmentCategory` / `InvestmentSubcategory` — their content and schema are unchanged; only the application layer above them loses write paths.
- New migration (check the highest existing `V` number at implementation time — `V23` as of this writing, taken by F032's audit log migration by the time this landed, so the actual migration is `V24`): seed `investment_segments` with the nine names listed in Scope. Plain `INSERT`s, no new column — the table (`V20`) already has `id`, `name` (`UNIQUE`), audit columns, and needs nothing structural added since nothing will ever write to it through the app again.

### API
- `InstitutionController`: no route change — `PATCH`/`DELETE` now return `409` for the built-in row where `PATCH` didn't before.
- `PaymentMethodController`, `InvestmentCategoryController`, `InvestmentSegmentController`: delete the `POST`/`PATCH`/`DELETE` handler methods, keep only `GET`.
- `InvestmentSubcategoryController`: deleted outright, not reduced to `GET` — it never had a `GET` of its own (sub-categories are read nested under `GET /api/investment-categories`), so removing `POST`/`PATCH`/`DELETE` leaves nothing to serve. A request to its old paths now gets Spring's default 404.
- Regenerate `frontend/src/api/generated/schema.ts` (`npm run generate-api-types`).

## Frontend
- `src/features/institutions/InstitutionsPage.tsx`: extend the existing row-rendering so **both** Rename and Delete are hidden (rendered-but-`visibility: hidden`, matching `isProtectedCategory`'s row-height-parity pattern) for `institution.builtIn`, not just Delete as today.
- `src/features/paymentMethods/PaymentMethodsPage.tsx`, `src/features/investmentCategories/InvestmentCategoriesPage.tsx`, `src/features/investmentSegments/InvestmentSegmentsPage.tsx`: strip all add/rename/delete controls, leaving a plain read-only list (name, + nested sub-categories for `InvestmentCategoriesPage`, expand/collapse kept). Remove the corresponding API client write functions (`src/api/paymentMethods/paymentMethods.ts`, `src/api/investments/investmentCategories.ts`, `src/api/investments/investmentSegments.ts`) and their MSW handlers' mutation routes; `src/api/investments/investmentSubcategories.ts`/`investmentSubcategoriesQueries.ts` and their mock handler file are deleted outright (no standalone read of their own).
- Check every picker component that renders one of these four taxonomies (e.g. wherever a payment method, investment category/sub-category, or segment is selected on a transaction/product form) for an inline "+ Add" quick-create affordance analogous to `InstitutionSelect`'s; remove it if present — nothing should be creatable from any surface, not just the Settings pages.
- `src/components/layout/Layout.tsx`: replace the current flat `SETTINGS_NAV_ITEMS` entries for Payment Methods, Investment Categories and Investment Segments with a single collapsible group (MUI `Collapse`, following the existing per-item-open-state pattern in `InvestmentCategoriesPage.tsx`/`ActivityPage.tsx` — no `Accordion`, no precedent for it in this codebase), labeled "System values" (new i18n key, e.g. `nav.systemValues`), collapsed by default, state held in local component state (not persisted to `localStorage` — resets on reload, consistent with no existing nav-state persistence). Categories, Institutions and Vehicles stay flat, unaffected.
- New `en`/`pt-BR` translation keys for the group label and any copy change on the three affected pages (e.g. removing "Add" button labels that no longer render).
- **`InvestmentSegment` joins F033's seed-name translation mechanism** (`src/i18n/seedNames.ts`): a new `investmentSegments` `SeedTable` entry and `INVESTMENT_SEGMENT_SEED_NAMES` lookup (the nine `V24` literals → new `common:seedNames.investmentSegments.*` keys, en/pt-BR), and `InvestmentSegmentsPage.tsx` renders each row through `translateSeedName('investmentSegments', segment.name, t)` instead of the raw `segment.name`. This was missed in the original pass — the table had no seed data before this feature, so F033 never had anything to translate here; now that `V24` gives it real content, it follows the same pattern Category/PaymentMethod/Institution/InvestmentCategory/InvestmentSubcategory already use. Every row is permanently protected (no rename possible), so unlike those five tables this lookup never falls through to the verbatim branch.

## Dependencies
F002 (Category/PaymentMethod pattern), F008 (InvestmentCategory/InvestmentSubcategory), F017 (Institution), F026 (InvestmentSegment).
