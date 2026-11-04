import { describe, expect, it, vi } from 'vitest'
import { act, renderHook } from '@testing-library/react'
import { ApiError } from '../api/core/apiError'
import { useAction } from './useAction'

function deferred() {
  let resolve!: () => void
  let reject!: (err: unknown) => void
  const promise = new Promise<void>((res, rej) => {
    resolve = res
    reject = rej
  })
  return { promise, resolve, reject }
}

describe('useAction', () => {
  it('starts idle', () => {
    const { result } = renderHook(() => useAction(vi.fn()))

    expect(result.current.pending).toBe(false)
  })

  it('clears the page error before running the call and resolves true on success', async () => {
    const order: string[] = []
    const setError = vi.fn((message: string | null) => order.push(`error:${message}`))
    const { result } = renderHook(() => useAction(setError))

    let ok: boolean | undefined
    await act(async () => {
      ok = await result.current.run(async () => {
        order.push('call')
      })
    })

    expect(ok).toBe(true)
    expect(order).toEqual(['error:null', 'call'])
  })

  it('reports a failure through the page error and resolves false instead of throwing', async () => {
    const setError = vi.fn()
    const { result } = renderHook(() => useAction(setError))

    let ok: boolean | undefined
    await act(async () => {
      ok = await result.current.run(async () => {
        throw new Error('boom')
      })
    })

    expect(ok).toBe(false)
    expect(setError).toHaveBeenLastCalledWith('boom')
  })

  it('applies a per-status override to an ApiError and not to other statuses', async () => {
    const setError = vi.fn()
    const { result } = renderHook(() => useAction(setError))

    await act(async () => {
      await result.current.run(
        async () => {
          throw new ApiError(404, 'raw 404 text')
        },
        { 404: 'Snapshot not found' },
      )
    })
    expect(setError).toHaveBeenLastCalledWith('Snapshot not found')

    await act(async () => {
      await result.current.run(
        async () => {
          throw new ApiError(409, 'conflict text')
        },
        { 404: 'Snapshot not found' },
      )
    })
    expect(setError).toHaveBeenLastCalledWith('conflict text')
  })

  it('is pending while the call is in flight and idle again after success', async () => {
    const call = deferred()
    const { result } = renderHook(() => useAction(vi.fn()))

    let running: Promise<boolean>
    act(() => {
      running = result.current.run(() => call.promise)
    })
    expect(result.current.pending).toBe(true)

    await act(async () => {
      call.resolve()
      await running
    })
    expect(result.current.pending).toBe(false)
  })

  it('is idle again after a failure', async () => {
    const call = deferred()
    const { result } = renderHook(() => useAction(vi.fn()))

    let running: Promise<boolean>
    act(() => {
      running = result.current.run(() => call.promise)
    })
    expect(result.current.pending).toBe(true)

    await act(async () => {
      call.reject(new Error('nope'))
      await running
    })
    expect(result.current.pending).toBe(false)
  })
})
