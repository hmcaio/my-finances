import { describe, expect, it } from 'vitest'
import { act, renderHook, waitFor } from '@testing-library/react'
import { usePagedData } from './usePagedData'

const PAGE = {
  content: [{ id: 1 }, { id: 2 }],
  page: { size: 10, number: 0, totalElements: 12, totalPages: 2 },
}

describe('usePagedData', () => {
  it('splits the envelope into rows and page info', async () => {
    const { result } = renderHook(() => usePagedData(() => Promise.resolve(PAGE), []))

    expect(result.current).toMatchObject({ items: null, pageInfo: null, loading: true })
    await waitFor(() => expect(result.current.items).toEqual(PAGE.content))
    expect(result.current.pageInfo).toEqual({ number: 0, totalPages: 2 })
    expect(result.current.loading).toBe(false)
  })

  it('setItems edits the rows and keeps the page info', async () => {
    const { result } = renderHook(() => usePagedData(() => Promise.resolve(PAGE), []))
    await waitFor(() => expect(result.current.items).not.toBeNull())

    act(() => result.current.setItems((prev) => [{ id: 0 }, ...prev]))

    expect(result.current.items).toEqual([{ id: 0 }, { id: 1 }, { id: 2 }])
    expect(result.current.pageInfo).toEqual({ number: 0, totalPages: 2 })
  })

  it('setItems is a no-op while nothing is loaded', () => {
    const { result } = renderHook(() => usePagedData(() => new Promise<typeof PAGE>(() => {}), []))

    act(() => result.current.setItems((prev) => [...prev, { id: 9 }]))

    expect(result.current.items).toBeNull()
  })
})
