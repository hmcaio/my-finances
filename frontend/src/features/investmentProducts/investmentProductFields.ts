import type { InvestmentCategory } from '../../api/investments/investmentCategories'

/**
 * Which optional fields of the product form are relevant to a classification (issue #230). The
 * category and sub-category lists are fixed, migration-seeded content (ADR 0028), so the rule
 * matches their literal names - the same way `FiiPage` finds the FII sub-category. Both names are
 * matched as a pair because "Stocks" and "ETFs" exist under Variable Income and International.
 */
const VARIABLE_INCOME = 'Variable Income'
const INTERNATIONAL = 'International'

/** Sub-categories traded on an exchange under a ticker, keyed by their category's name. */
const TICKER_SUBCATEGORIES: Record<string, readonly string[]> = {
  [VARIABLE_INCOME]: ['Stocks (Ações)', 'REITs (FIIs)', 'ETFs', 'BDRs'],
  [INTERNATIONAL]: ['Stocks', 'ETFs', 'REITs'],
}

/** Segments are FII sectors (ADR 0023): only this sub-category has them. */
const SEGMENT_SUBCATEGORIES: Record<string, readonly string[]> = {
  [VARIABLE_INCOME]: ['REITs (FIIs)'],
}

export interface InvestmentFieldRelevance {
  ticker: boolean
  segment: boolean
}

/** No sub-category selected (it is optional) means neither field is relevant. */
export function relevantInvestmentFields(
  categories: InvestmentCategory[],
  categoryId: string,
  subcategoryId: string,
): InvestmentFieldRelevance {
  const category = categories.find((c) => c.id === categoryId)
  const subcategory = category?.subcategories.find((s) => s.id === subcategoryId)
  if (!category || !subcategory) return { ticker: false, segment: false }
  return {
    ticker: TICKER_SUBCATEGORIES[category.name]?.includes(subcategory.name) ?? false,
    segment: SEGMENT_SUBCATEGORIES[category.name]?.includes(subcategory.name) ?? false,
  }
}
