import { apiClient } from '../core/client'
import { unwrap } from '../core/apiError'
import type { components } from '../generated/schema'

export type AuditEntityType = NonNullable<
  components['schemas']['AuditLogEntryResponse']['entityType']
>
export type AuditAction = NonNullable<components['schemas']['AuditLogEntryResponse']['action']>
export type AuditOrigin = NonNullable<components['schemas']['AuditLogEntryResponse']['origin']>

/** One field's before/after value in an entry's diff (F025 spec, ADR 0022). */
export interface FieldChange {
  from: unknown
  to: unknown
}

/**
 * One append-only audit entry, as returned by the API (PRD S5.12). `changes` is a free-form
 * `{field: {from, to}}` map - `from` is absent for a `CREATE`, `to` is absent for a `DELETE`.
 */
export interface AuditLogEntry {
  id: string
  occurredAt: string
  entityType: AuditEntityType
  entityId: string
  entityLabel: string | null
  action: AuditAction
  origin: AuditOrigin
  changes: Record<string, FieldChange>
  requestId: string | null
}

/** Optional filter dimensions for {@link getAuditLog} (F025 spec's API section). */
export interface AuditLogFilter {
  from?: string
  to?: string
  entityType?: AuditEntityType
  action?: AuditAction
  origin?: AuditOrigin
  entityId?: string
}

/** One page of audit entries - mirrors the backend's `PagedModel` envelope. */
export interface AuditLogPage {
  content: AuditLogEntry[]
  page: {
    size: number
    number: number
    totalElements: number
    totalPages: number
  }
}

/**
 * Fetches a filtered, paginated page of audit entries. `page` is 0-indexed; both `page`/`size`
 * default to the backend's own defaults (0, 20) when omitted, newest first.
 */
export async function getAuditLog(
  filter: AuditLogFilter = {},
  page?: number,
  size?: number,
): Promise<AuditLogPage> {
  return unwrap(apiClient.get<AuditLogPage>('/audit-log', { params: { ...filter, page, size } }))
}
