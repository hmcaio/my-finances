import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'

import commonEn from './locales/en/common.json'
import errorsEn from './locales/en/errors.json'
import categoriesEn from './locales/en/categories.json'
import paymentMethodsEn from './locales/en/paymentMethods.json'
import institutionsEn from './locales/en/institutions.json'
import accountsEn from './locales/en/accounts.json'
import investmentCategoriesEn from './locales/en/investmentCategories.json'
import investmentProductsEn from './locales/en/investmentProducts.json'
import investmentsEn from './locales/en/investments.json'
import budgetsEn from './locales/en/budgets.json'
import transactionsEn from './locales/en/transactions.json'
import transfersEn from './locales/en/transfers.json'
import recurringTemplatesEn from './locales/en/recurringTemplates.json'
import fuelEn from './locales/en/fuel.json'
import fiiEn from './locales/en/fii.json'
import netWorthEn from './locales/en/netWorth.json'
import dashboardEn from './locales/en/dashboard.json'
import commonPtBR from './locales/pt-BR/common.json'
import errorsPtBR from './locales/pt-BR/errors.json'
import categoriesPtBR from './locales/pt-BR/categories.json'
import paymentMethodsPtBR from './locales/pt-BR/paymentMethods.json'
import institutionsPtBR from './locales/pt-BR/institutions.json'
import accountsPtBR from './locales/pt-BR/accounts.json'
import investmentCategoriesPtBR from './locales/pt-BR/investmentCategories.json'
import investmentProductsPtBR from './locales/pt-BR/investmentProducts.json'
import investmentsPtBR from './locales/pt-BR/investments.json'
import budgetsPtBR from './locales/pt-BR/budgets.json'
import transactionsPtBR from './locales/pt-BR/transactions.json'
import transfersPtBR from './locales/pt-BR/transfers.json'
import recurringTemplatesPtBR from './locales/pt-BR/recurringTemplates.json'
import fuelPtBR from './locales/pt-BR/fuel.json'
import fiiPtBR from './locales/pt-BR/fii.json'
import netWorthPtBR from './locales/pt-BR/netWorth.json'
import dashboardPtBR from './locales/pt-BR/dashboard.json'

export const defaultNS = 'common'

/**
 * Translation resources for every namespace, statically imported (F033/ADR 0027: two locales'
 * worth of JSON is trivial bundle weight, so there is no HTTP-backend plugin and no runtime
 * loading state to handle). A namespace is added here, to `i18next.d.ts`'s module augmentation,
 * and to both locales' `locales/<locale>/` folders together - never only one side, or the
 * `localeParity` test and the `t()` type both go stale.
 */
export const resources = {
  en: {
    common: commonEn,
    errors: errorsEn,
    categories: categoriesEn,
    paymentMethods: paymentMethodsEn,
    institutions: institutionsEn,
    accounts: accountsEn,
    investmentCategories: investmentCategoriesEn,
    investmentProducts: investmentProductsEn,
    investments: investmentsEn,
    budgets: budgetsEn,
    transactions: transactionsEn,
    transfers: transfersEn,
    recurringTemplates: recurringTemplatesEn,
    fuel: fuelEn,
    fii: fiiEn,
    netWorth: netWorthEn,
    dashboard: dashboardEn,
  },
  'pt-BR': {
    common: commonPtBR,
    errors: errorsPtBR,
    categories: categoriesPtBR,
    paymentMethods: paymentMethodsPtBR,
    institutions: institutionsPtBR,
    accounts: accountsPtBR,
    investmentCategories: investmentCategoriesPtBR,
    investmentProducts: investmentProductsPtBR,
    investments: investmentsPtBR,
    budgets: budgetsPtBR,
    transactions: transactionsPtBR,
    transfers: transfersPtBR,
    recurringTemplates: recurringTemplatesPtBR,
    fuel: fuelPtBR,
    fii: fiiPtBR,
    netWorth: netWorthPtBR,
    dashboard: dashboardPtBR,
  },
} as const

// `lng: 'en'` is this module's own synchronous starting point - `LocaleProvider` (mounted once,
// by `App.tsx`) immediately drives the real active language from `localStorage`/
// `navigator.language` via its layout effect, so a real page only ever shows this initial value
// for the first (pre-effect) paint. It also matches jsdom's default `navigator.language` ('en-US')
// and this suite's long-standing "English/dot-decimal by default" assumption for every test that
// renders a component without wrapping it in `LocaleProvider` (most of them - only
// Layout/App/useLocale tests do). `fallbackLng` only matters for a key missing from the active
// language's resources, which full-coverage-at-ship (ADR 0027) means should never happen; it stays
// `pt-BR` per ADR 0027's own "ambiguous defaults to pt-BR" rule.
void i18n.use(initReactI18next).init({
  lng: 'en',
  fallbackLng: 'pt-BR',
  defaultNS,
  resources,
  interpolation: { escapeValue: false },
})

export default i18n
