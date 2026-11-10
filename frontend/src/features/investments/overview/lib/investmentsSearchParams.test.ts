import { describe, expect, it } from 'vitest'
import { parseInvestmentsTab, serializeInvestmentsTab } from './investmentsSearchParams'

const parse = (query: string) => parseInvestmentsTab(new URLSearchParams(query))

describe('parseInvestmentsTab', () => {
  it('defaults to accounts when the tab is absent', () => {
    expect(parse('')).toBe('accounts')
  })

  it('reads accounts and products', () => {
    expect(parse('tab=accounts')).toBe('accounts')
    expect(parse('tab=products')).toBe('products')
  })

  it('falls back to accounts for an unknown, empty or differently cased value', () => {
    for (const bad of ['zzz', '', 'Products', 'PRODUCTS', 'products ', 'accounts,products']) {
      expect(parse(`tab=${encodeURIComponent(bad)}`)).toBe('accounts')
    }
  })

  it('ignores unrelated parameters', () => {
    expect(parse('foo=bar&tab=products&baz=1')).toBe('products')
  })
})

describe('serializeInvestmentsTab', () => {
  it('omits the default tab so the plain URL stays clean', () => {
    expect(serializeInvestmentsTab('accounts').toString()).toBe('')
  })

  it('keeps the products tab', () => {
    expect(serializeInvestmentsTab('products').toString()).toBe('tab=products')
  })

  it('round-trips through parse', () => {
    for (const tab of ['accounts', 'products'] as const) {
      expect(parseInvestmentsTab(serializeInvestmentsTab(tab))).toBe(tab)
    }
  })
})
