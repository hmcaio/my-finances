import { describe, expect, it } from 'vitest'
import { server } from '../mocks/server'
import {
  investmentCategoryCreateConflictHandler,
  investmentCategoryDeleteConflictHandler,
  investmentCategoryRenameConflictHandler,
  seedInvestmentCategories,
} from '../mocks/handlers/investmentCategories'
import { ApiError } from './apiError'
import {
  createInvestmentCategory,
  deleteInvestmentCategory,
  getInvestmentCategories,
  renameInvestmentCategory,
} from './investmentCategories'

describe('investment categories API client', () => {
  it('getInvestmentCategories returns the categories with their sub-categories nested', async () => {
    const categories = await getInvestmentCategories()

    expect(categories).toEqual(seedInvestmentCategories)
    expect(categories.find((c) => c.name === 'Fixed Income')?.subcategories.length).toBeGreaterThan(
      0,
    )
  })

  it('createInvestmentCategory posts the name and returns the created category', async () => {
    const created = await createInvestmentCategory({ name: 'Real Estate' })

    expect(created).toMatchObject({ name: 'Real Estate', subcategories: [] })
    expect(created.id).toBeTruthy()
  })

  it('renameInvestmentCategory patches the name and returns the updated category', async () => {
    const updated = await renameInvestmentCategory('icat-fixed', { name: 'Renda Fixa' })

    expect(updated).toMatchObject({ id: 'icat-fixed', name: 'Renda Fixa' })
  })

  it('deleteInvestmentCategory resolves on success', async () => {
    await expect(deleteInvestmentCategory('icat-crypto')).resolves.toBeUndefined()
  })

  it('deleteInvestmentCategory maps a 409 to the in-use message', async () => {
    server.use(investmentCategoryDeleteConflictHandler)

    const error: unknown = await deleteInvestmentCategory('icat-fixed').catch((err: unknown) => err)

    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).status).toBe(409)
    expect((error as ApiError).message).toContain('sub-categories')
  })

  it('createInvestmentCategory and rename map a 409 to the duplicate-name message', async () => {
    server.use(investmentCategoryCreateConflictHandler, investmentCategoryRenameConflictHandler)

    const created: unknown = await createInvestmentCategory({ name: 'Crypto' }).catch(
      (err: unknown) => err,
    )
    const renamed: unknown = await renameInvestmentCategory('icat-fixed', {
      name: 'Crypto',
    }).catch((err: unknown) => err)

    for (const error of [created, renamed]) {
      expect(error).toBeInstanceOf(ApiError)
      expect((error as ApiError).status).toBe(409)
      expect((error as ApiError).message).toContain('already exists')
    }
  })
})
