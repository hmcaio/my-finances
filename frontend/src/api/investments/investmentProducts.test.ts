import { describe, expect, it } from 'vitest'
import { server } from '../../mocks/server'
import { seedInvestmentAccount } from '../../mocks/handlers/accounts'
import {
  investmentProductCreateConflictHandler,
  investmentProductDeleteConflictHandler,
  investmentProductEditConflictHandler,
  seedInvestmentProducts,
} from '../../mocks/handlers/investmentProducts'
import { investmentHoldingsStore } from '../../mocks/handlers/investmentHoldings'
import { ApiError } from '../core/apiError'
import {
  DELETE_CONFLICT_MESSAGE,
  SAVE_CONFLICT_MESSAGE,
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

  it('getInvestmentProduct returns one product', async () => {
    const product = await getInvestmentProduct('iprod-btc')

    expect(product).toMatchObject({ id: 'iprod-btc', name: 'Bitcoin' })
  })

  it('createInvestmentProduct posts the product and its first holding, sub-category optional', async () => {
    const created = await createInvestmentProduct({
      accountId: seedInvestmentAccount.id,
      investmentCategoryId: 'icat-crypto',
      name: 'Ethereum',
    })

    expect(created).toMatchObject({
      investmentCategoryId: 'icat-crypto',
      investmentSubcategoryId: null,
      name: 'Ethereum',
    })
    expect(investmentHoldingsStore.list()).toContainEqual(
      expect.objectContaining({ productId: created.id, accountId: seedInvestmentAccount.id }),
    )
  })

  it('editInvestmentProduct patches the full body and returns the updated product', async () => {
    const updated = await editInvestmentProduct('iprod-selic', {
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

  it('deleteInvestmentProduct resolves on success', async () => {
    investmentHoldingsStore.remove('iholding-selic')
    await expect(deleteInvestmentProduct('iprod-selic')).resolves.toBeUndefined()
  })

  it('deleteInvestmentProduct maps a 409 to the remove-holdings-first message', async () => {
    server.use(investmentProductDeleteConflictHandler)

    const error: unknown = await deleteInvestmentProduct('iprod-btc').catch((err: unknown) => err)

    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).status).toBe(409)
    expect((error as ApiError).message).toBe(DELETE_CONFLICT_MESSAGE)
  })

  it('create and edit map a 409 to the save-conflict message', async () => {
    server.use(investmentProductCreateConflictHandler, investmentProductEditConflictHandler)
    const createBody = {
      accountId: seedInvestmentAccount.id,
      investmentCategoryId: 'icat-crypto',
      name: 'Bitcoin',
    }
    const editBody = { investmentCategoryId: 'icat-crypto', name: 'Bitcoin' }

    const created: unknown = await createInvestmentProduct(createBody).catch((err: unknown) => err)
    const edited: unknown = await editInvestmentProduct('iprod-selic', editBody).catch(
      (err: unknown) => err,
    )

    for (const error of [created, edited]) {
      expect(error).toBeInstanceOf(ApiError)
      expect((error as ApiError).status).toBe(409)
      expect((error as ApiError).message).toBe(SAVE_CONFLICT_MESSAGE)
    }
  })
})
