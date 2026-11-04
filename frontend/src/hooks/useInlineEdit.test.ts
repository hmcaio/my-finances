import { describe, expect, it } from 'vitest'
import { act, renderHook } from '@testing-library/react'
import { useInlineEdit } from './useInlineEdit'

const EMPTY = { name: '', institutionId: undefined as string | undefined }

describe('useInlineEdit', () => {
  it('starts with no row being edited and the empty draft', () => {
    const { result } = renderHook(() => useInlineEdit(EMPTY))

    expect(result.current.editingId).toBeNull()
    expect(result.current.draft).toEqual(EMPTY)
  })

  it('start selects the row and loads its draft', () => {
    const { result } = renderHook(() => useInlineEdit(EMPTY))

    act(() => result.current.start('a1', { name: 'Checking', institutionId: 'i1' }))

    expect(result.current.editingId).toBe('a1')
    expect(result.current.draft).toEqual({ name: 'Checking', institutionId: 'i1' })
  })

  it('setDraft edits the draft while the row stays selected', () => {
    const { result } = renderHook(() => useInlineEdit(EMPTY))

    act(() => result.current.start('a1', { name: 'Checking', institutionId: 'i1' }))
    act(() => result.current.setDraft({ name: 'Savings', institutionId: 'i1' }))

    expect(result.current.editingId).toBe('a1')
    expect(result.current.draft.name).toBe('Savings')
  })

  it('starting another row replaces the previous draft', () => {
    const { result } = renderHook(() => useInlineEdit(EMPTY))

    act(() => result.current.start('a1', { name: 'Checking', institutionId: 'i1' }))
    act(() => result.current.start('a2', { name: 'Wallet', institutionId: 'i2' }))

    expect(result.current.editingId).toBe('a2')
    expect(result.current.draft).toEqual({ name: 'Wallet', institutionId: 'i2' })
  })

  it('cancel clears the selection and resets the draft to the empty one', () => {
    const { result } = renderHook(() => useInlineEdit(EMPTY))

    act(() => result.current.start('a1', { name: 'Checking', institutionId: 'i1' }))
    act(() => result.current.cancel())

    expect(result.current.editingId).toBeNull()
    expect(result.current.draft).toEqual(EMPTY)
  })
})
