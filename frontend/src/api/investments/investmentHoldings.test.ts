import { describe, expect, it } from 'vitest'
import { server } from '../../mocks/server'
import { seedInvestmentAccount } from '../../mocks/handlers/accounts'
import {
  investmentHoldingCloseConflictHandler,
  investmentHoldingCreateConflictHandler,
  investmentHoldingDeleteConflictHandler,
  seedInvestmentHoldings,
} from '../../mocks/handlers/investmentHoldings'
import { ApiError } from '../core/apiError'
import {
  CLOSE_CONFLICT_MESSAGE,
  DELETE_CONFLICT_MESSAGE,
  SAVE_CONFLICT_MESSAGE,
  closeInvestmentHolding,
  createInvestmentHolding,
  deleteInvestmentHolding,
  editInvestmentHoldingNotes,
  getInvestmentHolding,
  getInvestmentHoldingsByAccount,
  getInvestmentHoldingsByProduct,
} from './investmentHoldings'

describe('investment holdings API client', () => {
  it('getInvestmentHoldingsByProduct returns every holding of a product', async () => {
    await expect(getInvestmentHoldingsByProduct('iprod-btc')).resolves.toEqual([
      seedInvestmentHoldings.find((h) => h.id === 'iholding-btc'),
    ])
  })

  it('getInvestmentHoldingsByAccount returns every holding in an account', async () => {
    await expect(getInvestmentHoldingsByAccount(seedInvestmentAccount.id)).resolves.toEqual(
      seedInvestmentHoldings,
    )
  })

  it('getInvestmentHolding returns one holding with its hasHistory flag and freshness data', async () => {
    const holding = await getInvestmentHolding('iholding-btc')

    expect(holding).toMatchObject({
      id: 'iholding-btc',
      hasHistory: true,
      needsSnapshot: true,
      latestSnapshot: { date: '2026-08-05', balance: 900 },
    })
  })

  it('createInvestmentHolding posts the pair and returns the new holding', async () => {
    const created = await createInvestmentHolding({
      productId: 'iprod-selic',
      accountId: seedInvestmentAccount.id,
    })

    expect(created).toMatchObject({
      productId: 'iprod-selic',
      accountId: seedInvestmentAccount.id,
      closed: false,
    })
  })

  it('editInvestmentHoldingNotes patches the notes and returns the updated holding', async () => {
    const updated = await editInvestmentHoldingNotes('iholding-selic', {
      additionalNotes: 'bought via promo',
    })

    expect(updated).toMatchObject({ id: 'iholding-selic', additionalNotes: 'bought via promo' })
  })

  it('closeInvestmentHolding posts to the close endpoint and returns the closed holding', async () => {
    const closed = await closeInvestmentHolding('iholding-selic')

    expect(closed).toMatchObject({ id: 'iholding-selic', closed: true })
    expect(closed.closedDate).toBeTruthy()
  })

  it('deleteInvestmentHolding resolves on success', async () => {
    await expect(deleteInvestmentHolding('iholding-selic')).resolves.toBeUndefined()
  })

  it('deleteInvestmentHolding maps a 409 to the close-instead message', async () => {
    server.use(investmentHoldingDeleteConflictHandler)

    const error: unknown = await deleteInvestmentHolding('iholding-btc').catch(
      (err: unknown) => err,
    )

    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).status).toBe(409)
    expect((error as ApiError).message).toBe(DELETE_CONFLICT_MESSAGE)
  })

  it('closeInvestmentHolding maps a 409 to a message pointing at the zero snapshot', async () => {
    server.use(investmentHoldingCloseConflictHandler)

    const error: unknown = await closeInvestmentHolding('iholding-old').catch((err: unknown) => err)

    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).status).toBe(409)
    expect((error as ApiError).message).toBe(CLOSE_CONFLICT_MESSAGE)
  })

  it('createInvestmentHolding maps a 409 to the save-conflict message', async () => {
    server.use(investmentHoldingCreateConflictHandler)

    const error: unknown = await createInvestmentHolding({
      productId: 'iprod-btc',
      accountId: seedInvestmentAccount.id,
    }).catch((err: unknown) => err)

    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).status).toBe(409)
    expect((error as ApiError).message).toBe(SAVE_CONFLICT_MESSAGE)
  })
})
