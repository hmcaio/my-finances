import { describe, expect, it } from 'vitest'
import { server } from '../mocks/server'
import {
  categoryCreateConflictHandler,
  categoryDeleteConflictHandler,
  categoryRenameConflictHandler,
  seedCategories,
} from '../mocks/handlers/categories'
import { ApiError } from './apiError'
import {
  CONFLICT_MESSAGE,
  DUPLICATE_NAME_MESSAGE,
  createCategory,
  deleteCategory,
  getCategories,
  renameCategory,
} from './categories'

describe('categories API client', () => {
  it('getCategories returns the seeded list', async () => {
    await expect(getCategories()).resolves.toEqual(seedCategories)
  })

  it('createCategory posts the new category and returns the created one', async () => {
    const created = await createCategory({ name: 'Rent', type: 'EXPENSE' })
    expect(created).toMatchObject({ name: 'Rent', type: 'EXPENSE' })
    expect(created.id).toBeTruthy()
  })

  it('renameCategory patches the name and returns the updated category', async () => {
    const updated = await renameCategory('cat-1', { name: 'Groceries & Dining' })
    expect(updated).toMatchObject({ id: 'cat-1', name: 'Groceries & Dining' })
  })

  it('deleteCategory resolves on success', async () => {
    await expect(deleteCategory('cat-1')).resolves.toBeUndefined()
  })

  it('deleteCategory maps a 409 to the delete-conflict message', async () => {
    server.use(categoryDeleteConflictHandler)

    const error: unknown = await deleteCategory('cat-1').catch((err: unknown) => err)

    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).status).toBe(409)
    expect((error as ApiError).message).toBe(CONFLICT_MESSAGE)
  })

  it('createCategory maps a 409 to the duplicate-name message', async () => {
    server.use(categoryCreateConflictHandler)

    const error: unknown = await createCategory({ name: 'Groceries', type: 'EXPENSE' }).catch(
      (err: unknown) => err,
    )

    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).status).toBe(409)
    expect((error as ApiError).message).toBe(DUPLICATE_NAME_MESSAGE)
  })

  it('renameCategory maps a 409 to the duplicate-name message', async () => {
    server.use(categoryRenameConflictHandler)

    const error: unknown = await renameCategory('cat-2', { name: 'Groceries' }).catch(
      (err: unknown) => err,
    )

    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).status).toBe(409)
    expect((error as ApiError).message).toBe(DUPLICATE_NAME_MESSAGE)
  })
})
