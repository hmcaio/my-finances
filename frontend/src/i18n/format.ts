import type { Locale } from '../hooks/locale/useLocale'

/**
 * Locale-aware formatters backing every number/date shown in the UI (F033/ADR 0027), replacing
 * every ad hoc `.toFixed()` call and the two hardcoded `'en-US'` date formats that predated real
 * i18n. `pt-BR` renders `1.234,56` / `16 de março de 2026`; `en` renders `1,234.56` /
 * `March 16, 2026` - same precision as before per call site, just locale-driven now. Amounts stay
 * symbol-less in both locales (PRD S3/S6.14): introducing `R$`/`$` is a separate, unrelated
 * decision this feature doesn't make.
 */

/** Money: always 2 decimal places (root CLAUDE.md's `numeric(19,2)` precision), no currency symbol. */
export function formatMoney(amount: number, locale: Locale): string {
  return new Intl.NumberFormat(locale, {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(amount)
}

/** A plain number at a fixed decimal precision (2 money-equivalent, 3 liters, 1 odometer/km, ...). */
export function formatNumber(value: number, locale: Locale, fractionDigits: number): string {
  return new Intl.NumberFormat(locale, {
    minimumFractionDigits: fractionDigits,
    maximumFractionDigits: fractionDigits,
  }).format(value)
}

/**
 * A date (or date+time, via `options`) in the active locale. Defaults to a long calendar date
 * (`dateStyle: 'long'`: "March 16, 2026" / "16 de março de 2026"); pass `options` for a different
 * shape, e.g. a time-of-day label (`{ hour: '2-digit', minute: '2-digit' }`).
 */
export function formatDate(
  date: Date | string,
  locale: Locale,
  options: Intl.DateTimeFormatOptions = { dateStyle: 'long' },
): string {
  const d = typeof date === 'string' ? new Date(date) : date
  return new Intl.DateTimeFormat(locale, options).format(d)
}
