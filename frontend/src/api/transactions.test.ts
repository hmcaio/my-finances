import { describe, expect, it } from 'vitest'
import { server } from '../mocks/server'
import {
  seedTransactions,
  transactionClosedAccountConflictHandler,
} from '../mocks/handlers/transactions'
import { ApiError } from './apiError'
import {
  createTransaction,
  deleteTransaction,
  editTransaction,
  getTransaction,
  getTransactions,
} from './transactions'

describe('transactions API client', () => {
  it('getTransactions returns a page envelope with content and page metadata', async () => {
    const result = await getTransactions()

    // Most recent first, matching the real backend's default sort (date desc).
    expect(result.content).toEqual([...seedTransactions].sort((a, b) => (a.date < b.date ? 1 : -1)))
    expect(result.page).toMatchObject({ totalElements: seedTransactions.length })
  })

  it('getTransactions applies filter params', async () => {
    const incomeTransaction = seedTransactions.find((t) => t.type === 'INCOME')!

    const result = await getTransactions({ categoryId: incomeTransaction.categoryId })

    expect(result.content).toEqual([incomeTransaction])
  })

  it('getTransaction returns a single transaction by id', async () => {
    const transaction = await getTransaction(seedTransactions[0].id)

    expect(transaction).toEqual(seedTransactions[0])
  })

  it('getTransaction of an unknown id maps a 404 to an ApiError', async () => {
    const error: unknown = await getTransaction('txn-missing').catch((err: unknown) => err)

    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).status).toBe(404)
  })

  it('createTransaction posts the new transaction and returns the created one', async () => {
    const created = await createTransaction({
      date: '2026-04-01',
      amount: 25,
      categoryId: 'cat-1',
      accountId: 'acct-1',
      paymentMethodId: 'pm-1',
      note: 'Test',
    })

    expect(created).toMatchObject({ date: '2026-04-01', amount: 25, type: 'EXPENSE' })
    expect(created.id).toBeTruthy()
  })

  it('createTransaction maps a 409 (closed account) to an ApiError with a friendly message', async () => {
    server.use(transactionClosedAccountConflictHandler)

    const error: unknown = await createTransaction({
      date: '2026-04-01',
      amount: 25,
      categoryId: 'cat-1',
      accountId: 'acct-2',
      paymentMethodId: 'pm-1',
    }).catch((err: unknown) => err)

    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).status).toBe(409)
    expect((error as ApiError).message).toMatch(/cannot accept new transactions/)
  })

  it('editTransaction patches every field and returns the updated transaction', async () => {
    const updated = await editTransaction(seedTransactions[0].id, {
      date: '2026-05-01',
      amount: 99,
      categoryId: 'cat-2',
      accountId: 'acct-1',
      paymentMethodId: 'pm-2',
      note: 'Edited',
    })

    expect(updated).toMatchObject({
      id: seedTransactions[0].id,
      date: '2026-05-01',
      amount: 99,
      note: 'Edited',
    })
  })

  it('deleteTransaction resolves on success', async () => {
    await expect(deleteTransaction(seedTransactions[0].id)).resolves.toBeUndefined()
  })
})
