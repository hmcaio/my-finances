import type { AuditAction, AuditEntityType, AuditOrigin } from '../../api/auditLog/auditLog'

export const ENTITY_TYPES: AuditEntityType[] = [
  'TRANSACTION',
  'TRANSFER',
  'ACCOUNT',
  'CATEGORY',
  'PAYMENT_METHOD',
  'INSTITUTION',
  'BUDGET',
  'RECURRING_TEMPLATE',
  'INVESTMENT_CATEGORY',
  'INVESTMENT_SUBCATEGORY',
  'INVESTMENT_PRODUCT',
  'INVESTMENT_HOLDING',
  'INVESTMENT_SNAPSHOT',
  'INVESTMENT_OPENING_POSITION',
  'INVESTMENT_SEGMENT',
  'ALLOCATION_PLAN',
  'VEHICLE',
]

export const ACTIONS: AuditAction[] = [
  'CREATE',
  'UPDATE',
  'DELETE',
  'CLOSE',
  'REOPEN',
  'STOPPED',
  'GENERATED',
]

export const ORIGINS: AuditOrigin[] = ['USER', 'SYSTEM']

/** Start of `dateStr` (`YYYY-MM-DD`) in the viewer's local time zone, as a UTC instant string. */
export function startOfDayIso(dateStr: string): string {
  return new Date(`${dateStr}T00:00:00`).toISOString()
}

/** End of `dateStr` (`YYYY-MM-DD`) in the viewer's local time zone, as a UTC instant string. */
export function endOfDayIso(dateStr: string): string {
  return new Date(`${dateStr}T23:59:59.999`).toISOString()
}
