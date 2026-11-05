import { describe, expect, it } from 'vitest'
import { lineTotal, netSettlementDirection, netSettlementPreview, round2 } from './tradeMath'

describe('tradeMath', () => {
  it('round2 rounds to cents without float noise', () => {
    expect(round2(0.1 + 0.2)).toBe(0.3)
    expect(round2(1.005)).toBe(1.01)
    expect(round2(100.10000000000001)).toBe(100.1)
  })

  it('lineTotal needs both quantity and unit price to be positive numbers', () => {
    expect(lineTotal(10, 100)).toBe(1000)
    expect(lineTotal(0.01, 100000)).toBe(1000)
    expect(lineTotal(null, 100)).toBeNull()
    expect(lineTotal(10, null)).toBeNull()
    expect(lineTotal(0, 100)).toBeNull()
    expect(lineTotal(NaN, 100)).toBeNull()
  })

  it('netSettlementPreview sums a single BUY line plus taxes', () => {
    expect(netSettlementPreview([{ side: 'BUY', quantity: 10, unitPrice: 100 }], 5)).toBe(1005)
  })

  it('netSettlementPreview subtracts a single SELL line, then adds taxes', () => {
    expect(netSettlementPreview([{ side: 'SELL', quantity: 10, unitPrice: 100 }], 5)).toBe(-995)
  })

  it('netSettlementPreview nets BUY against SELL across multiple lines', () => {
    const lines = [
      { side: 'BUY' as const, quantity: 10, unitPrice: 100 },
      { side: 'SELL' as const, quantity: 5, unitPrice: 50 },
    ]
    expect(netSettlementPreview(lines, 10)).toBe(760)
  })

  it('netSettlementPreview treats missing taxes as zero', () => {
    expect(netSettlementPreview([{ side: 'BUY', quantity: 10, unitPrice: 100 }], null)).toBe(1000)
  })

  it('netSettlementPreview is null until every line has quantity and unit price', () => {
    expect(netSettlementPreview([{ side: 'BUY', quantity: null, unitPrice: 100 }], 0)).toBeNull()
    expect(
      netSettlementPreview(
        [
          { side: 'BUY', quantity: 10, unitPrice: 100 },
          { side: 'SELL', quantity: 5, unitPrice: null },
        ],
        0,
      ),
    ).toBeNull()
  })

  it('netSettlementPreview rounds only the final figure', () => {
    // 3 * 0.333 = 0.999, + 0.0006 taxes = 0.9996 -> rounds to 1.00.
    expect(netSettlementPreview([{ side: 'BUY', quantity: 3, unitPrice: 0.333 }], 0.0006)).toBe(1)
  })

  it('netSettlementDirection reads the sign: positive is a cost, negative is proceeds', () => {
    expect(netSettlementDirection(1005)).toBe('cost')
    expect(netSettlementDirection(-995)).toBe('proceeds')
    expect(netSettlementDirection(0)).toBeNull()
    expect(netSettlementDirection(null)).toBeNull()
  })
})
