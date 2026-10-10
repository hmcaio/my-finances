import { accountsHandlers } from './handlers/accounts'
import { allocationPlanHandlers } from './handlers/allocationPlan'
import { auditLogHandlers } from './handlers/auditLog'
import { backupStatusHandlers } from './handlers/backupStatus'
import { budgetsHandlers } from './handlers/budgets'
import { categoriesHandlers } from './handlers/categories'
import { exportHandlers } from './handlers/export'
import { fiiAllocationHandlers } from './handlers/fiiAllocation'
import { fiiDividendsHandlers } from './handlers/fiiDividends'
import { fiiPortfolioHandlers } from './handlers/fiiPortfolio'
import { institutionsHandlers } from './handlers/institutions'
import { investmentCategoriesHandlers } from './handlers/investmentCategories'
import { investmentAllocationHandlers } from './handlers/investmentAllocation'
import { investmentHoldingsHandlers } from './handlers/investmentHoldings'
import { investmentProductsHandlers } from './handlers/investmentProducts'
import { investmentSegmentsHandlers } from './handlers/investmentSegments'
import { investmentSnapshotsHandlers } from './handlers/investmentSnapshots'
import { investmentSubcategoriesHandlers } from './handlers/investmentSubcategories'
import { investmentValueSeriesHandlers } from './handlers/investmentValueSeries'
import { netWorthHandlers } from './handlers/netWorth'
import { paymentMethodsHandlers } from './handlers/paymentMethods'
import { recurringTemplatesHandlers } from './handlers/recurringTemplates'
import { tradeConfirmationLinesHandlers } from './handlers/tradeConfirmationLines'
import { transactionsHandlers } from './handlers/transactions'
import { transfersHandlers } from './handlers/transfers'
import { vehiclesHandlers } from './handlers/vehicles'

/**
 * Combined MSW request handlers for every aggregate (F015 spec). Each aggregate owns one file
 * under `src/mocks/handlers/` (mirrors `src/api`'s one-module-per-aggregate convention); this file
 * just concatenates them into the single array `src/mocks/server.ts` is built from.
 *
 * F003+ each add their own `src/mocks/handlers/<aggregate>.ts` and append it here - the only
 * shared-file touch, so independently-built features don't collide on handler content.
 */
export const handlers = [
  ...accountsHandlers,
  ...allocationPlanHandlers,
  ...auditLogHandlers,
  ...backupStatusHandlers,
  ...budgetsHandlers,
  ...categoriesHandlers,
  ...exportHandlers,
  ...fiiAllocationHandlers,
  ...fiiDividendsHandlers,
  ...fiiPortfolioHandlers,
  ...institutionsHandlers,
  ...investmentCategoriesHandlers,
  ...investmentAllocationHandlers,
  ...investmentHoldingsHandlers,
  ...investmentProductsHandlers,
  ...investmentSegmentsHandlers,
  ...investmentSnapshotsHandlers,
  ...investmentValueSeriesHandlers,
  ...investmentSubcategoriesHandlers,
  ...netWorthHandlers,
  ...paymentMethodsHandlers,
  ...recurringTemplatesHandlers,
  ...tradeConfirmationLinesHandlers,
  ...transactionsHandlers,
  ...transfersHandlers,
  ...vehiclesHandlers,
]
