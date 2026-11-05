import type { TFunction } from 'i18next'
import type { Locale } from '../../../providers/locale/useLocale'
import { formatNumber } from '../../../i18n/format'

/** A reference field's value once the backend has resolved its label (see `AuditReferenceLabels`). */
interface ReferenceValue {
  id: string
  label: string | null
}

function isReferenceValue(value: unknown): value is ReferenceValue {
  return (
    typeof value === 'object' &&
    value !== null &&
    'id' in value &&
    'label' in value &&
    typeof (value as { id: unknown }).id === 'string'
  )
}

/**
 * Renders a diff value: `null`/`undefined` as a dash, booleans as Yes/No, a resolved reference as
 * "Label (id)" (falling back to the bare id when the backend had no label for it, e.g. an older
 * audit row predating label resolution, or a deleted referenced entity), everything else as-is.
 */
export function formatDiffValue(value: unknown, locale: Locale, t: TFunction<'activity'>): string {
  if (value === null || value === undefined) return '—'
  if (typeof value === 'boolean') return value ? t('boolean.yes') : t('boolean.no')
  if (typeof value === 'number')
    return Number.isInteger(value) ? String(value) : formatNumber(value, locale, 2)
  if (isReferenceValue(value)) return value.label ? `${value.label} (${value.id})` : value.id
  if (typeof value === 'object') return JSON.stringify(value)
  return String(value)
}
