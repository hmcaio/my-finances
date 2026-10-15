import { useLayoutEffect, useMemo, useState, type PropsWithChildren } from 'react'
import i18n from '../../i18n/i18n'
import { LocaleContext, type Locale, type LocaleContextValue } from './useLocale'

const STORAGE_KEY = 'my-finances:locale'

function getInitialLocale(): Locale {
  const stored = localStorage.getItem(STORAGE_KEY)
  if (stored === 'en' || stored === 'pt-BR') {
    return stored
  }
  return navigator.language.startsWith('pt') ? 'pt-BR' : 'en'
}

/**
 * Per-device UI preference only (no backend involved), mirroring `ColorModeProvider` exactly
 * (F033/ADR 0027): defaults to a `navigator.language` guess on first load (`pt*` -> `pt-BR`,
 * else `en`), then persisted to `localStorage`. The one difference from `ColorModeProvider` is
 * the effect below: `i18next` is a mutable singleton outside React, so syncing it to the current
 * `locale` needs an explicit side effect rather than just feeding a value to `<ThemeProvider>`.
 * A layout effect (not a plain effect) so it runs before the browser paints - `i18n.ts`'s own
 * module-level `lng: 'en'` is otherwise visible for one paint on every load, including a
 * `pt-BR`-detected one.
 *
 * Also syncs `<html lang>` to the active locale: correct a11y practice regardless, and the reason
 * Chromium's native `<input type="date">`/`type="month">` picker (month names, date order) follows
 * this app's language instead of just the OS locale - Firefox/Safari don't honor `lang` for that
 * and keep following OS settings regardless (accepted gap, F033 spec's Non-goals).
 */
export function LocaleProvider({ children }: PropsWithChildren) {
  const [locale, setLocale] = useState<Locale>(getInitialLocale)

  useLayoutEffect(() => {
    void i18n.changeLanguage(locale)
    document.documentElement.lang = locale
  }, [locale])

  const value = useMemo<LocaleContextValue>(
    () => ({
      locale,
      toggleLocale: () => {
        setLocale((prev) => {
          const next = prev === 'pt-BR' ? 'en' : 'pt-BR'
          localStorage.setItem(STORAGE_KEY, next)
          return next
        })
      },
    }),
    [locale],
  )

  return <LocaleContext.Provider value={value}>{children}</LocaleContext.Provider>
}
