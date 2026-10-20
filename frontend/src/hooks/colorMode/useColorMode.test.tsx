import { afterEach, describe, expect, it } from 'vitest'
import { act, renderHook } from '@testing-library/react'
import { ColorModeProvider } from './ColorModeProvider'
import { useColorMode } from './useColorMode'

const STORAGE_KEY = 'my-finances:color-mode'

/** jsdom does not implement `matchMedia` - stub it with the given OS preference. */
function stubMatchMedia(prefersDark: boolean) {
  window.matchMedia = ((query: string) => ({
    matches: query === '(prefers-color-scheme: dark)' && prefersDark,
    media: query,
    addEventListener: () => {},
    removeEventListener: () => {},
  })) as unknown as typeof window.matchMedia
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

  it('throws when used outside a ColorModeProvider', () => {
    expect(() => renderHook(() => useColorMode())).toThrow(
      'useColorMode must be used within a ColorModeProvider',
    )
  })
})
