import { describe, expect, it } from 'vitest'
import { monthRange } from './monthRange'

describe('monthRange', () => {
  it('lists every month from first to last, inclusive', () => {
    expect(monthRange('2026-09', '2026-11')).toEqual(['2026-09', '2026-10', '2026-11'])
  })

  it('rolls over a year boundary', () => {
    expect(monthRange('2026-11', '2027-02')).toEqual(['2026-11', '2026-12', '2027-01', '2027-02'])
  })

  it('returns a single month when first and last are the same', () => {
    expect(monthRange('2026-11', '2026-11')).toEqual(['2026-11'])
  })

  it('returns nothing when last is before first', () => {
    expect(monthRange('2026-11', '2026-09')).toEqual([])
  })
})
