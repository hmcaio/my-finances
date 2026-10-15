# F035 — Action Plan

**Depends on**: F011, F002 (both built). See [ADR 0028](../../adr/0028-fixed-taxonomy-values-and-onboarding-seeded-categories.md).

## Backend
- [x] Edit `V2__categories_and_payment_methods.sql`: remove the Groceries/Rent/Utilities/Subscriptions/Transport/Dining/Health/Salary inserts, keep `Other`/`Other Income`.
- [x] Migration test: fresh `categories` after `V2` has exactly `Other`/`Other Income`; after migrating through `V14`/`V18`/`V20`, the protected set is still exactly Other Expense/Other Income/Fuel/Dividends. (`V2CategorySeedMigrationTest`)
- [x] Confirm no other backend test fixture relied on the eight removed rows existing by default (grep test setup for their literal names); fix any that do. (`CategoryRepositoryAdapterTest.theTwoBuiltInFallbackCategoriesFromTheMigrationAreSeeded`, `BuiltInCategoriesMigrationTest.theSchemaRefusesASecondBuiltInCategoryOfTheSameType`, `CategoryControllerTest.listExposesTheBuiltInFlagAndCreatedCategoriesAreNotBuiltIn` all updated - see spec/report for detail.)

## Frontend
- [ ] `suggestedCategories.ts`: the 11-entry `{translationKey, type}` list.
- [ ] `OnboardingPage.tsx`: `Stepper` with "Account"/"Categories" steps, `activeStep` state, existing `AccountCreateForm` as step 1 unchanged.
- [ ] `CategorySuggestionsStep.tsx`: pre-checked suggestion checkboxes grouped by type, add-your-own repeatable row, always-enabled Finish, `POST /api/categories` per selection, banner+retry on failure, calls `onCompleted` on success.
- [ ] `seedNames.ts`: English + Portuguese literal entries for all 11 suggestions (3 new translation keys for Entertainment/Education/Insurance, 8 existing keys gain a Portuguese-literal lookup entry).
- [ ] New `en`/`pt-BR` strings: step 2 heading/intro, "edit anytime later" note, add-your-own control labels.
- [ ] `OnboardingPage.test.tsx`: two-step flow, Finish with mixed selections, Finish with zero selections.
- [ ] `CategorySuggestionsStep.test.tsx`: new.

## Verification
- [ ] Fresh database + fresh frontend: onboarding shows step 1 (account creation) then step 2 (11 pre-checked suggestions).
- [ ] Uncheck some suggestions, add one custom category, Finish: only the checked + custom categories exist afterward, in the language active at the time.
- [ ] Finish step 2 with every suggestion unchecked and nothing custom added: allowed, zero non-protected categories exist, app proceeds normally.
- [ ] Switch UI language mid-session after onboarding: an accepted-but-unrenamed suggestion's name still translates (exact-string match); a renamed one does not.
- [ ] Reload after onboarding completes: step 2 never reappears (at least one account now exists).
- [ ] Settings → Categories: onboarding-inserted categories are fully editable/deletable, indistinguishable from a category added later.
