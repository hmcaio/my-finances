import { describe, expect, it } from 'vitest'
import { formatDate, formatMoney, formatNumber } from './format'

describe('formatMoney', () => {
  it('formats with a thousands separator and 2 decimals in en', () => {
    expect(formatMoney(1234.5, 'en')).toBe('1,234.50')
  })

  it('formats with a thousands separator and 2 decimals in pt-BR', () => {
    expect(formatMoney(1234.5, 'pt-BR')).toBe('1.234,50')
  })

  it('rounds to 2 decimals', () => {
    expect(formatMoney(10, 'en')).toBe('10.00')
    expect(formatMoney(10, 'pt-BR')).toBe('10,00')
  })
})

describe('formatNumber', () => {
  it('formats liters at 3 decimals', () => {
    expect(formatNumber(45.5, 'en', 3)).toBe('45.500')
    expect(formatNumber(45.5, 'pt-BR', 3)).toBe('45,500')
  })

  it('formats odometer/km at 1 decimal with a thousands separator', () => {
    expect(formatNumber(12345.6, 'en', 1)).toBe('12,345.6')
    expect(formatNumber(12345.6, 'pt-BR', 1)).toBe('12.345,6')
  })

  it('formats at 2 decimals', () => {
    expect(formatNumber(7, 'en', 2)).toBe('7.00')
    expect(formatNumber(7, 'pt-BR', 2)).toBe('7,00')
  })
})

describe('formatDate', () => {
  // Built from local date components (not an ISO string) so the assertion doesn't depend on the
  // runtime's time zone (tests run with TZ=America/Sao_Paulo, build.gradle/vite.config.ts) -
  // parsing '2026-03-16' as UTC midnight would render as the 15th there.
  const date = new Date(2026, 2, 16)

  it('formats a long calendar date in en', () => {
    expect(formatDate(date, 'en')).toBe('March 16, 2026')
  })

  it('formats a long calendar date in pt-BR', () => {
    expect(formatDate(date, 'pt-BR')).toBe('16 de março de 2026')
  })

  it('accepts an ISO date string', () => {
    expect(formatDate('2026-03-16T12:00:00', 'en')).toBe('March 16, 2026')
  })

  it('accepts custom options, e.g. a time-of-day label', () => {
    const date = new Date('2026-03-16T14:05:00Z')
    expect(formatDate(date, 'en', { hour: '2-digit', minute: '2-digit', timeZone: 'UTC' })).toBe(
      '02:05 PM',
    )
  })
})
