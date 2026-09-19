import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { act, renderHook } from '@testing-library/react'
import { useDelayedFlag } from './useDelayedFlag'

describe('useDelayedFlag', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('stays false until the delay has elapsed', () => {
    const { result } = renderHook(() => useDelayedFlag(true, 150))

    expect(result.current).toBe(false)
    act(() => vi.advanceTimersByTime(149))
    expect(result.current).toBe(false)
    act(() => vi.advanceTimersByTime(1))
    expect(result.current).toBe(true)
  })

  it('never turns true if it goes inactive before the delay elapses', () => {
    const { result, rerender } = renderHook(({ active }) => useDelayedFlag(active, 150), {
      initialProps: { active: true },
    })

    act(() => vi.advanceTimersByTime(100))
    rerender({ active: false })
    act(() => vi.advanceTimersByTime(500))

    expect(result.current).toBe(false)
  })

  it('turns false immediately when it goes inactive after the delay elapsed', () => {
    const { result, rerender } = renderHook(({ active }) => useDelayedFlag(active, 150), {
      initialProps: { active: true },
    })
    act(() => vi.advanceTimersByTime(150))
    expect(result.current).toBe(true)

    rerender({ active: false })

    expect(result.current).toBe(false)
  })

  it('waits the full delay again when it re-activates', () => {
    const { result, rerender } = renderHook(({ active }) => useDelayedFlag(active, 150), {
      initialProps: { active: true },
    })
    act(() => vi.advanceTimersByTime(150))
    rerender({ active: false })
    rerender({ active: true })

    expect(result.current).toBe(false)
    act(() => vi.advanceTimersByTime(150))
    expect(result.current).toBe(true)
  })
})
