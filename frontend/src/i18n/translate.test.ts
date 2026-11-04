import { describe, expect, it, vi } from 'vitest'
import i18n from './i18n'
import { translateKey, type QualifiedKey, type Translate } from './translate'

describe('translateKey', () => {
  it('passes the key to t untouched and returns its result', () => {
    const t = vi.fn(() => 'translated')

    expect(translateKey(t, 'accounts:accountTypes.checking')).toBe('translated')
    expect(t).toHaveBeenCalledExactlyOnceWith('accounts:accountTypes.checking')
  })

  it('forwards interpolation options when given, and none when not', () => {
    const t = vi.fn(
      (key: never, options?: Record<string, unknown>) =>
        `${String(key)}|${String(options?.product)}`,
    )

    expect(translateKey(t, 'transfers:tradeLabelBuy', { product: 'BTC' })).toBe(
      'transfers:tradeLabelBuy|BTC',
    )
    expect(t).toHaveBeenLastCalledWith('transfers:tradeLabelBuy', { product: 'BTC' })
    translateKey(t, 'transfers:tradeLabelSell')
    expect(t).toHaveBeenLastCalledWith('transfers:tradeLabelSell')
  })

  it('works with the real t, whatever namespaces it was created for', () => {
    expect(translateKey(i18n.t, 'accounts:accountTypes.checking')).toBe('Checking')
    expect(translateKey(i18n.t, 'transfers:tradeLabelBuy', { product: 'BTC' })).toContain('BTC')
    expect(translateKey(i18n.getFixedT('en', 'ledger'), 'activity:actions.create')).toBe('Create')
    expect(translateKey(i18n.getFixedT('pt-BR', 'common'), 'accounts:accountTypes.checking')).toBe(
      'Conta Corrente',
    )
  })

  it('accepts a plain function, a real t and rejects an unknown key at compile time', () => {
    const plain: Translate = (key) => String(key)
    const valid: QualifiedKey = 'accounts:accountTypes.savings'
    expect(plain).toBeTypeOf('function')
    expect(valid).toBe('accounts:accountTypes.savings')

    // @ts-expect-error the key is not in any namespace's resources
    const unknown: QualifiedKey = 'accounts:accountTypes.doesNotExist'
    // @ts-expect-error a bare key without its namespace is not a QualifiedKey
    const unqualified: QualifiedKey = 'accountTypes.checking'
    expect([unknown, unqualified]).toHaveLength(2)
  })
})
