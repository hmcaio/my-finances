import { accountsHandlers } from './handlers/accounts'
import { categoriesHandlers } from './handlers/categories'
import { paymentMethodsHandlers } from './handlers/paymentMethods'
import { transactionsHandlers } from './handlers/transactions'

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
  ...categoriesHandlers,
  ...paymentMethodsHandlers,
  ...transactionsHandlers,
]
