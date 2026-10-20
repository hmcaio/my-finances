import { describe, expect, it } from 'vitest'
import { seedInvestmentCategories } from '../../mocks/handlers/investmentCategories'
import { getInvestmentCategories } from './investmentCategories'

/**
 * F034/ADR 0028: InvestmentCategory (and InvestmentSubcategory, nested under it) lost
 * create/rename/delete - every row at both levels is fixed, migration-seeded content. Only the
 * nested read is left to test.
 */
describe('investment categories API client', () => {
  it('getInvestmentCategories returns the categories with their sub-categories nested', async () => {
    const categories = await getInvestmentCategories()

    // The seed array is already in the backend's sorted order (both levels by name).
    expect(categories).toEqual(seedInvestmentCategories)
    expect(categories.find((c) => c.name === 'Fixed Income')?.subcategories.length).toBeGreaterThan(
      0,
    )
  })
})
