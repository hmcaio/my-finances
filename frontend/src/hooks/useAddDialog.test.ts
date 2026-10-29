import { describe, expect, it } from 'vitest'
import { act, renderHook } from '@testing-library/react'
import { useAddDialog } from './useAddDialog'

describe('useAddDialog', () => {
  it('starts closed with the empty draft', () => {
    const { result } = renderHook(() => useAddDialog({ name: '', type: 'EXPENSE' }))

    expect(result.current.isOpen).toBe(false)
    expect(result.current.draft).toEqual({ name: '', type: 'EXPENSE' })
  })

  it('keeps the draft while open and resets it on close', () => {
    const { result } = renderHook(() => useAddDialog({ name: '', type: 'EXPENSE' }))

    act(() => result.current.show())
    act(() => result.current.setDraft({ name: 'Rent', type: 'INCOME' }))
    expect(result.current.isOpen).toBe(true)
    expect(result.current.draft).toEqual({ name: 'Rent', type: 'INCOME' })

    act(() => result.current.close())
    expect(result.current.isOpen).toBe(false)
    expect(result.current.draft).toEqual({ name: '', type: 'EXPENSE' })

    act(() => result.current.show())
    expect(result.current.draft).toEqual({ name: '', type: 'EXPENSE' })
  })
})
