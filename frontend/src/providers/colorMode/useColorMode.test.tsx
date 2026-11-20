import { afterEach, describe, expect, it } from 'vitest'
import { act, renderHook } from '@testing-library/react'
import { ColorModeProvider } from './ColorModeProvider'
import { useColorMode } from './useColorMode'

const STORAGE_KEY = 'my-finances:color-mode'

type ChangeListener = (event: MediaQueryListEvent) => void

/**
 * jsdom does not implement `matchMedia` - stub it with the given OS preference. Returns the
 * registered `change` listeners and a function that plays an OS switch to dark/light.
 */
function stubMatchMedia(prefersDark: boolean) {
  const listeners = new Set<ChangeListener>()
  window.matchMedia = ((query: string) => ({
    matches: query === '(prefers-color-scheme: dark)' && prefersDark,
    media: query,
    addEventListener: (_type: string, listener: ChangeListener) => listeners.add(listener),
    removeEventListener: (_type: string, listener: ChangeListener) => listeners.delete(listener),
  })) as unknown as typeof window.matchMedia
  return {
    listeners,
    switchOsTo: (dark: boolean) =>
      act(() => {
        listeners.forEach((listener) => listener({ matches: dark } as MediaQueryListEvent))
      }),
  }
}

function renderColorMode() {
  return renderHook(() => useColorMode(), { wrapper: ColorModeProvider })
}

describe('useColorMode', () => {
  afterEach(() => {
    localStorage.clear()
  })

  it('defaults to the OS preference when nothing is stored (dark)', () => {
    stubMatchMedia(true)

    const { result } = renderColorMode()

    expect(result.current.mode).toBe('dark')
  })

  it('defaults to the OS preference when nothing is stored (light)', () => {
    stubMatchMedia(false)

    const { result } = renderColorMode()

    expect(result.current.mode).toBe('light')
  })

  it('loads a persisted mode from localStorage, ignoring the OS preference', () => {
    stubMatchMedia(false)
    localStorage.setItem(STORAGE_KEY, 'dark')

    const { result } = renderColorMode()

    expect(result.current.mode).toBe('dark')
  })

  it('toggleMode flips the mode and persists it to localStorage', () => {
    stubMatchMedia(false)
    const { result } = renderColorMode()
    expect(result.current.mode).toBe('light')

    act(() => result.current.toggleMode())

    expect(result.current.mode).toBe('dark')
    expect(localStorage.getItem(STORAGE_KEY)).toBe('dark')

    act(() => result.current.toggleMode())

    expect(result.current.mode).toBe('light')
    expect(localStorage.getItem(STORAGE_KEY)).toBe('light')
  })

  describe('OS preference changes', () => {
    it('follows an OS switch while nothing is stored', () => {
      const os = stubMatchMedia(false)
      const { result } = renderColorMode()

      os.switchOsTo(true)
      expect(result.current.mode).toBe('dark')

      os.switchOsTo(false)
      expect(result.current.mode).toBe('light')
      // Following the OS is not a choice: nothing is persisted.
      expect(localStorage.getItem(STORAGE_KEY)).toBeNull()
    })

    it('ignores an OS switch when a mode is stored', () => {
      const os = stubMatchMedia(false)
      localStorage.setItem(STORAGE_KEY, 'light')
      const { result } = renderColorMode()

      os.switchOsTo(true)

      expect(result.current.mode).toBe('light')
    })

    it('stops following the OS once the toggle has been used', () => {
      const os = stubMatchMedia(false)
      const { result } = renderColorMode()
      act(() => result.current.toggleMode())
      expect(result.current.mode).toBe('dark')

      os.switchOsTo(false)

      expect(result.current.mode).toBe('dark')
    })

    it('removes its listener on unmount', () => {
      const os = stubMatchMedia(false)
      const { unmount } = renderColorMode()
      expect(os.listeners.size).toBe(1)

      unmount()

      expect(os.listeners.size).toBe(0)
    })
  })

  it('throws when used outside a ColorModeProvider', () => {
    expect(() => renderHook(() => useColorMode())).toThrow(
      'useColorMode must be used within a ColorModeProvider',
    )
  })
})
