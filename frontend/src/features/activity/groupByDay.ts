import type { AuditLogEntry } from '../../api/auditLog/auditLog'
import type { Locale } from '../../hooks/locale/useLocale'
import { formatDate } from '../../i18n/format'

export interface AuditLogDayGroup {
  /** `YYYY-MM-DD` in the viewer's local time zone - a stable React key, not shown. */
  dateKey: string
  /** Human-readable heading for the group, e.g. "March 16, 2026" / "16 de março de 2026". */
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

/**
 * Groups a page of audit entries by day in the viewer's local time zone (F025 spec's Activity
 * page, PRD S6.12: "grouped by day in the viewer's local time zone") - `occurredAt` is a UTC
 * instant, so an entry near midnight can fall on a different local day than its UTC date. Entries
 * are expected newest-first (the API's own order); groups come out in that same order, and each
 * group's entries keep their relative order.
 *
 * <p>The day *boundary* follows the viewer's local time zone (via plain {@link Date} getters,
 * which read the runtime's zone - `TZ`); the *label* follows `locale` via `formatDate` (F033/ADR
 * 0027 - was always formatted in hardcoded `en-US` before this feature, regardless of the active
 * UI language).
 */
export function groupEntriesByLocalDay(
  entries: AuditLogEntry[],
  locale: Locale,
): AuditLogDayGroup[] {
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
        label: formatDate(occurredAt, locale),
        entries: [entry],
      })
    }
  }
  return groups
}
