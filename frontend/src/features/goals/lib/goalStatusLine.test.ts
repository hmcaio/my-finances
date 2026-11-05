import { describe, expect, it } from 'vitest'
import { goalStatusLine } from './goalStatusLine'

describe('goalStatusLine', () => {
  it('a dated goal with no shortfall is on track', () => {
    expect(
      goalStatusLine({ targetDate: '2028-11-15', projectedShortfall: -200, monthsToTarget: null }),
    ).toEqual({ kind: 'onTrack' })
    expect(
      goalStatusLine({ targetDate: '2028-11-15', projectedShortfall: 0, monthsToTarget: null }),
    ).toEqual({ kind: 'onTrack' })
  })

  it('a dated goal with a shortfall says by how much and at which date', () => {
    expect(
      goalStatusLine({ targetDate: '2028-11-15', projectedShortfall: 5000, monthsToTarget: null }),
    ).toEqual({ kind: 'short', amount: 5000, date: '2028-11-15' })
  })

  it('a dateless goal already at its target is reached', () => {
    expect(
      goalStatusLine({ targetDate: null, projectedShortfall: null, monthsToTarget: 0 }),
    ).toEqual({ kind: 'reached' })
  })

  it('a dateless goal reports the months until it is reached', () => {
    expect(
      goalStatusLine({ targetDate: null, projectedShortfall: null, monthsToTarget: 30 }),
    ).toEqual({ kind: 'reachedInMonths', months: 30 })
  })

  it('a dateless goal that is never reached says not within fifty years', () => {
    expect(
      goalStatusLine({ targetDate: null, projectedShortfall: null, monthsToTarget: null }),
    ).toEqual({ kind: 'notWithin50Years' })
  })
})
