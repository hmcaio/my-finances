import { expect } from 'vitest'
import {
  investmentSubcategoryCreateConflictHandler,
  investmentSubcategoryDeleteConflictHandler,
  investmentSubcategoryRenameConflictHandler,
} from '../../mocks/handlers/investmentSubcategories'
import { describeNamedEntityApi } from '../../test/apiContract'
import {
  CONFLICT_MESSAGE,
  DUPLICATE_NAME_MESSAGE,
  createInvestmentSubcategory,
  deleteInvestmentSubcategory,
  renameInvestmentSubcategory,
  type CreateInvestmentSubcategoryRequest,
  type InvestmentSubcategory,
  type UpdateInvestmentSubcategoryRequest,
} from './investmentSubcategories'

// No `api.get`: sub-categories have no standalone list endpoint - they're only ever read nested
// under their category (`investmentCategories.ts`'s `getInvestmentCategories`).
describeNamedEntityApi<
  InvestmentSubcategory,
  CreateInvestmentSubcategoryRequest,
  UpdateInvestmentSubcategoryRequest
>({
  label: 'investment sub-categories',
  api: {
    create: createInvestmentSubcategory,
    rename: renameInvestmentSubcategory,
    remove: deleteInvestmentSubcategory,
  },
  create: {
    request: { investmentCategoryId: 'icat-fixed', name: 'LCI' },
    expect: { investmentCategoryId: 'icat-fixed', name: 'LCI', id: expect.any(String) },
  },
  rename: {
    id: 'isub-cdb',
    request: { name: 'CDB / RDB' },
    expect: { id: 'isub-cdb', investmentCategoryId: 'icat-fixed', name: 'CDB / RDB' },
  },
  remove: { id: 'isub-cdb' },
  conflict: {
    message: CONFLICT_MESSAGE,
    duplicateNameMessage: DUPLICATE_NAME_MESSAGE,
    createHandler: investmentSubcategoryCreateConflictHandler,
    renameHandler: investmentSubcategoryRenameConflictHandler,
    deleteHandler: investmentSubcategoryDeleteConflictHandler,
    createRequest: { investmentCategoryId: 'icat-fixed', name: 'CDB' },
    renameId: 'isub-selic',
    renameRequest: { name: 'CDB' },
  },
})
