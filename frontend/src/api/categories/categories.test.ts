import { expect } from 'vitest'
import {
  categoryCreateConflictHandler,
  categoryDeleteConflictHandler,
  categoryRenameConflictHandler,
  seedCategories,
} from '../../mocks/handlers/categories'
import { describeNamedEntityApi } from '../../test/apiContract'
import {
  CONFLICT_MESSAGE,
  DUPLICATE_NAME_MESSAGE,
  createCategory,
  deleteCategory,
  getCategories,
  renameCategory,
  type Category,
  type CreateCategoryRequest,
  type UpdateCategoryRequest,
} from './categories'

describeNamedEntityApi<Category, CreateCategoryRequest, UpdateCategoryRequest>({
  label: 'categories',
  api: {
    get: getCategories,
    create: createCategory,
    rename: renameCategory,
    remove: deleteCategory,
  },
  seedList: seedCategories,
  create: {
    request: { name: 'Rent', type: 'EXPENSE' },
    expect: { name: 'Rent', type: 'EXPENSE', id: expect.any(String) },
  },
  rename: {
    id: 'cat-1',
    request: { name: 'Groceries & Dining' },
    expect: { id: 'cat-1', name: 'Groceries & Dining' },
  },
  remove: { id: 'cat-1' },
  conflict: {
    message: CONFLICT_MESSAGE,
    duplicateNameMessage: DUPLICATE_NAME_MESSAGE,
    createHandler: categoryCreateConflictHandler,
    renameHandler: categoryRenameConflictHandler,
    deleteHandler: categoryDeleteConflictHandler,
    createRequest: { name: 'Groceries', type: 'EXPENSE' },
    renameId: 'cat-2',
    renameRequest: { name: 'Groceries' },
  },
})
