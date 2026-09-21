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
