import { describe, expect, it, vi } from 'vitest'
import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { act, waitFor } from '@testing-library/react'
import { ApiError } from '../api/apiError'
import { renderHookWithQueryClient } from '../test/renderWithQueryClient'
import { combineLoadState, useQueryState } from './queryState'

function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (reason: unknown) => void
  const promise = new Promise<T>((res, rej) => {
    resolve = res
    reject = rej
  })
  return { promise, resolve, reject }
}

describe('useQueryState', () => {
  it('is loading (skeleton) only until the first fetch resolves', async () => {
    const { result } = renderHookWithQueryClient(() => {
      const query = useQuery({ queryKey: ['a'], queryFn: () => Promise.resolve(['x']) })
      return useQueryState(query)
    })

    expect(result.current).toMatchObject({ loading: true, loadError: null })
    await waitFor(() => expect(result.current.loading).toBe(false))
    expect(result.current.loadError).toBeNull()
  })

  it('reports a failed first fetch as loadError (not loading) and calls onError', async () => {
    const onError = vi.fn()
    const { result } = renderHookWithQueryClient(() => {
      const query = useQuery({
        queryKey: ['a'],
        queryFn: () => Promise.reject(new ApiError(0, 'Network Error')),
      })
      return useQueryState(query, onError)
    })

    await waitFor(() => expect(result.current.loadError).toBe('Network Error'))
    expect(result.current.loading).toBe(false)
    expect(onError).toHaveBeenCalledWith('Network Error')
  })

  it('reload after a failed first load shows loading again, then the data', async () => {
    let calls = 0
    const retry = deferred<number>()
    const { result } = renderHookWithQueryClient(() => {
      const query = useQuery({
        queryKey: ['a'],
        queryFn: () => (++calls === 1 ? Promise.reject(new ApiError(500, 'down')) : retry.promise),
      })
      return useQueryState(query)
    })
    await waitFor(() => expect(result.current.loadError).toBe('down'))

    act(() => result.current.reload())

    await waitFor(() => expect(result.current.loading).toBe(true))
    expect(result.current.loadError).toBeNull()
    retry.resolve(1)
    await waitFor(() => expect(result.current.loading).toBe(false))
    expect(result.current.loadError).toBeNull()
  })

  it('keeps the rows when a refetch fails, reporting only through onError', async () => {
    const onError = vi.fn()
    let calls = 0
    const { result } = renderHookWithQueryClient(() => {
      const query = useQuery({
        queryKey: ['a'],
        queryFn: () =>
          ++calls === 1 ? Promise.resolve(['row']) : Promise.reject(new ApiError(500, 'down')),
      })
      return { query, state: useQueryState(query, onError) }
    })
    await waitFor(() => expect(result.current.query.data).toEqual(['row']))

    await act(() => result.current.query.refetch())

    await waitFor(() => expect(onError).toHaveBeenCalledWith('down'))
    expect(result.current.query.data).toEqual(['row'])
    expect(result.current.state).toMatchObject({ loading: false, loadError: null })
  })

  it('does not go back to loading while the next page loads with keepPreviousData', async () => {
    const next = deferred<string>()
    const { result, rerender } = (() => {
      let page = 0
      const hook = renderHookWithQueryClient(() => {
        const query = useQuery({
          queryKey: ['paged', page],
          queryFn: () => (page === 0 ? Promise.resolve('first') : next.promise),
          placeholderData: keepPreviousData,
        })
        return { query, state: useQueryState(query) }
      })
      return {
        result: hook.result,
        rerender: (p: number) => {
          page = p
          hook.rerender()
        },
      }
    })()
    await waitFor(() => expect(result.current.query.data).toBe('first'))

    rerender(1)

    expect(result.current.query.data).toBe('first')
    expect(result.current.query.isPlaceholderData).toBe(true)
    expect(result.current.state.loading).toBe(false)
    next.resolve('second')
    await waitFor(() => expect(result.current.query.data).toBe('second'))
  })
})

describe('combineLoadState', () => {
  const loaded = { loading: false, loadError: null, reload: vi.fn() }
  const loading = { loading: true, loadError: null, reload: vi.fn() }
  const failed = (message: string) => ({ loading: false, loadError: message, reload: vi.fn() })

  it('is loading while any source is still loading', () => {
    expect(combineLoadState(loaded, loading)).toMatchObject({ loading: true, loadError: null })
  })

  it('is not loading once every source has loaded', () => {
    expect(combineLoadState(loaded, loaded)).toMatchObject({ loading: false, loadError: null })
  })

  it('reports the first failure right away, even while another source is still loading', () => {
    expect(combineLoadState(loading, failed('down'), failed('later'))).toMatchObject({
      loading: false,
      loadError: 'down',
    })
  })

  it('reload retries only the sources that failed', () => {
    const a = failed('a down')
    const b = { ...loaded, reload: vi.fn() }
    const c = failed('c down')

    combineLoadState(a, b, c).reload()

    expect(a.reload).toHaveBeenCalledTimes(1)
    expect(b.reload).not.toHaveBeenCalled()
    expect(c.reload).toHaveBeenCalledTimes(1)
  })
})
