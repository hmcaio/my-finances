import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { act, renderHook } from '@testing-library/react'
import { useDebouncedValue } from './useDebouncedValue'

describe('useDebouncedValue', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('starts with the initial value', () => {
    const { result } = renderHook(() => useDebouncedValue('a', 300))

    expect(result.current).toBe('a')
  })

  it('publishes a new value only after the delay', () => {
    const { result, rerender } = renderHook(({ value }) => useDebouncedValue(value, 300), {
      initialProps: { value: 'a' },
    })

    rerender({ value: 'b' })
    act(() => vi.advanceTimersByTime(299))
    expect(result.current).toBe('a')
    act(() => vi.advanceTimersByTime(1))
    expect(result.current).toBe('b')
  })

  it('restarts the wait on every change, so only the last value in a burst is published', () => {
    const { result, rerender } = renderHook(({ value }) => useDebouncedValue(value, 300), {
      initialProps: { value: '' },
    })

    rerender({ value: 'c' })
    act(() => vi.advanceTimersByTime(200))
    rerender({ value: 'ca' })
    act(() => vi.advanceTimersByTime(200))
    rerender({ value: 'caf' })
    expect(result.current).toBe('')
    act(() => vi.advanceTimersByTime(300))

    expect(result.current).toBe('caf')
  })
})
