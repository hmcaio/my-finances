import type { AuditLogEntry } from '../../api/auditLog/auditLog'

export interface AuditLogDayGroup {
  /** `YYYY-MM-DD` in the viewer's local time zone - a stable React key, not shown. */
  dateKey: string
  /** Human-readable heading for the group, e.g. "March 16, 2026". */
  label: string
  entries: AuditLogEntry[]
}

/** `YYYY-MM-DD` for `date` in the browser's local time zone (not UTC). */
function localDateKey(date: Date): string {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

const DAY_LABEL_FORMAT: Intl.DateTimeFormatOptions = {
  year: 'numeric',
  month: 'long',
  day: 'numeric',
}

/**
 * Groups a page of audit entries by day in the viewer's local time zone (F025 spec's Activity
 * page, PRD S6.12: "grouped by day in the viewer's local time zone") - `occurredAt` is a UTC
 * instant, so an entry near midnight can fall on a different local day than its UTC date. Entries
 * are expected newest-first (the API's own order); groups come out in that same order, and each
 * group's entries keep their relative order.
 *
 * <p>The day *boundary* follows the viewer's local time zone (via plain {@link Date} getters,
 * which read the runtime's zone - `TZ`); the *label* is always formatted in `en-US`, like every
 * other piece of UI text in this app, rather than the runtime's default locale (which in a
 * `pt-BR`-flavored environment would otherwise render e.g. "16 de março de 2026").
 */
export function groupEntriesByLocalDay(entries: AuditLogEntry[]): AuditLogDayGroup[] {
  const groups: AuditLogDayGroup[] = []
  for (const entry of entries) {
    const occurredAt = new Date(entry.occurredAt)
    const dateKey = localDateKey(occurredAt)
    const lastGroup = groups.at(-1)
    if (lastGroup && lastGroup.dateKey === dateKey) {
      lastGroup.entries.push(entry)
    } else {
      groups.push({
        dateKey,
        label: occurredAt.toLocaleDateString('en-US', DAY_LABEL_FORMAT),
        entries: [entry],
      })
    }
  }
  return groups
}
