import { describe, expect, it } from 'vitest'
import { http, HttpResponse } from 'msw'
import { server } from '../mocks/server'
import { seedInvestmentAccount } from '../mocks/handlers/accounts'
import {
  investmentProductCloseConflictHandler,
  investmentProductCreateConflictHandler,
  investmentProductDeleteConflictHandler,
  investmentProductEditConflictHandler,
  seedInvestmentProducts,
} from '../mocks/handlers/investmentProducts'
import { ApiError } from './apiError'
import {
  CLOSE_CONFLICT_MESSAGE,
  DELETE_CONFLICT_MESSAGE,
  SAVE_CONFLICT_MESSAGE,
  closeInvestmentProduct,
  createInvestmentProduct,
  deleteInvestmentProduct,
  editInvestmentProduct,
  getInvestmentProduct,
  getInvestmentProducts,
} from './investmentProducts'

describe('investment products API client', () => {
  it('getInvestmentProducts returns every product', async () => {
    await expect(getInvestmentProducts()).resolves.toEqual(seedInvestmentProducts)
  })

  it('getInvestmentProducts filters by account through the accountId query param', async () => {
    let sentAccountId: string | null = null
    server.use(
      http.get('/api/investment-products', ({ request }) => {
        sentAccountId = new URL(request.url).searchParams.get('accountId')
        return HttpResponse.json([])
      }),
    )

    await getInvestmentProducts(seedInvestmentAccount.id)

    expect(sentAccountId).toBe(seedInvestmentAccount.id)
  })

  it('getInvestmentProduct returns one product with its hasHistory flag and freshness data', async () => {
    const product = await getInvestmentProduct('iprod-btc')

    expect(product).toMatchObject({
      id: 'iprod-btc',
      hasHistory: true,
      needsSnapshot: true,
      latestSnapshot: { date: '2026-08-05', balance: 900 },
    })
  })

  it('createInvestmentProduct posts the product, sub-category optional, and returns it', async () => {
    const created = await createInvestmentProduct({
      accountId: seedInvestmentAccount.id,
      investmentCategoryId: 'icat-crypto',
      name: 'Ethereum',
    })

    expect(created).toMatchObject({
      accountId: seedInvestmentAccount.id,
      investmentCategoryId: 'icat-crypto',
      investmentSubcategoryId: null,
      name: 'Ethereum',
      closed: false,
    })
  })

  it('editInvestmentProduct patches the full body and returns the updated product', async () => {
    const updated = await editInvestmentProduct('iprod-selic', {
      accountId: seedInvestmentAccount.id,
      investmentCategoryId: 'icat-fixed',
      investmentSubcategoryId: 'isub-cdb',
      name: 'CDB 110% CDI',
    })

    expect(updated).toMatchObject({
      id: 'iprod-selic',
      investmentSubcategoryId: 'isub-cdb',
      name: 'CDB 110% CDI',
    })
  })

  it('closeInvestmentProduct posts to the close endpoint and returns the closed product', async () => {
    const closed = await closeInvestmentProduct('iprod-selic')

    expect(closed).toMatchObject({ id: 'iprod-selic', closed: true })
    expect(closed.closedDate).toBeTruthy()
  })

  it('deleteInvestmentProduct resolves on success', async () => {
    await expect(deleteInvestmentProduct('iprod-selic')).resolves.toBeUndefined()
  })

  it('deleteInvestmentProduct maps a 409 to the close-instead message', async () => {
    server.use(investmentProductDeleteConflictHandler)

    const error: unknown = await deleteInvestmentProduct('iprod-btc').catch((err: unknown) => err)

    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).status).toBe(409)
    expect((error as ApiError).message).toBe(DELETE_CONFLICT_MESSAGE)
  })

  it('closeInvestmentProduct maps a 409 to a message pointing at the zero snapshot', async () => {
    server.use(investmentProductCloseConflictHandler)

    const error: unknown = await closeInvestmentProduct('iprod-old').catch((err: unknown) => err)

    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).status).toBe(409)
    expect((error as ApiError).message).toBe(CLOSE_CONFLICT_MESSAGE)
  })

  it('create and edit map a 409 to the save-conflict message', async () => {
    server.use(investmentProductCreateConflictHandler, investmentProductEditConflictHandler)
    const body = {
      accountId: seedInvestmentAccount.id,
      investmentCategoryId: 'icat-crypto',
      name: 'Bitcoin',
    }

    const created: unknown = await createInvestmentProduct(body).catch((err: unknown) => err)
    const edited: unknown = await editInvestmentProduct('iprod-selic', body).catch(
      (err: unknown) => err,
    )

    for (const error of [created, edited]) {
      expect(error).toBeInstanceOf(ApiError)
      expect((error as ApiError).status).toBe(409)
      expect((error as ApiError).message).toBe(SAVE_CONFLICT_MESSAGE)
    }
  })
})
