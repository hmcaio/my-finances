import type { AuditEntityType } from '../../api/auditLog/auditLog'

/**
 * Friendly labels for the field names that appear in an audit entry's diff (F025 spec: "friendly
 * field labels from a small per-entity map; unknown fields fall back to the raw field name").
 * Fields shared by most aggregates (`name`, `description`, ...) live under `COMMON` and apply to
 * every entity type unless that type's own map overrides them; reference fields (ids) are shown
 * as-is - no extra lookups in v1.
 */
const COMMON: Record<string, string> = {
  name: 'Name',
  description: 'Description',
  additionalNotes: 'Additional notes',
  type: 'Type',
  builtIn: 'Built-in',
  active: 'Active',
  closedDate: 'Closed date',
  date: 'Date',
  effectiveFrom: 'Effective from',
  requestId: 'Request ID',
}

const BY_ENTITY_TYPE: Partial<Record<AuditEntityType, Record<string, string>>> = {
  TRANSACTION: {
    amount: 'Amount',
    categoryId: 'Category',
    accountId: 'Account',
    paymentMethodId: 'Payment method',
    recurringTemplateVersionId: 'Recurring template version',
    vehicleId: 'Vehicle',
    fuelType: 'Fuel type',
    liters: 'Liters',
    pricePerLiter: 'Price per liter',
    kmSinceLastFill: 'Km since last fill',
    odometer: 'Odometer',
    investmentHoldingId: 'Investment holding',
  },
  TRANSFER: {
    fromAccountId: 'From account',
    toAccountId: 'To account',
    amount: 'Amount',
    taxes: 'Taxes',
    tradeConfirmationLines: 'Trade confirmation lines',
  },
  ACCOUNT: {
    institutionId: 'Institution',
    openingBalance: 'Opening balance',
    openingBalanceDate: 'Opening balance date',
  },
  CATEGORY: {
    fuelCategory: 'Fuel category',
    dividendCategory: 'Dividend category',
  },
  BUDGET: {
    budgetId: 'Budget',
    monthlyCap: 'Monthly cap',
  },
  RECURRING_TEMPLATE: {
    categoryId: 'Category',
    accountId: 'Account',
    templateId: 'Template',
    amount: 'Amount',
    dayOfMonth: 'Day of month',
    lastGeneratedFor: 'Last generated for',
    count: 'Occurrences generated',
    firstDate: 'First due date',
    lastDate: 'Last due date',
  },
  INVESTMENT_SUBCATEGORY: {
    investmentCategoryId: 'Investment category',
  },
  INVESTMENT_PRODUCT: {
    investmentCategoryId: 'Investment category',
    investmentSubcategoryId: 'Investment sub-category',
    ticker: 'Ticker',
    segmentId: 'Segment',
  },
  INVESTMENT_HOLDING: {
    productId: 'Product',
    accountId: 'Account',
  },
  INVESTMENT_SNAPSHOT: {
    holdingId: 'Holding',
    balance: 'Balance',
  },
  ALLOCATION_PLAN: {
    planId: 'Allocation plan',
    entries: 'Entries',
    investmentProductId: 'Product',
    targetPercentage: 'Target percentage',
  },
}

/** Friendly label for `field` on `entityType`, falling back to the raw field name. */
export function fieldLabel(entityType: AuditEntityType, field: string): string {
  return BY_ENTITY_TYPE[entityType]?.[field] ?? COMMON[field] ?? field
}
