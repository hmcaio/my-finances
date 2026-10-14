import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'

import commonEn from './locales/en/common.json'
import errorsEn from './locales/en/errors.json'
import commonPtBR from './locales/pt-BR/common.json'
import errorsPtBR from './locales/pt-BR/errors.json'

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
  },
  'pt-BR': {
    common: commonPtBR,
    errors: errorsPtBR,
  },
} as const

// Initial language is set by `LocaleProvider` right after this module is imported (`main.tsx`),
// from `localStorage`/`navigator.language` - `fallbackLng` only matters for a key missing from the
// active language's resources, which full-coverage-at-ship (ADR 0027) means should never happen.
void i18n.use(initReactI18next).init({
  fallbackLng: 'pt-BR',
  defaultNS,
  resources,
  interpolation: { escapeValue: false },
})

export default i18n
