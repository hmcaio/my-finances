import { afterEach, describe, expect, it, vi } from 'vitest'
import { act, renderHook, waitFor } from '@testing-library/react'
import i18n from '../../i18n/i18n'
import { LocaleProvider } from './LocaleProvider'
import { useLocale } from './useLocale'

const STORAGE_KEY = 'my-finances:locale'

function renderLocale() {
  return renderHook(() => useLocale(), { wrapper: LocaleProvider })
}

describe('useLocale', () => {
  afterEach(() => {
    localStorage.clear()
    vi.unstubAllGlobals()
  })

  it('defaults to pt-BR when the browser language starts with pt', () => {
    vi.stubGlobal('navigator', { ...navigator, language: 'pt-PT' })

    const { result } = renderLocale()

    expect(result.current.locale).toBe('pt-BR')
  })

  it('defaults to en when the browser language does not start with pt', () => {
    vi.stubGlobal('navigator', { ...navigator, language: 'en-US' })

    const { result } = renderLocale()

    expect(result.current.locale).toBe('en')
  })

  it('loads a persisted locale from localStorage, ignoring the browser language', () => {
    vi.stubGlobal('navigator', { ...navigator, language: 'en-US' })
    localStorage.setItem(STORAGE_KEY, 'pt-BR')

    const { result } = renderLocale()

    expect(result.current.locale).toBe('pt-BR')
  })

  it('toggleLocale flips the locale, persists it, and drives i18next', async () => {
    vi.stubGlobal('navigator', { ...navigator, language: 'en-US' })
    const { result } = renderLocale()
    expect(result.current.locale).toBe('en')

    act(() => result.current.toggleLocale())

    expect(result.current.locale).toBe('pt-BR')
    expect(localStorage.getItem(STORAGE_KEY)).toBe('pt-BR')
    await waitFor(() => expect(i18n.language).toBe('pt-BR'))
    expect(document.documentElement.lang).toBe('pt-BR')

    act(() => result.current.toggleLocale())

    expect(result.current.locale).toBe('en')
    expect(localStorage.getItem(STORAGE_KEY)).toBe('en')
    await waitFor(() => expect(i18n.language).toBe('en'))
    expect(document.documentElement.lang).toBe('en')
  })

  it('throws when used outside a LocaleProvider', () => {
    expect(() => renderHook(() => useLocale())).toThrow(
      'useLocale must be used within a LocaleProvider',
    )
  })
})
