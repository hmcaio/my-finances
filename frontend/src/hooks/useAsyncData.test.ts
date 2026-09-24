import { describe, expect, it, vi } from 'vitest'
import { act, renderHook, waitFor } from '@testing-library/react'
import { ApiError } from '../api/apiError'
import { useAsyncData } from './useAsyncData'

function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (reason: unknown) => void
  const promise = new Promise<T>((res, rej) => {
    resolve = res
    reject = rej
  })
  return { promise, resolve, reject }
}

describe('useAsyncData', () => {
  it('is loading until the first fetch resolves, then exposes the data', async () => {
    const { result } = renderHook(() => useAsyncData(() => Promise.resolve(['a']), []))

    expect(result.current).toMatchObject({ data: null, loading: true, loadError: null })
    await waitFor(() => expect(result.current.data).toEqual(['a']))
    expect(result.current).toMatchObject({ loading: false, loadError: null })
  })

  it('reports a failed first fetch as loadError (not loading) and calls onError', async () => {
    const onError = vi.fn()
    const { result } = renderHook(() =>
      useAsyncData(() => Promise.reject(new ApiError(0, 'Network Error')), [], { onError }),
    )

    await waitFor(() => expect(result.current.loadError).toBe('Network Error'))
    expect(result.current).toMatchObject({ data: null, loading: false })
    expect(onError).toHaveBeenCalledWith('Network Error')
  })

  it('uses a custom errorMessage when given', async () => {
    const { result } = renderHook(() =>
      useAsyncData(() => Promise.reject(new ApiError(404, 'x')), [], {
        errorMessage: () => 'Account not found.',
      }),
    )

    await waitFor(() => expect(result.current.loadError).toBe('Account not found.'))
  })

  it('keeps stale data and hides loadError when a refetch fails over existing data', async () => {
    const onError = vi.fn()
    const fetcher = vi
      .fn<(id: number) => Promise<string>>()
      .mockResolvedValueOnce('first')
      .mockRejectedValueOnce(new Error('later failure'))
    const { result, rerender } = renderHook(
      ({ id }) => useAsyncData(() => fetcher(id), [id], { onError }),
      { initialProps: { id: 1 } },
    )
    await waitFor(() => expect(result.current.data).toBe('first'))

    rerender({ id: 2 })
    await waitFor(() => expect(onError).toHaveBeenCalledWith('later failure'))

    expect(result.current).toMatchObject({ data: 'first', loading: false, loadError: null })
  })

  it('refetches when deps change and ignores the superseded response', async () => {
    const slow = deferred<string>()
    const fast = deferred<string>()
    const { result, rerender } = renderHook(
      ({ id }) => useAsyncData(() => (id === 1 ? slow.promise : fast.promise), [id]),
      { initialProps: { id: 1 } },
    )

    rerender({ id: 2 })
    await act(async () => fast.resolve('second'))
    await act(async () => slow.resolve('first'))

    expect(result.current.data).toBe('second')
  })

  it('reload refetches, clears loadError, and is loading again until the retry resolves', async () => {
    const retry = deferred<string>()
    const fetcher = vi
      .fn<() => Promise<string>>()
      .mockRejectedValueOnce(new Error('down'))
      .mockReturnValueOnce(retry.promise)
    const { result } = renderHook(() => useAsyncData(fetcher, []))
    await waitFor(() => expect(result.current.loadError).toBe('down'))

    act(() => result.current.reload())

    expect(result.current).toMatchObject({ loading: true, loadError: null })
    await act(async () => retry.resolve('back'))
    expect(result.current).toMatchObject({ data: 'back', loading: false, loadError: null })
  })

  it('setData applies local edits', async () => {
    const { result } = renderHook(() => useAsyncData(() => Promise.resolve(['a']), []))
    await waitFor(() => expect(result.current.data).toEqual(['a']))

    act(() => result.current.setData((prev) => [...(prev ?? []), 'b']))

    expect(result.current.data).toEqual(['a', 'b'])
  })
})
