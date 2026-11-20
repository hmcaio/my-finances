import { useEffect, useMemo, useState, type PropsWithChildren } from 'react'
import type { PaletteMode } from '@mui/material'
import { ColorModeContext, type ColorModeContextValue } from './useColorMode'

const STORAGE_KEY = 'my-finances:color-mode'
const PREFERS_DARK_QUERY = '(prefers-color-scheme: dark)'

function getStoredMode(): PaletteMode | null {
  const stored = localStorage.getItem(STORAGE_KEY)
  return stored === 'light' || stored === 'dark' ? stored : null
}

function getInitialMode(): PaletteMode {
  return getStoredMode() ?? (window.matchMedia(PREFERS_DARK_QUERY).matches ? 'dark' : 'light')
}

/**
 * Per-device UI preference only (no backend involved) - defaults to the OS preference on
 * first load, then persisted to localStorage (see F001 spec's "Dark mode toggle" section). Until
 * the toggle is used the mode keeps following the OS (a sunset schedule, a system switch); after
 * that the stored choice wins and OS changes are ignored.
 */
export function ColorModeProvider({ children }: PropsWithChildren) {
  const [mode, setMode] = useState<PaletteMode>(getInitialMode)

  useEffect(() => {
    const query = window.matchMedia(PREFERS_DARK_QUERY)
    const onChange = (event: MediaQueryListEvent) => {
      // Read the storage when the event fires, not at mount: a toggle after mount must stop this.
      if (getStoredMode() === null) {
        setMode(event.matches ? 'dark' : 'light')
      }
    }
    query.addEventListener('change', onChange)
    return () => query.removeEventListener('change', onChange)
  }, [])

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
