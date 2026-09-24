import { accountsHandlers } from './handlers/accounts'
import { budgetsHandlers } from './handlers/budgets'
import { categoriesHandlers } from './handlers/categories'
import { healthHandlers } from './handlers/health'
import { institutionsHandlers } from './handlers/institutions'
import { investmentCategoriesHandlers } from './handlers/investmentCategories'
import { investmentAllocationHandlers } from './handlers/investmentAllocation'
import { investmentProductsHandlers } from './handlers/investmentProducts'
import { investmentSnapshotsHandlers } from './handlers/investmentSnapshots'
import { investmentSubcategoriesHandlers } from './handlers/investmentSubcategories'
import { investmentValueSeriesHandlers } from './handlers/investmentValueSeries'
import { netWorthHandlers } from './handlers/netWorth'
import { paymentMethodsHandlers } from './handlers/paymentMethods'
import { recurringTemplatesHandlers } from './handlers/recurringTemplates'
import { transactionsHandlers } from './handlers/transactions'
import { transfersHandlers } from './handlers/transfers'

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
  ...budgetsHandlers,
  ...categoriesHandlers,
  ...healthHandlers,
  ...institutionsHandlers,
  ...investmentCategoriesHandlers,
  ...investmentAllocationHandlers,
  ...investmentProductsHandlers,
  ...investmentSnapshotsHandlers,
  ...investmentValueSeriesHandlers,
  ...investmentSubcategoriesHandlers,
  ...netWorthHandlers,
  ...paymentMethodsHandlers,
  ...recurringTemplatesHandlers,
  ...transactionsHandlers,
  ...transfersHandlers,
]
