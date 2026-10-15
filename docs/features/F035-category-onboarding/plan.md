# F035 — Action Plan

**Depends on**: F011, F002 (both built). See [ADR 0028](../../adr/0028-fixed-taxonomy-values-and-onboarding-seeded-categories.md).

## Backend
- [x] Edit `V2__categories_and_payment_methods.sql`: remove the Groceries/Rent/Utilities/Subscriptions/Transport/Dining/Health/Salary inserts, keep `Other`/`Other Income`.
- [x] Migration test: fresh `categories` after `V2` has exactly `Other`/`Other Income`; after migrating through `V14`/`V18`/`V20`, the protected set is still exactly Other Expense/Other Income/Fuel/Dividends. (`V2CategorySeedMigrationTest`)
- [x] Confirm no other backend test fixture relied on the eight removed rows existing by default (grep test setup for their literal names); fix any that do. (`CategoryRepositoryAdapterTest.theTwoBuiltInFallbackCategoriesFromTheMigrationAreSeeded`, `BuiltInCategoriesMigrationTest.theSchemaRefusesASecondBuiltInCategoryOfTheSameType`, `CategoryControllerTest.listExposesTheBuiltInFlagAndCreatedCategoriesAreNotBuiltIn` all updated - see spec/report for detail.)

## Frontend
- [x] `suggestedCategories.ts`: the 11-entry `{translationKey, type}` list.
- [x] `App.tsx`: fix the onboarding gate so it doesn't swap to the dashboard mid-flow. **Found after initial implementation, via a user bug report**: creating the account in step 1 already flips `hasAccounts` to `true` (every mutation invalidates every query), so the original gate (`hasAccounts ? shell : <OnboardingPage />`) left the dashboard the instant step 1 succeeded, before step 2 ever rendered — the category-suggestions step was being silently skipped on every run. Added an `inOnboarding` latch (set during render, not a `useEffect`, so it applies on the same render that observes `hasAccounts` turn `false`) that only releases via `OnboardingPage`'s own `onCompleted`. New `App.test.tsx` case ("stays on the category-suggestions step after account creation, until Finish is clicked") reproduces the bug against the pre-fix code (confirmed failing without the fix, passing with it) and guards the regression; the pre-existing "moves into the normal app after the first account is created" test was itself asserting the buggy behavior (jumping straight to the dashboard) and is corrected to stop at step 2 instead.
- [x] `OnboardingPage.tsx`: `Stepper` with "Account"/"Categories" steps, `activeStep` state, existing `AccountCreateForm` as step 1 unchanged.
- [x] `CategorySuggestionsStep.tsx`: pre-checked suggestion checkboxes grouped by type, add-your-own repeatable row, always-enabled Finish, `POST /api/categories` per selection, banner+retry on failure, calls `onCompleted` on success.
- [x] `seedNames.ts`: English + Portuguese literal entries for all 11 suggestions (3 new translation keys for Entertainment/Education/Insurance, 8 existing keys gain a Portuguese-literal lookup entry).
- [x] New `en`/`pt-BR` strings: step 2 heading/intro, "edit anytime later" note, add-your-own control labels.
- [x] `OnboardingPage.test.tsx`: two-step flow, Finish with mixed selections, Finish with zero selections.
- [x] `CategorySuggestionsStep.test.tsx`: new.

## Verification
- [x] Fresh database + fresh frontend: onboarding shows step 1 (account creation) then step 2 (11 pre-checked suggestions), **and actually stays on step 2 after step 1's account is created** (the real integration path, not just `OnboardingPage` rendered standalone) — covered end to end by `App.test.tsx`'s new case; `OnboardingPage.test.tsx`/`CategorySuggestionsStep.test.tsx` cover the step's own behavior in isolation; `scripts/verify.sh` green end to end.
- [x] Uncheck some suggestions, add one custom category, Finish: only the checked + custom categories exist afterward, in the language active at the time. (`OnboardingPage.test.tsx`: "completes onboarding on Finish with a mix of checked, unchecked and custom categories")
- [x] Finish step 2 with every suggestion unchecked and nothing custom added: allowed, zero non-protected categories exist, app proceeds normally. (`OnboardingPage.test.tsx`/`CategorySuggestionsStep.test.tsx`: "...with every suggestion unchecked...")
- [x] Switch UI language mid-session after onboarding: an accepted-but-unrenamed suggestion's name still translates (exact-string match); a renamed one does not. (existing `translateSeedName`/`CategoriesPage` mechanism, extended with both literals per suggestion - unit-covered by `seedNames.ts`'s lookup table and `localeParity.test.ts`; not re-verified end-to-end here since the mechanism itself is unchanged, only its data.)
- [x] Reload after onboarding completes: step 2 never reappears (at least one account now exists). (entry trigger unchanged, `useHasAccounts` - covered by the existing F011 `App.test.tsx` cases, which still pass; the exit latch added above doesn't affect a fresh mount, since `inOnboarding` initializes from `hasAccounts` itself.)
- [x] Settings → Categories: onboarding-inserted categories are fully editable/deletable, indistinguishable from a category added later. (no `CategoryService`/`CategoriesPage` change was needed - the suggestions step calls the same `POST /api/categories` as any other caller.)
