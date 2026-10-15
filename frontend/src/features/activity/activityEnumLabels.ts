import type { AuditAction, AuditEntityType, AuditOrigin } from '../../api/auditLog/auditLog'

/**
 * Translation-key lookups for the Activity page's three enums (F033/ADR 0027: were a single
 * generic `enumLabel()` title-caser before this feature). `t` is typed loosely for the same
 * reason `translateSeedName`'s is - see its own docblock (`src/i18n/seedNames.ts`).
 */
const ENTITY_TYPE_KEYS: Record<AuditEntityType, string> = {
  TRANSACTION: 'activity:entityTypes.transaction',
  TRANSFER: 'activity:entityTypes.transfer',
  ACCOUNT: 'activity:entityTypes.account',
  CATEGORY: 'activity:entityTypes.category',
  PAYMENT_METHOD: 'activity:entityTypes.paymentMethod',
  INSTITUTION: 'activity:entityTypes.institution',
  BUDGET: 'activity:entityTypes.budget',
  RECURRING_TEMPLATE: 'activity:entityTypes.recurringTemplate',
  INVESTMENT_CATEGORY: 'activity:entityTypes.investmentCategory',
  INVESTMENT_SUBCATEGORY: 'activity:entityTypes.investmentSubcategory',
  INVESTMENT_PRODUCT: 'activity:entityTypes.investmentProduct',
  INVESTMENT_HOLDING: 'activity:entityTypes.investmentHolding',
  INVESTMENT_SNAPSHOT: 'activity:entityTypes.investmentSnapshot',
  INVESTMENT_SEGMENT: 'activity:entityTypes.investmentSegment',
  ALLOCATION_PLAN: 'activity:entityTypes.allocationPlan',
  VEHICLE: 'activity:entityTypes.vehicle',
}

const ACTION_KEYS: Record<AuditAction, string> = {
  CREATE: 'activity:actions.create',
  UPDATE: 'activity:actions.update',
  DELETE: 'activity:actions.delete',
  CLOSE: 'activity:actions.close',
  REOPEN: 'activity:actions.reopen',
  STOPPED: 'activity:actions.stopped',
  GENERATED: 'activity:actions.generated',
}

const ORIGIN_KEYS: Record<AuditOrigin, string> = {
  USER: 'activity:origins.user',
  SYSTEM: 'activity:origins.system',
}

export function entityTypeLabel(type: AuditEntityType, t: (key: never) => string): string {
  return t(ENTITY_TYPE_KEYS[type] as never)
}

export function actionLabel(action: AuditAction, t: (key: never) => string): string {
  return t(ACTION_KEYS[action] as never)
}

export function originLabel(origin: AuditOrigin, t: (key: never) => string): string {
  return t(ORIGIN_KEYS[origin] as never)
}
