import { formatDate, formatNumber } from '../../../i18n/format'
import type { Locale } from '../../../providers/locale/useLocale'

/** A plain `YYYY-MM-DD` date: formatted in UTC so no local time zone shifts it a day. */
const DATE_OPTIONS: Intl.DateTimeFormatOptions = { dateStyle: 'long', timeZone: 'UTC' }

/** A goal's target date (`YYYY-MM-DD`) as a long calendar date in the active locale. */
export function formatGoalDate(date: string, locale: Locale): string {
  return formatDate(date, locale, DATE_OPTIONS)
}

/** A `YYYY-MM` month as a short month and year ("Nov 2026") in the active locale. */
export function formatGoalMonth(month: string, locale: Locale): string {
  return formatDate(`${month}-01`, locale, { month: 'short', year: 'numeric', timeZone: 'UTC' })
}

/** Progress as a percentage with one decimal; it may exceed 100. */
export function formatProgress(percent: number, locale: Locale): string {
  return formatNumber(percent, locale, 1)
}

/** The bar fills to 100 at most, whatever the progress. */
export function barValue(percent: number): number {
  return Math.min(100, Math.max(0, percent))
}

/** At most two decimal places in the typed text (the money and percentage columns' scale). */
export function hasAtMostTwoDecimals(text: string): boolean {
  return /^-?\d*(\.\d{0,2})?$/.test(text.trim())
}
