import type { CategoryType } from '../../api/categories/categories'

export interface SuggestedCategory {
  /**
   * The exact key `seedNames.ts`'s `CATEGORY_SEED_NAMES` table uses for this name (reused for the
   * eight names that already existed as a migration seed; three brand-new keys - entertainment,
   * education, insurance - added alongside it by this feature). `t(translationKey)` is both the
   * checkbox label shown here and the literal name sent to `POST /api/categories` on Finish, so
   * whichever language was active at onboarding time is exactly what `translateSeedName` matches
   * on every later render (F035/ADR 0028).
   */
  translationKey: string
  type: CategoryType
}

/**
 * The 11 starter-category suggestions offered by onboarding's second step, replacing the eight
 * regular rows `V2` used to migration-seed (plus Entertainment/Education/Insurance, new). Grouped
 * here in the same expense-then-income order `CategoriesPage`/`sortCategories` already use.
 */
export const SUGGESTED_CATEGORIES: SuggestedCategory[] = [
  { translationKey: 'common:seedNames.categories.groceries', type: 'EXPENSE' },
  { translationKey: 'common:seedNames.categories.rent', type: 'EXPENSE' },
  { translationKey: 'common:seedNames.categories.utilities', type: 'EXPENSE' },
  { translationKey: 'common:seedNames.categories.subscriptions', type: 'EXPENSE' },
  { translationKey: 'common:seedNames.categories.transport', type: 'EXPENSE' },
  { translationKey: 'common:seedNames.categories.dining', type: 'EXPENSE' },
  { translationKey: 'common:seedNames.categories.health', type: 'EXPENSE' },
  { translationKey: 'common:seedNames.categories.entertainment', type: 'EXPENSE' },
  { translationKey: 'common:seedNames.categories.education', type: 'EXPENSE' },
  { translationKey: 'common:seedNames.categories.insurance', type: 'EXPENSE' },
  { translationKey: 'common:seedNames.categories.salary', type: 'INCOME' },
]
