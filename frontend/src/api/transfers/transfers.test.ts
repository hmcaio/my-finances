import { describe, expect, it } from 'vitest'
import { http, HttpResponse } from 'msw'
import { server } from '../../mocks/server'
import {
  seedBitcoinBuyTransfer,
  seedBitcoinSellTransfer,
  seedCreditCardPaymentTransfer,
  seedTransfers,
  transferClosedAccountConflictHandler,
} from '../../mocks/handlers/transfers'
import { ApiError } from '../core/apiError'
import {
  TRANSFER_CONFLICT_MESSAGE,
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
    expect((error as ApiError).message).toBe(TRANSFER_CONFLICT_MESSAGE)
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

  it('getTransfers filters by investmentProductId and returns the owning confirmations', async () => {
    const result = await getTransfers({ investmentProductId: 'iprod-btc' })

    // Most recent first: the sell, then the buy, each with its tradeConfirmation lines/taxes.
    expect(result.content).toEqual([seedBitcoinSellTransfer, seedBitcoinBuyTransfer])
    expect(result.content[1]).toMatchObject({
      taxes: 5,
      tradeConfirmation: { lines: [{ productId: 'iprod-btc', quantity: 0.01, unitPrice: 100000 }] },
    })
  })

  it('a plain transfer has null taxes and no trade confirmation', async () => {
    const transfer = await getTransfer(seedCreditCardPaymentTransfer.id)

    expect(transfer).toMatchObject({ taxes: null, tradeConfirmation: null })
  })

  it('createTransfer sends a trade confirmation and returns the derived amount/direction', async () => {
    let sentBody: unknown = null
    server.use(
      http.post('/api/transfers', async ({ request }) => {
        sentBody = await request.json()
        return HttpResponse.json(seedBitcoinBuyTransfer, { status: 201 })
      }),
    )
    const request = {
      date: '2026-08-10',
      description: 'Buy Bitcoin',
      cashAccountId: 'acct-1',
      investmentAccountId: 'acct-inv',
      tradeConfirmation: {
        taxes: 5,
        lines: [
          { productId: 'iprod-btc', side: 'BUY' as const, quantity: 0.01, unitPrice: 100000 },
        ],
      },
    }

    const created = await createTransfer(request)

    expect(sentBody).toEqual(request)
    expect(created).toMatchObject({
      tradeConfirmation: { lines: [{ productId: 'iprod-btc', quantity: 0.01 }] },
    })
  })

  it('editTransfer sends the trade confirmation fields', async () => {
    const updated = await editTransfer(seedBitcoinBuyTransfer.id, {
      date: '2026-08-10',
      description: 'Buy Bitcoin',
      cashAccountId: 'acct-1',
      investmentAccountId: 'acct-inv',
      tradeConfirmation: {
        taxes: 5,
        lines: [{ productId: 'iprod-btc', side: 'BUY', quantity: 0.01, unitPrice: 100500 }],
      },
    })

    expect(updated.tradeConfirmation?.lines[0]).toMatchObject({
      productId: 'iprod-btc',
      unitPrice: 100500,
    })
  })

  it('deleteTransfer resolves on success', async () => {
    await expect(deleteTransfer(seedCreditCardPaymentTransfer.id)).resolves.toBeUndefined()
  })
})
