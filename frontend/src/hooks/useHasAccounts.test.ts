import { describe, expect, it } from 'vitest'
import { waitFor } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { server } from '../mocks/server'
import { useCreateAccount } from '../api/accounts/accountsQueries'
import { renderHookWithQueryClient } from '../test/renderWithQueryClient'
import { useHasAccounts } from './useHasAccounts'

describe('useHasAccounts', () => {
  it('is unresolved (null) while loading, then true when accounts exist', async () => {
    const { result } = renderHookWithQueryClient(() => useHasAccounts())

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
    renderHookWithQueryClient(() => useHasAccounts())

    await waitFor(() => expect(includeClosed).toBe('true'))
  })

  it('is false when there are no accounts, and flips to true once the first account is created', async () => {
    let created = false
    server.use(
      http.get('/api/accounts', () =>
        HttpResponse.json(
          created
            ? [
                {
                  id: 'acct-first',
                  name: 'Main',
                  institutionId: 'inst-1',
                  type: 'CHECKING',
                  openingBalance: 0,
                  openingBalanceDate: '2026-01-01',
                  closedDate: null,
                  closed: false,
                  balance: 0,
                },
              ]
            : [],
        ),
      ),
      http.post('/api/accounts', () => {
        created = true
        return HttpResponse.json({ id: 'acct-first' }, { status: 201 })
      }),
    )
    const { result } = renderHookWithQueryClient(() => ({
      gate: useHasAccounts(),
      create: useCreateAccount(),
    }))

    await waitFor(() => expect(result.current.gate.hasAccounts).toBe(false))
    await result.current.create.mutateAsync({
      name: 'Main',
      institutionId: 'inst-1',
      type: 'CHECKING',
      openingBalance: 0,
      openingBalanceDate: '2026-01-01',
    })

    await waitFor(() => expect(result.current.gate.hasAccounts).toBe(true))
  })

  it('reports a failure as loadError, never as "no accounts"', async () => {
    server.use(http.get('/api/accounts', () => new HttpResponse(null, { status: 500 })))
    const { result } = renderHookWithQueryClient(() => useHasAccounts())

    await waitFor(() => expect(result.current.loadError).not.toBeNull())
    expect(result.current.hasAccounts).toBeNull()
  })
})
