import type { PropsWithChildren } from 'react'
import { act, renderHook, waitFor } from '@testing-library/react'
import {
  MemoryRouter,
  useLocation,
  useNavigate,
  useNavigationType,
  useSearchParams,
} from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import { useFocusParam } from './useFocusParam'

function setup(url: string) {
  const onFocus = vi.fn<(id: string) => void>()
  const wrapper = ({ children }: PropsWithChildren) => (
    <MemoryRouter initialEntries={[url]}>{children}</MemoryRouter>
  )
  const hook = renderHook(
    () => {
      const [searchParams] = useSearchParams()
      useFocusParam(searchParams.get('focus') ?? '', onFocus)
      return {
        location: useLocation(),
        navigationType: useNavigationType(),
        navigate: useNavigate(),
      }
    },
    { wrapper },
  )
  return { ...hook, onFocus }
}

describe('useFocusParam', () => {
  it('hands the focused id to the callback once and drops only the focus key, in place', async () => {
    const { result, onFocus } = setup('/ledger?month=2026-01&type=transfer&focus=trf-1&q=abc')

    await waitFor(() => expect(result.current.location.search).not.toContain('focus'))

    expect(onFocus).toHaveBeenCalledExactlyOnceWith('trf-1')
    expect(result.current.location.search).toBe('?month=2026-01&type=transfer&q=abc')
    expect(result.current.navigationType).toBe('REPLACE')
  })

  it('does nothing without a focus param', () => {
    const { result, onFocus } = setup('/ledger?month=2026-01')

    expect(onFocus).not.toHaveBeenCalled()
    expect(result.current.navigationType).toBe('POP')
    expect(result.current.location.search).toBe('?month=2026-01')
  })

  it('handles the same link again once the key has been dropped', async () => {
    const { result, onFocus } = setup('/ledger?focus=trf-1')
    await waitFor(() => expect(result.current.location.search).toBe(''))

    act(() => {
      result.current.navigate('/ledger?focus=trf-1')
    })
    await waitFor(() => expect(result.current.location.search).toBe(''))

    expect(onFocus).toHaveBeenCalledTimes(2)
    expect(onFocus).toHaveBeenLastCalledWith('trf-1')
  })

  it('follows a different id arriving later', async () => {
    const { result, onFocus } = setup('/ledger?focus=trf-1')
    await waitFor(() => expect(result.current.location.search).toBe(''))

    act(() => {
      result.current.navigate('/ledger?focus=trf-2')
    })
    await waitFor(() => expect(result.current.location.search).toBe(''))

    expect(onFocus).toHaveBeenLastCalledWith('trf-2')
  })
})
