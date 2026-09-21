import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { currentMonth, today } from './localDate'

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
