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

  it('asks the count endpoint, never the account list that computes every balance (issue #189)', async () => {
    let listRequested = false
    let countRequested = false
    server.use(
      http.get('/api/accounts', () => {
        listRequested = true
        return HttpResponse.json([])
      }),
      http.get('/api/accounts/count', () => {
        countRequested = true
        return HttpResponse.json({ count: 0 })
      }),
    )
    renderHookWithQueryClient(() => useHasAccounts())

    await waitFor(() => expect(countRequested).toBe(true))
    expect(listRequested).toBe(false)
  })

  it('is false when there are no accounts, and flips to true once the first account is created', async () => {
    let created = false
    server.use(
      http.get('/api/accounts/count', () => HttpResponse.json({ count: created ? 1 : 0 })),
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
    server.use(http.get('/api/accounts/count', () => new HttpResponse(null, { status: 500 })))
    const { result } = renderHookWithQueryClient(() => useHasAccounts())

    await waitFor(() => expect(result.current.loadError).not.toBeNull())
    expect(result.current.hasAccounts).toBeNull()
  })
})
