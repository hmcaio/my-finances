import { apiClient } from '../core/client'
import { unwrap } from '../core/apiError'

/** A sub-category as nested under its category in the categories response. */
export interface InvestmentSubcategoryEntry {
  id: string
  name: string
}

/**
 * An investment category (PRD S5.8, F008) with its sub-categories nested: one small call feeds
 * every product picker.
 *
 * F034/ADR 0028: read-only — every row (both levels) is fixed, migration-seeded content (F013's
 * seeded set). create/rename/delete were removed from the backend and this client alike.
 */
export interface InvestmentCategory {
  id: string
  name: string
  subcategories: InvestmentSubcategoryEntry[]
}

/** Matches the backend's `TextFieldConstraints.MAX_NAME_LENGTH` - still used by
 * `InvestmentProductForm`'s ticker field, a flat-taxonomy-style name of its own. */
export const INVESTMENT_NAME_MAX_LENGTH = 100

/** Fetches every category with its sub-categories, both levels sorted by name. */
export async function getInvestmentCategories(): Promise<InvestmentCategory[]> {
  return unwrap(apiClient.get<InvestmentCategory[]>('/investment-categories'))
}
