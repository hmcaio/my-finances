import type { PropsWithChildren } from 'react'
import { act, renderHook, waitFor } from '@testing-library/react'
import { MemoryRouter, useLocation, useNavigate, useNavigationType } from 'react-router-dom'
import type { InitialEntry } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import { useConsumeNavigationState } from './useConsumeNavigationState'

interface Preset {
  presetCategoryId?: string
}

const readPreset = (state: unknown) => (state as Preset | null)?.presetCategoryId

function setup(entry: InitialEntry) {
  const wrapper = ({ children }: PropsWithChildren) => (
    <MemoryRouter initialEntries={[entry]}>{children}</MemoryRouter>
  )
  return renderHook(
    () => ({
      value: useConsumeNavigationState(readPreset),
      location: useLocation(),
      navigationType: useNavigationType(),
      navigate: useNavigate(),
    }),
    { wrapper },
  )
}

describe('useConsumeNavigationState', () => {
  it('returns the value read from the navigation state on the first render', () => {
    const { result } = setup({ pathname: '/ledger', state: { presetCategoryId: 'cat-fuel' } })

    expect(result.current.value).toBe('cat-fuel')
  })

  it('returns undefined and leaves the history alone when there is nothing to consume', () => {
    const { result } = setup({ pathname: '/ledger', state: { other: 1 } })

    expect(result.current.value).toBeUndefined()
    expect(result.current.navigationType).toBe('POP')
    expect(result.current.location.state).toEqual({ other: 1 })
  })

  it('clears the state in place (replace), keeping the path and search', async () => {
    const { result } = setup({
      pathname: '/ledger',
      search: '?month=2026-01',
      state: { presetCategoryId: 'cat-fuel' },
    })

    await waitFor(() => expect(result.current.location.state).toBeNull())
    expect(result.current.navigationType).toBe('REPLACE')
    expect(result.current.location.pathname).toBe('/ledger')
    expect(result.current.location.search).toBe('?month=2026-01')
  })

  it('keeps returning the consumed value after the state is cleared', async () => {
    const { result } = setup({ pathname: '/ledger', state: { presetCategoryId: 'cat-fuel' } })

    await waitFor(() => expect(result.current.location.state).toBeNull())

    expect(result.current.value).toBe('cat-fuel')
  })

  it('clears only once: a later navigation keeps its own state', async () => {
    const { result } = setup({ pathname: '/ledger', state: { presetCategoryId: 'cat-fuel' } })
    await waitFor(() => expect(result.current.location.state).toBeNull())

    act(() => {
      result.current.navigate('/ledger?month=2026-02', { state: { presetCategoryId: 'later' } })
    })

    expect(result.current.navigationType).toBe('PUSH')
    expect(result.current.location.state).toEqual({ presetCategoryId: 'later' })
    expect(result.current.value).toBe('cat-fuel')
  })
})
