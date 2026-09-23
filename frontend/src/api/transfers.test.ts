import { describe, expect, it } from 'vitest'
import { server } from '../mocks/server'
import {
  seedCreditCardPaymentTransfer,
  seedTransfers,
  transferClosedAccountConflictHandler,
} from '../mocks/handlers/transfers'
import { ApiError } from './apiError'
import {
  CLOSED_ACCOUNT_MESSAGE,
  createTransfer,
  deleteTransfer,
  editTransfer,
  getTransfer,
  getTransfers,
} from './transfers'

describe('transfers API client', () => {
  it('getTransfers returns a page envelope with content and page metadata', async () => {
    const result = await getTransfers()

    // Most recent first, matching the real backend's default sort (date desc).
    expect(result.content).toEqual([...seedTransfers].sort((a, b) => (a.date < b.date ? 1 : -1)))
    expect(result.page).toMatchObject({ totalElements: seedTransfers.length })
  })

  it('getTransfers applies the accountId filter, matching either side', async () => {
    const result = await getTransfers({ accountId: 'acct-1' })

    // Both seed transfers touch acct-1 (one as source, one as destination), most recent first.
    expect(result.content).toEqual([...seedTransfers].sort((a, b) => (a.date < b.date ? 1 : -1)))
  })

  it('getTransfer returns a single transfer by id', async () => {
    const transfer = await getTransfer(seedCreditCardPaymentTransfer.id)

    expect(transfer).toEqual(seedCreditCardPaymentTransfer)
  })

  it('getTransfer of an unknown id maps a 404 to an ApiError', async () => {
    const error: unknown = await getTransfer('trf-missing').catch((err: unknown) => err)

    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).status).toBe(404)
  })

  it('createTransfer posts the new transfer and returns the created one', async () => {
    const created = await createTransfer({
      date: '2026-04-01',
      fromAccountId: 'acct-1',
      toAccountId: 'acct-3',
      amount: 25,
      description: 'Test',
    })

    expect(created).toMatchObject({ date: '2026-04-01', amount: 25 })
    expect(created.id).toBeTruthy()
  })

  it('createTransfer maps a 409 (closed account) to an ApiError with a friendly message', async () => {
    server.use(transferClosedAccountConflictHandler)

    const error: unknown = await createTransfer({
      date: '2026-04-01',
      fromAccountId: 'acct-1',
      toAccountId: 'acct-2',
      amount: 25,
      description: 'Test',
    }).catch((err: unknown) => err)

    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).status).toBe(409)
    expect((error as ApiError).message).toBe(CLOSED_ACCOUNT_MESSAGE)
  })

  it('editTransfer patches every field and returns the updated transfer', async () => {
    const updated = await editTransfer(seedCreditCardPaymentTransfer.id, {
      date: '2026-05-01',
      fromAccountId: 'acct-3',
      toAccountId: 'acct-1',
      amount: 99,
      description: 'Edited',
    })

    expect(updated).toMatchObject({
      id: seedCreditCardPaymentTransfer.id,
      date: '2026-05-01',
      amount: 99,
      description: 'Edited',
    })
  })

  it('deleteTransfer resolves on success', async () => {
    await expect(deleteTransfer(seedCreditCardPaymentTransfer.id)).resolves.toBeUndefined()
  })
})
