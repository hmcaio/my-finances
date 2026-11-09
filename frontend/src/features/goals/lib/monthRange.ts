/**
 * Every `YYYY-MM` month from `first` to `last`, inclusive, in order. Empty when `last` is before
 * `first`. Plain arithmetic on the year and month, so no time zone is involved.
 */
export function monthRange(first: string, last: string): string[] {
  const [firstYear, firstMonth] = first.split('-').map(Number)
  const [lastYear, lastMonth] = last.split('-').map(Number)
  const count = (lastYear - firstYear) * 12 + (lastMonth - firstMonth) + 1
  return Array.from({ length: Math.max(0, count) }, (_, offset) => {
    const zeroBased = firstMonth - 1 + offset
    const year = firstYear + Math.floor(zeroBased / 12)
    return `${year}-${String((zeroBased % 12) + 1).padStart(2, '0')}`
  })
}
