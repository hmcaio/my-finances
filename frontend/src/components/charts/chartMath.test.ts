import { describe, expect, it } from 'vitest'
import { createDayScale, createYScale, dayNumber, niceStep } from './chartMath'

describe('niceStep', () => {
  it.each([
    [4, 4, 1],
    [8, 4, 2],
    [10, 4, 5],
    [100, 4, 50],
    [30, 4, 10],
    [1000, 4, 500],
  ])('rounds range %s over %s ticks up to %s', (range, ticks, expected) => {
    expect(niceStep(range, ticks)).toBe(expected)
  })

  it('handles sub-unit ranges', () => {
    expect(niceStep(0.3, 4)).toBeCloseTo(0.1, 10)
    expect(niceStep(0.04, 4)).toBeCloseTo(0.01, 10)
  })
})

describe('dayNumber', () => {
  it('counts days since the epoch', () => {
    expect(dayNumber('1970-01-01')).toBe(0)
    expect(dayNumber('1970-01-02')).toBe(1)
  })

  it('differences are whole days, across a leap day too', () => {
    expect(dayNumber('2024-03-01') - dayNumber('2024-02-28')).toBe(2)
    expect(dayNumber('2025-03-01') - dayNumber('2025-02-28')).toBe(1)
  })
})

describe('createYScale', () => {
  const margin = { top: 16, bottom: 32 }

  it('extends the range to nice bounds and lists the ticks', () => {
    const scale = createYScale({ min: 0, max: 87, height: 260, margin })
    expect(scale.step).toBe(50)
    expect(scale.bottom).toBe(0)
    expect(scale.top).toBe(100)
    expect(scale.ticks).toEqual([0, 50, 100])
  })

  it('maps top to the top margin and bottom to the chart floor', () => {
    const scale = createYScale({ min: -20, max: 80, height: 260, margin })
    expect(scale.y(scale.top)).toBe(16)
    expect(scale.y(scale.bottom)).toBe(260 - 32)
  })

  it('does not divide by zero on a flat range', () => {
    const scale = createYScale({ min: 5, max: 5, height: 260, margin })
    expect(Number.isFinite(scale.y(5))).toBe(true)
  })
})

describe('createDayScale', () => {
  it('maps the first date to the left edge and the last to the right edge', () => {
    const x = createDayScale('2024-01-01', '2024-01-11', 64, 500)
    expect(x('2024-01-01')).toBe(64)
    expect(x('2024-01-06')).toBe(64 + 250)
    expect(x('2024-01-11')).toBe(64 + 500)
  })

  it('centers every date when the span is zero', () => {
    const x = createDayScale('2024-01-01', '2024-01-01', 64, 500)
    expect(x('2024-01-01')).toBe(64 + 250)
  })
})
