import { describe, expect, it } from 'vitest'
import i18n from '../../i18n/i18n'
import { ACCOUNT_TYPES, accountTypeLabel } from './accountTypes'

describe('accountTypeLabel', () => {
  it('labels every account type in English', () => {
    const t = i18n.getFixedT('en')

    expect(ACCOUNT_TYPES.map((type) => accountTypeLabel(type, t))).toEqual([
      'Checking',
      'Savings',
      'Cash Wallet',
      'Credit Card',
      'Investment',
    ])
  })

  it('labels every account type in Portuguese, with a translated string for each', () => {
    const labels = ACCOUNT_TYPES.map((type) => accountTypeLabel(type, i18n.getFixedT('pt-BR')))

    expect(labels.every((label) => label !== '' && !label.includes(':'))).toBe(true)
    expect(new Set(labels).size).toBe(ACCOUNT_TYPES.length)
  })
})
