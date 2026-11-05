import { createContext, useContext } from 'react'

export type Locale = 'en' | 'pt-BR'

export interface LocaleContextValue {
  locale: Locale
  toggleLocale: () => void
}

export const LocaleContext = createContext<LocaleContextValue | undefined>(undefined)

export function useLocale(): LocaleContextValue {
  const context = useContext(LocaleContext)
  if (!context) {
    throw new Error('useLocale must be used within a LocaleProvider')
  }
  return context
}
