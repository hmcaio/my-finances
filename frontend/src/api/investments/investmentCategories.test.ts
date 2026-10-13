import { describe, expect, it } from 'vitest'
import {
  investmentCategoryCreateConflictHandler,
  investmentCategoryDeleteConflictHandler,
  investmentCategoryRenameConflictHandler,
  seedInvestmentCategories,
} from '../../mocks/handlers/investmentCategories'
import { describeNamedEntityApi } from '../../test/apiContract'
import { PROBLEM_TYPE_MESSAGES } from '../core/apiError'
import {
  createInvestmentCategory,
  deleteInvestmentCategory,
  getInvestmentCategories,
  renameInvestmentCategory,
  type CreateInvestmentCategoryRequest,
  type InvestmentCategory,
  type UpdateInvestmentCategoryRequest,
} from './investmentCategories'

describeNamedEntityApi<
  InvestmentCategory,
  CreateInvestmentCategoryRequest,
  UpdateInvestmentCategoryRequest
>({
  label: 'investment categories',
  api: {
    get: getInvestmentCategories,
    create: createInvestmentCategory,
    rename: renameInvestmentCategory,
    remove: deleteInvestmentCategory,
  },
  seedList: seedInvestmentCategories,
  create: {
    request: { name: 'Real Estate' },
    expect: { name: 'Real Estate', subcategories: [], id: expect.any(String) },
  },
  rename: {
    id: 'icat-fixed',
    request: { name: 'Renda Fixa' },
    expect: { id: 'icat-fixed', name: 'Renda Fixa' },
  },
  remove: { id: 'icat-crypto' },
  conflict: {
    message: PROBLEM_TYPE_MESSAGES['urn:my-finances:error:investment-category-in-use'],
    duplicateNameMessage:
      PROBLEM_TYPE_MESSAGES['urn:my-finances:error:investment-category-name-already-exists'],
    createHandler: investmentCategoryCreateConflictHandler,
    renameHandler: investmentCategoryRenameConflictHandler,
    deleteHandler: investmentCategoryDeleteConflictHandler,
    createRequest: { name: 'Crypto' },
    renameId: 'icat-fixed',
    renameRequest: { name: 'Crypto' },
  },
})

// Bespoke: the categories response nests sub-categories, unlike any other flat-taxonomy entity.
describe('investment categories API client - nested sub-categories', () => {
  it('getInvestmentCategories returns the categories with their sub-categories nested', async () => {
    const categories = await getInvestmentCategories()

    expect(categories.find((c) => c.name === 'Fixed Income')?.subcategories.length).toBeGreaterThan(
      0,
    )
  })
})
