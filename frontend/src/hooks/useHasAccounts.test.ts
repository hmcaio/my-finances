import { describe, expect, it } from 'vitest'
import { renderHook } from '@testing-library/react'
import { useHasAccounts } from './useHasAccounts'

describe('useHasAccounts', () => {
  it('reports true (stub pending the real F003 accounts check)', () => {
    const { result } = renderHook(() => useHasAccounts())

    expect(result.current).toBe(true)
  })
})
