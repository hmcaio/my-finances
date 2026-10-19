import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { currentMonth, monthRange, shiftMonth, today } from './localDate'

// America/Sao_Paulo is UTC-3 all year (no DST since 2019). CI runs in UTC, where a UTC-vs-local
// bug is invisible, so the zone is pinned and only Date is faked (timers keep running).
beforeEach(() => {
  vi.stubEnv('TZ', 'America/Sao_Paulo')
  vi.useFakeTimers({ toFake: ['Date'] })
})

afterEach(() => {
  vi.useRealTimers()
  vi.unstubAllEnvs()
})

describe('today', () => {
  it('returns the local date, not the UTC date, late in the evening', () => {
    // 23:30 local on 31 March is already 1 April in UTC.
    vi.setSystemTime(new Date(2026, 2, 31, 23, 30))

    expect(today()).toBe('2026-03-31')
  })

  it('returns the local date just after local midnight', () => {
    // 00:30 local on 1 April is 03:30 UTC the same day, so local and UTC agree here.
    vi.setSystemTime(new Date(2026, 3, 1, 0, 30))

    expect(today()).toBe('2026-04-01')
  })

  it('zero-pads the month and day', () => {
    vi.setSystemTime(new Date(2026, 0, 5, 12, 0))

    expect(today()).toBe('2026-01-05')
  })
})

describe('currentMonth', () => {
  it('returns the local month on the last evening of the month', () => {
    vi.setSystemTime(new Date(2026, 2, 31, 23, 30))

    expect(currentMonth()).toBe('2026-03')
  })

  it('returns the new month just after local midnight', () => {
    vi.setSystemTime(new Date(2026, 3, 1, 0, 30))

    expect(currentMonth()).toBe('2026-04')
  })

  it('rolls the year over correctly and zero-pads the month', () => {
    vi.setSystemTime(new Date(2026, 0, 1, 0, 30))

    expect(currentMonth()).toBe('2026-01')
  })
})

describe('shiftMonth', () => {
  it('moves within a year in either direction', () => {
    expect(shiftMonth('2026-05', 1)).toBe('2026-06')
    expect(shiftMonth('2026-05', -1)).toBe('2026-04')
    expect(shiftMonth('2026-05', 0)).toBe('2026-05')
  })

  it('rolls over the year boundary forward and backward', () => {
    expect(shiftMonth('2026-12', 1)).toBe('2027-01')
    expect(shiftMonth('2026-01', -1)).toBe('2025-12')
  })

  it('handles multi-month and multi-year jumps and zero-pads', () => {
    expect(shiftMonth('2026-10', 3)).toBe('2027-01')
    expect(shiftMonth('2026-03', -15)).toBe('2024-12')
    expect(shiftMonth('2026-08', 25)).toBe('2028-09')
    expect(shiftMonth('2026-02', -1)).toBe('2026-01')
  })
})

describe('monthRange', () => {
  it('covers a 31-day and a 30-day month', () => {
    expect(monthRange('2026-01')).toEqual({ from: '2026-01-01', to: '2026-01-31' })
    expect(monthRange('2026-04')).toEqual({ from: '2026-04-01', to: '2026-04-30' })
  })

  it('knows February in common and leap years, and the century rule', () => {
    expect(monthRange('2026-02').to).toBe('2026-02-28')
    expect(monthRange('2028-02').to).toBe('2028-02-29')
    expect(monthRange('2100-02').to).toBe('2100-02-28')
    expect(monthRange('2000-02').to).toBe('2000-02-29')
  })

  it('handles December, the year boundary', () => {
    expect(monthRange('2026-12')).toEqual({ from: '2026-12-01', to: '2026-12-31' })
  })

  it('is not shifted by the time zone', () => {
    vi.stubEnv('TZ', 'Pacific/Kiritimati')
    expect(monthRange('2026-03').to).toBe('2026-03-31')
    vi.stubEnv('TZ', 'Pacific/Pago_Pago')
    expect(monthRange('2026-03').to).toBe('2026-03-31')
  })
})
