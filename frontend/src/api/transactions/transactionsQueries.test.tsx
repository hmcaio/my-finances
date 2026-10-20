import { describe, expect, it } from 'vitest'
import { waitFor } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { server } from '../../mocks/server'
import {
  seedCategorySpend,
  seedGroceriesTransaction,
  transactionClosedAccountConflictHandler,
} from '../../mocks/handlers/transactions'
import { renderHookWithQueryClient } from '../../test/renderWithQueryClient'
import { resolveErrorMessage } from '../../test/errorMessages'
import { useLedger } from '../ledger/ledgerQueries'
import {
  useCreateTransaction,
  useDeleteTransaction,
  useSpendByCategory,
  useTransaction,
} from './transactionsQueries'

const request = {
  date: '2026-01-15',
  amount: 10,
  categoryId: 'cat-1',
  accountId: 'acct-1',
  paymentMethodId: 'pm-1',
  description: 'Coffee',
}

const january = { dateFrom: '2026-01-01', dateTo: '2026-01-31', type: 'EXPENSE' as const }

describe('transactions hooks', () => {
  it('loads one transaction by id, and stays idle while the id is null', async () => {
    const { result } = renderHookWithQueryClient(() => ({
      loaded: useTransaction(seedGroceriesTransaction.id),
      idle: useTransaction(null),
    }))

    await waitFor(() => expect(result.current.loaded.data).toEqual(seedGroceriesTransaction))
    expect(result.current.idle.fetchStatus).toBe('idle')
    expect(result.current.idle.data).toBeUndefined()
  })

  it('loads the spend per category', async () => {
    const { result } = renderHookWithQueryClient(() => useSpendByCategory('2026-01'))

    await waitFor(() => expect(result.current.data).toEqual(seedCategorySpend))
  })

  it('refetches the ledger after a create and a delete', async () => {
    const { result } = renderHookWithQueryClient(() => ({
      list: useLedger(january),
      create: useCreateTransaction(),
      remove: useDeleteTransaction(),
    }))
    await waitFor(() => expect(result.current.list.data?.entries).toHaveLength(1))

    await result.current.create.mutateAsync(request)
    await waitFor(() => expect(result.current.list.data?.entries).toHaveLength(2))

    await result.current.remove.mutateAsync(seedGroceriesTransaction.id)
    await waitFor(() =>
      expect(result.current.list.data?.entries.map((e) => e.id)).not.toContain(
        seedGroceriesTransaction.id,
      ),
    )
  })

  it('does not refetch after a rejected create, and keeps the closed-account message', async () => {
    let lists = 0
    server.use(
      transactionClosedAccountConflictHandler,
      http.get('/api/ledger', () => {
        lists += 1
        return HttpResponse.json({
          entries: [],
          summary: { income: 0, expense: 0, net: 0 },
          truncated: false,
        })
      }),
    )
    const { result } = renderHookWithQueryClient(() => ({
      list: useLedger(january),
      create: useCreateTransaction(),
    }))
    await waitFor(() => expect(result.current.list.data).toBeDefined())

    await expect(result.current.create.mutateAsync(request)).rejects.toThrow(
      resolveErrorMessage('urn:my-finances:error:account-closed'),
    )

    expect(lists).toBe(1)
  })
})
