import { describe, expect, it } from 'vitest'
import { waitFor } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { server } from '../mocks/server'
import {
  seedCategorySpend,
  seedGroceriesTransaction,
  seedTransactions,
  transactionClosedAccountConflictHandler,
} from '../mocks/handlers/transactions'
import { renderHookWithQueryClient } from '../test/renderWithQueryClient'
import { CLOSED_ACCOUNT_MESSAGE } from './transactions'
import {
  useCreateTransaction,
  useDeleteTransaction,
  useSpendByCategory,
  useTransactions,
} from './transactionsQueries'

const request = {
  date: '2026-02-01',
  amount: 10,
  categoryId: 'cat-1',
  accountId: 'acct-1',
  paymentMethodId: 'pm-1',
  description: 'Coffee',
}

describe('transactions hooks', () => {
  it('loads a filtered page and the spend per category', async () => {
    const { result } = renderHookWithQueryClient(() => ({
      list: useTransactions({ categoryId: 'cat-1' }, 0, 20),
      spend: useSpendByCategory('2026-01'),
    }))

    await waitFor(() =>
      expect(result.current.list.data?.content).toEqual([seedGroceriesTransaction]),
    )
    await waitFor(() => expect(result.current.spend.data).toEqual(seedCategorySpend))
  })

  it('refetches the list after a create and a delete', async () => {
    const { result } = renderHookWithQueryClient(() => ({
      list: useTransactions(),
      create: useCreateTransaction(),
      remove: useDeleteTransaction(),
    }))
    await waitFor(() =>
      expect(result.current.list.data?.content).toHaveLength(seedTransactions.length),
    )

    await result.current.create.mutateAsync(request)
    await waitFor(() =>
      expect(result.current.list.data?.content).toHaveLength(seedTransactions.length + 1),
    )

    await result.current.remove.mutateAsync(seedGroceriesTransaction.id)
    await waitFor(() =>
      expect(result.current.list.data?.content.map((t) => t.id)).not.toContain(
        seedGroceriesTransaction.id,
      ),
    )
  })

  it('does not refetch after a rejected create, and keeps the closed-account message', async () => {
    let lists = 0
    server.use(
      transactionClosedAccountConflictHandler,
      http.get('/api/transactions', () => {
        lists += 1
        return HttpResponse.json({
          content: [],
          page: { size: 20, number: 0, totalElements: 0, totalPages: 1 },
        })
      }),
    )
    const { result } = renderHookWithQueryClient(() => ({
      list: useTransactions(),
      create: useCreateTransaction(),
    }))
    await waitFor(() => expect(result.current.list.data).toBeDefined())

    await expect(result.current.create.mutateAsync(request)).rejects.toThrow(CLOSED_ACCOUNT_MESSAGE)

    expect(lists).toBe(1)
  })
})
