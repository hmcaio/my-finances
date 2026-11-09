import type { AuditAction, AuditEntityType, AuditOrigin } from '../../../api/auditLog/auditLog'
import { translateKey, type QualifiedKey, type Translate } from '../../../i18n/translate'

/**
 * Translation-key lookups for the Activity page's three enums (F033/ADR 0027: were a single
 * generic `enumLabel()` title-caser before this feature). `t` is the loosely typed `Translate`
 * (`src/i18n/translate.ts`); the keys are checked against the `en` resources by `QualifiedKey`.
 */
const ENTITY_TYPE_KEYS: Record<AuditEntityType, QualifiedKey> = {
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
  INVESTMENT_OPENING_POSITION: 'activity:entityTypes.investmentOpeningPosition',
  INVESTMENT_SEGMENT: 'activity:entityTypes.investmentSegment',
  ALLOCATION_PLAN: 'activity:entityTypes.allocationPlan',
  GOAL: 'activity:entityTypes.goal',
  VEHICLE: 'activity:entityTypes.vehicle',
}

const ACTION_KEYS: Record<AuditAction, QualifiedKey> = {
  CREATE: 'activity:actions.create',
  UPDATE: 'activity:actions.update',
  DELETE: 'activity:actions.delete',
  CLOSE: 'activity:actions.close',
  REOPEN: 'activity:actions.reopen',
  STOPPED: 'activity:actions.stopped',
  GENERATED: 'activity:actions.generated',
}

const ORIGIN_KEYS: Record<AuditOrigin, QualifiedKey> = {
  USER: 'activity:origins.user',
  SYSTEM: 'activity:origins.system',
}

export function entityTypeLabel(type: AuditEntityType, t: Translate): string {
  return translateKey(t, ENTITY_TYPE_KEYS[type])
}

export function actionLabel(action: AuditAction, t: Translate): string {
  return translateKey(t, ACTION_KEYS[action])
}

export function originLabel(origin: AuditOrigin, t: Translate): string {
  return translateKey(t, ORIGIN_KEYS[origin])
}
