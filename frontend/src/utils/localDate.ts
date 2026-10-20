// Calendar "now" in the user's own time zone. `new Date().toISOString()` is always UTC, so after
// 21:00 in UTC-3 it already reads as tomorrow (and as next month on the last day of a month) -
// never derive a default date or month from it.

function pad(value: number): string {
  return String(value).padStart(2, '0')
}

/** Today's local date as `YYYY-MM-DD` (the value format of `<input type="date">`). */
export function today(): string {
  const now = new Date()
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`
}

/** The current local month as `YYYY-MM` (the value format of `<input type="month">`). */
export function currentMonth(): string {
  const now = new Date()
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}`
}

/**
 * The month `delta` months after `month` (`YYYY-MM`, negative for before), rolling the year over
 * in either direction. Pure integer arithmetic: no `Date`, so no time zone can shift the result.
 */
export function shiftMonth(month: string, delta: number): string {
  const [year, monthNumber] = month.split('-').map(Number)
  const index = year * 12 + (monthNumber - 1) + delta
  return `${Math.floor(index / 12)}-${pad((index % 12) + 1)}`
}

/**
 * The first and last calendar date of `month` (`YYYY-MM`) as `YYYY-MM-DD`, in local-date terms (no
 * UTC conversion): `new Date(year, monthNumber, 0)` is day 0 of the next month, i.e. the last day
 * of this one, and its day-of-month does not depend on the time zone.
 */
export function monthRange(month: string): { from: string; to: string } {
  const [year, monthNumber] = month.split('-').map(Number)
  const lastDay = new Date(year, monthNumber, 0).getDate()
  return { from: `${month}-01`, to: `${month}-${pad(lastDay)}` }
}
