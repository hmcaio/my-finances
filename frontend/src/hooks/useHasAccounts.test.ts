import { describe, expect, it } from 'vitest'
import { act, renderHook, waitFor } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { server } from '../mocks/server'
import { useHasAccounts } from './useHasAccounts'

describe('useHasAccounts', () => {
  it('is unresolved (null) while loading, then true when accounts exist', async () => {
    const { result } = renderHook(() => useHasAccounts())

    expect(result.current.hasAccounts).toBeNull()
    expect(result.current.loading).toBe(true)
    await waitFor(() => expect(result.current.hasAccounts).toBe(true))
  })

  it('checks including closed accounts', async () => {
    let includeClosed: string | null = null
    server.use(
      http.get('/api/accounts', ({ request }) => {
        includeClosed = new URL(request.url).searchParams.get('includeClosed')
        return HttpResponse.json([])
      }),
    )
    renderHook(() => useHasAccounts())

    await waitFor(() => expect(includeClosed).toBe('true'))
  })

  it('is false when there are no accounts, and markHasAccounts flips it to true', async () => {
    server.use(http.get('/api/accounts', () => HttpResponse.json([])))
    const { result } = renderHook(() => useHasAccounts())

    await waitFor(() => expect(result.current.hasAccounts).toBe(false))
    act(() => result.current.markHasAccounts())

    expect(result.current.hasAccounts).toBe(true)
  })

  it('reports a failure as loadError, never as "no accounts"', async () => {
    server.use(http.get('/api/accounts', () => new HttpResponse(null, { status: 500 })))
    const { result } = renderHook(() => useHasAccounts())

    await waitFor(() => expect(result.current.loadError).not.toBeNull())
    expect(result.current.hasAccounts).toBeNull()
  })
})
