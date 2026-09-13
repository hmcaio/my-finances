import { createContext, useContext, useMemo, useState, type PropsWithChildren } from 'react'
import type { PaletteMode } from '@mui/material'

const STORAGE_KEY = 'my-finances:color-mode'

interface ColorModeContextValue {
  mode: PaletteMode
  toggleMode: () => void
}

const ColorModeContext = createContext<ColorModeContextValue | undefined>(undefined)

function getInitialMode(): PaletteMode {
  const stored = localStorage.getItem(STORAGE_KEY)
  if (stored === 'light' || stored === 'dark') {
    return stored
  }
  const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches
  return prefersDark ? 'dark' : 'light'
}

/**
 * Per-device UI preference only (no backend involved) - defaults to the OS preference on
 * first load, then persisted to localStorage (see F001 spec's "Dark mode toggle" section).
 */
export function ColorModeProvider({ children }: PropsWithChildren) {
  const [mode, setMode] = useState<PaletteMode>(getInitialMode)

  const value = useMemo<ColorModeContextValue>(
    () => ({
      mode,
      toggleMode: () => {
        setMode((prev) => {
          const next = prev === 'light' ? 'dark' : 'light'
          localStorage.setItem(STORAGE_KEY, next)
          return next
        })
      },
    }),
    [mode],
  )

  return <ColorModeContext.Provider value={value}>{children}</ColorModeContext.Provider>
}

export function useColorMode(): ColorModeContextValue {
  const context = useContext(ColorModeContext)
  if (!context) {
    throw new Error('useColorMode must be used within a ColorModeProvider')
  }
  return context
}
