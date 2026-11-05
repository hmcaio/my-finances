import { describe, expect, it } from 'vitest'
import { retirementTarget } from './retirementTarget'

describe('retirementTarget', () => {
  it('is the desired monthly income times twelve over the withdrawal rate', () => {
    expect(retirementTarget(8000, 4)).toBe(2400000)
  })

  it('rounds to cents', () => {
    expect(retirementTarget(1000, 3)).toBe(400000)
    expect(retirementTarget(1234.56, 3.5)).toBe(423277.71)
  })

  it('is null when the income or the rate is not positive', () => {
    expect(retirementTarget(0, 4)).toBeNull()
    expect(retirementTarget(-100, 4)).toBeNull()
    expect(retirementTarget(8000, 0)).toBeNull()
    expect(retirementTarget(8000, -1)).toBeNull()
  })

  it('is null for a value that is not a finite number', () => {
    expect(retirementTarget(Number.NaN, 4)).toBeNull()
    expect(retirementTarget(8000, Number.POSITIVE_INFINITY)).toBeNull()
  })
})
