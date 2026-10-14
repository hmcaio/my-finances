import type commonEn from './locales/en/common.json'
import type errorsEn from './locales/en/errors.json'
import type categoriesEn from './locales/en/categories.json'
import type paymentMethodsEn from './locales/en/paymentMethods.json'
import type institutionsEn from './locales/en/institutions.json'
import type accountsEn from './locales/en/accounts.json'

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
      errors: typeof errorsEn
      categories: typeof categoriesEn
      paymentMethods: typeof paymentMethodsEn
      institutions: typeof institutionsEn
      accounts: typeof accountsEn
    }
  }
}
