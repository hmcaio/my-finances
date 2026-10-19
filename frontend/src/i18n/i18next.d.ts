import type authEn from './locales/en/auth.json'
import type commonEn from './locales/en/common.json'
import type errorsEn from './locales/en/errors.json'
import type categoriesEn from './locales/en/categories.json'
import type paymentMethodsEn from './locales/en/paymentMethods.json'
import type institutionsEn from './locales/en/institutions.json'
import type accountsEn from './locales/en/accounts.json'
import type investmentCategoriesEn from './locales/en/investmentCategories.json'
import type investmentProductsEn from './locales/en/investmentProducts.json'
import type investmentsEn from './locales/en/investments.json'
import type budgetsEn from './locales/en/budgets.json'
import type transactionsEn from './locales/en/transactions.json'
import type transfersEn from './locales/en/transfers.json'
import type ledgerEn from './locales/en/ledger.json'
import type recurringTemplatesEn from './locales/en/recurringTemplates.json'
import type fuelEn from './locales/en/fuel.json'
import type fiiEn from './locales/en/fii.json'
import type netWorthEn from './locales/en/netWorth.json'
import type dashboardEn from './locales/en/dashboard.json'
import type activityEn from './locales/en/activity.json'
import type onboardingEn from './locales/en/onboarding.json'
import type reportsEn from './locales/en/reports.json'
import type vehiclesEn from './locales/en/vehicles.json'
import type investmentSegmentsEn from './locales/en/investmentSegments.json'

/**
 * Types `t()` call sites against the `en` resources (the canonical key set, F033/ADR 0027) - a
 * typo'd or unknown key is then a compile error. `pt-BR`'s matching key set is enforced separately
 * by `localeParity.test.ts`, since TS only ever checks against this one declared resource shape.
 * A namespace added to `i18n.ts`'s `resources` must be added here too.
 */
declare module 'i18next' {
  interface CustomTypeOptions {
    defaultNS: 'common'
    resources: {
      common: typeof commonEn
      auth: typeof authEn
      errors: typeof errorsEn
      categories: typeof categoriesEn
      paymentMethods: typeof paymentMethodsEn
      institutions: typeof institutionsEn
      accounts: typeof accountsEn
      investmentCategories: typeof investmentCategoriesEn
      investmentProducts: typeof investmentProductsEn
      investments: typeof investmentsEn
      budgets: typeof budgetsEn
      transactions: typeof transactionsEn
      transfers: typeof transfersEn
      ledger: typeof ledgerEn
      recurringTemplates: typeof recurringTemplatesEn
      fuel: typeof fuelEn
      fii: typeof fiiEn
      netWorth: typeof netWorthEn
      dashboard: typeof dashboardEn
      activity: typeof activityEn
      onboarding: typeof onboardingEn
      reports: typeof reportsEn
      vehicles: typeof vehiclesEn
      investmentSegments: typeof investmentSegmentsEn
    }
  }
}
