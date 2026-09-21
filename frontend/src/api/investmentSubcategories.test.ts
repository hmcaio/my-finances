import { describe, expect, it } from 'vitest'
import { server } from '../mocks/server'
import {
  investmentSubcategoryCreateConflictHandler,
  investmentSubcategoryDeleteConflictHandler,
  investmentSubcategoryRenameConflictHandler,
} from '../mocks/handlers/investmentSubcategories'
import { ApiError } from './apiError'
import {
  createInvestmentSubcategory,
  deleteInvestmentSubcategory,
  renameInvestmentSubcategory,
} from './investmentSubcategories'

describe('investment sub-categories API client', () => {
  it('createInvestmentSubcategory posts the parent and name and returns the created one', async () => {
    const created = await createInvestmentSubcategory({
      investmentCategoryId: 'icat-fixed',
      name: 'LCI',
    })

    expect(created).toMatchObject({ investmentCategoryId: 'icat-fixed', name: 'LCI' })
    expect(created.id).toBeTruthy()
  })

  it('renameInvestmentSubcategory patches the name and keeps the parent', async () => {
    const updated = await renameInvestmentSubcategory('isub-cdb', { name: 'CDB / RDB' })

    expect(updated).toMatchObject({
      id: 'isub-cdb',
      investmentCategoryId: 'icat-fixed',
      name: 'CDB / RDB',
    })
  })

  it('deleteInvestmentSubcategory resolves on success', async () => {
    await expect(deleteInvestmentSubcategory('isub-cdb')).resolves.toBeUndefined()
  })

  it('deleteInvestmentSubcategory maps a 409 to the in-use message', async () => {
    server.use(investmentSubcategoryDeleteConflictHandler)

    const error: unknown = await deleteInvestmentSubcategory('isub-cdb').catch(
      (err: unknown) => err,
    )

    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).status).toBe(409)
    expect((error as ApiError).message).toContain('reclassify')
  })

  it('create and rename map a 409 to the duplicate-name message', async () => {
    server.use(
      investmentSubcategoryCreateConflictHandler,
      investmentSubcategoryRenameConflictHandler,
    )

    const created: unknown = await createInvestmentSubcategory({
      investmentCategoryId: 'icat-fixed',
      name: 'CDB',
    }).catch((err: unknown) => err)
    const renamed: unknown = await renameInvestmentSubcategory('isub-selic', {
      name: 'CDB',
    }).catch((err: unknown) => err)

    for (const error of [created, renamed]) {
      expect(error).toBeInstanceOf(ApiError)
      expect((error as ApiError).status).toBe(409)
      expect((error as ApiError).message).toContain('already has a sub-category')
    }
  })
})
