import { describe, expect, it } from 'vitest'
import type { InvestmentCategory } from '../../api/investments/investmentCategories'
import { relevantInvestmentFields } from './investmentProductFields'

const categories: InvestmentCategory[] = [
  {
    id: 'c-var',
    name: 'Variable Income',
    subcategories: [
      { id: 's-stocks', name: 'Stocks (Ações)' },
      { id: 's-fii', name: 'REITs (FIIs)' },
      { id: 's-etfs', name: 'ETFs' },
      { id: 's-bdrs', name: 'BDRs' },
    ],
  },
  {
    id: 'c-intl',
    name: 'International',
    subcategories: [
      { id: 's-istocks', name: 'Stocks' },
      { id: 's-ietfs', name: 'ETFs' },
      { id: 's-ireits', name: 'REITs' },
      { id: 's-ibonds', name: 'Bonds' },
    ],
  },
  { id: 'c-fixed', name: 'Fixed Income', subcategories: [{ id: 's-cdb', name: 'CDB' }] },
  // Same sub-category name as International's, to prove the pair is matched, not the name alone.
  { id: 'c-other', name: 'Other', subcategories: [{ id: 's-other-etfs', name: 'ETFs' }] },
  { id: 'c-crypto', name: 'Crypto', subcategories: [] },
]

describe('relevantInvestmentFields', () => {
  it.each([
    ['c-var', 's-stocks'],
    ['c-var', 's-etfs'],
    ['c-var', 's-bdrs'],
    ['c-intl', 's-istocks'],
    ['c-intl', 's-ietfs'],
    ['c-intl', 's-ireits'],
  ])('shows only the ticker for %s / %s', (category, subcategory) => {
    expect(relevantInvestmentFields(categories, category, subcategory)).toEqual({
      ticker: true,
      segment: false,
    })
  })

  it('shows the ticker and the segment for FIIs', () => {
    expect(relevantInvestmentFields(categories, 'c-var', 's-fii')).toEqual({
      ticker: true,
      segment: true,
    })
  })

  it.each([
    ['c-fixed', 's-cdb'],
    ['c-intl', 's-ibonds'],
    ['c-other', 's-other-etfs'],
  ])('shows neither for %s / %s', (category, subcategory) => {
    expect(relevantInvestmentFields(categories, category, subcategory)).toEqual({
      ticker: false,
      segment: false,
    })
  })

  it('shows neither without a sub-category, or for a category with none', () => {
    const none = { ticker: false, segment: false }
    expect(relevantInvestmentFields(categories, 'c-var', '')).toEqual(none)
    expect(relevantInvestmentFields(categories, 'c-crypto', '')).toEqual(none)
    expect(relevantInvestmentFields(categories, '', '')).toEqual(none)
  })
})
