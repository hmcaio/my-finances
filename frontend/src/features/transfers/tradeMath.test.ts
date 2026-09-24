import { describe, expect, it } from 'vitest'
import {
  grossFromQuantityAndPrice,
  grossTradedValue,
  round2,
  suggestedResultingBalance,
  tradeTotal,
} from './tradeMath'

describe('tradeMath', () => {
  it('round2 rounds to cents without float noise', () => {
    expect(round2(0.1 + 0.2)).toBe(0.3)
    expect(round2(1.005)).toBe(1.01)
    expect(round2(100.10000000000001)).toBe(100.1)
  })

  it('grossFromQuantityAndPrice needs both to be positive numbers', () => {
    expect(grossFromQuantityAndPrice(10, 100)).toBe(1000)
    expect(grossFromQuantityAndPrice(0.01, 100000)).toBe(1000)
    expect(grossFromQuantityAndPrice(null, 100)).toBeNull()
    expect(grossFromQuantityAndPrice(10, null)).toBeNull()
    expect(grossFromQuantityAndPrice(0, 100)).toBeNull()
    expect(grossFromQuantityAndPrice(NaN, 100)).toBeNull()
  })

  it('tradeTotal adds taxes on a buy and subtracts them on a sell', () => {
    expect(tradeTotal('buy', 10, 100, 5)).toBe(1005)
    expect(tradeTotal('sell', 10, 100, 5)).toBe(995)
  })

  it('tradeTotal treats missing taxes as zero and is null without quantity and price', () => {
    expect(tradeTotal('buy', 10, 100, null)).toBe(1000)
    expect(tradeTotal('sell', 2, 50.5, null)).toBe(101)
    expect(tradeTotal('buy', null, 100, 5)).toBeNull()
    expect(tradeTotal('buy', 10, null, 5)).toBeNull()
  })

  it('grossTradedValue prefers quantity x price', () => {
    expect(grossTradedValue('buy', 1005, 5, 10, 100)).toBe(1000)
    // The amount was overridden away from quantity x price: units still win.
    expect(grossTradedValue('buy', 1234, 5, 10, 100)).toBe(1000)
  })

  it('grossTradedValue backs taxes out of the amount when there are no units', () => {
    expect(grossTradedValue('buy', 1005, 5, null, null)).toBe(1000)
    expect(grossTradedValue('sell', 995, 5, null, null)).toBe(1000)
    expect(grossTradedValue('buy', 1000, null, null, null)).toBe(1000)
  })

  it('suggestedResultingBalance moves the latest snapshot by the gross value', () => {
    expect(suggestedResultingBalance('buy', 2000, 1000)).toBe(3000)
    expect(suggestedResultingBalance('buy', 0, 1000)).toBe(1000)
    expect(suggestedResultingBalance('sell', 2000, 500)).toBe(1500)
  })

  it('suggestedResultingBalance never goes below zero on a sell', () => {
    expect(suggestedResultingBalance('sell', 300, 1000)).toBe(0)
  })
})
