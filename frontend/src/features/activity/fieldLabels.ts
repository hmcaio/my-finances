import type { AuditEntityType } from '../../api/auditLog/auditLog'

/**
 * Friendly translation-key labels for the field names that appear in an audit entry's diff (F025
 * spec: "friendly field labels from a small per-entity map; unknown fields fall back to the raw
 * field name"; F033/ADR 0027 moved the literal English strings onto the `activity` namespace).
 * Fields shared by most aggregates (`name`, `description`, ...) live under `COMMON` and apply to
 * every entity type unless that type's own map overrides them; reference fields (ids) are shown
 * as-is - no extra lookups in v1.
 */
const COMMON: Record<string, string> = {
  name: 'activity:fields.common.name',
  description: 'activity:fields.common.description',
  additionalNotes: 'activity:fields.common.additionalNotes',
  type: 'activity:fields.common.type',
  builtIn: 'activity:fields.common.builtIn',
  active: 'activity:fields.common.active',
  closedDate: 'activity:fields.common.closedDate',
  date: 'activity:fields.common.date',
  effectiveFrom: 'activity:fields.common.effectiveFrom',
  requestId: 'activity:fields.common.requestId',
}

const BY_ENTITY_TYPE: Partial<Record<AuditEntityType, Record<string, string>>> = {
  TRANSACTION: {
    amount: 'activity:fields.transaction.amount',
    categoryId: 'activity:fields.transaction.categoryId',
    accountId: 'activity:fields.transaction.accountId',
    paymentMethodId: 'activity:fields.transaction.paymentMethodId',
    recurringTemplateVersionId: 'activity:fields.transaction.recurringTemplateVersionId',
    vehicleId: 'activity:fields.transaction.vehicleId',
    fuelType: 'activity:fields.transaction.fuelType',
    liters: 'activity:fields.transaction.liters',
    pricePerLiter: 'activity:fields.transaction.pricePerLiter',
    kmSinceLastFill: 'activity:fields.transaction.kmSinceLastFill',
    odometer: 'activity:fields.transaction.odometer',
    investmentHoldingId: 'activity:fields.transaction.investmentHoldingId',
  },
  TRANSFER: {
    fromAccountId: 'activity:fields.transfer.fromAccountId',
    toAccountId: 'activity:fields.transfer.toAccountId',
    amount: 'activity:fields.transfer.amount',
    taxes: 'activity:fields.transfer.taxes',
    tradeConfirmationLines: 'activity:fields.transfer.tradeConfirmationLines',
  },
  ACCOUNT: {
    institutionId: 'activity:fields.account.institutionId',
    openingBalance: 'activity:fields.account.openingBalance',
    openingBalanceDate: 'activity:fields.account.openingBalanceDate',
  },
  CATEGORY: {
    fuelCategory: 'activity:fields.category.fuelCategory',
    dividendCategory: 'activity:fields.category.dividendCategory',
  },
  BUDGET: {
    budgetId: 'activity:fields.budget.budgetId',
    monthlyCap: 'activity:fields.budget.monthlyCap',
  },
  RECURRING_TEMPLATE: {
    categoryId: 'activity:fields.recurringTemplate.categoryId',
    accountId: 'activity:fields.recurringTemplate.accountId',
    templateId: 'activity:fields.recurringTemplate.templateId',
    amount: 'activity:fields.recurringTemplate.amount',
    dayOfMonth: 'activity:fields.recurringTemplate.dayOfMonth',
    lastGeneratedFor: 'activity:fields.recurringTemplate.lastGeneratedFor',
    count: 'activity:fields.recurringTemplate.count',
    firstDate: 'activity:fields.recurringTemplate.firstDate',
    lastDate: 'activity:fields.recurringTemplate.lastDate',
  },
  INVESTMENT_SUBCATEGORY: {
    investmentCategoryId: 'activity:fields.investmentSubcategory.investmentCategoryId',
  },
  INVESTMENT_PRODUCT: {
    investmentCategoryId: 'activity:fields.investmentProduct.investmentCategoryId',
    investmentSubcategoryId: 'activity:fields.investmentProduct.investmentSubcategoryId',
    ticker: 'activity:fields.investmentProduct.ticker',
    segmentId: 'activity:fields.investmentProduct.segmentId',
  },
  INVESTMENT_HOLDING: {
    productId: 'activity:fields.investmentHolding.productId',
    accountId: 'activity:fields.investmentHolding.accountId',
  },
  INVESTMENT_SNAPSHOT: {
    holdingId: 'activity:fields.investmentSnapshot.holdingId',
    balance: 'activity:fields.investmentSnapshot.balance',
  },
  ALLOCATION_PLAN: {
    planId: 'activity:fields.allocationPlan.planId',
    entries: 'activity:fields.allocationPlan.entries',
    investmentProductId: 'activity:fields.allocationPlan.investmentProductId',
    targetPercentage: 'activity:fields.allocationPlan.targetPercentage',
  },
}

/**
 * Friendly label for `field` on `entityType`, falling back to the raw field name. `t` is typed
 * loosely for the same reason `translateSeedName`'s is - see its own docblock
 * (`src/i18n/seedNames.ts`).
 */
export function fieldLabel(
  entityType: AuditEntityType,
  field: string,
  t: (key: never) => string,
): string {
  const key = BY_ENTITY_TYPE[entityType]?.[field] ?? COMMON[field]
  return key ? t(key as never) : field
}
