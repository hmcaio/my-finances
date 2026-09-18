import { apiClient } from './client'
import { unwrap } from './apiError'
import type { components } from './generated/schema'

export type CategoryType = 'INCOME' | 'EXPENSE'

/** A Category as returned by the API (PRD S5.1). */
export interface Category {
  id: string
  name: string
  type: CategoryType
}

export type CreateCategoryRequest = components['schemas']['CreateCategoryRequest']
export type UpdateCategoryRequest = components['schemas']['UpdateCategoryRequest']

const CONFLICT_MESSAGE =
  'This category is used by existing transactions, budgets, or recurring templates — reassign them before deleting it.'

const DUPLICATE_NAME_MESSAGE = 'A category with this name already exists.'

/**
 * Fetches every category. Reused as a dropdown-options source by F004 (Transactions), F006
 * (Budgets), and F007 (Recurring Templates), per F002 spec.
 */
export async function getCategories(): Promise<Category[]> {
  return unwrap(apiClient.get<Category[]>('/categories'))
}

export async function createCategory(request: CreateCategoryRequest): Promise<Category> {
  return unwrap(apiClient.post<Category>('/categories', request), DUPLICATE_NAME_MESSAGE)
}

/** Renames a category. Type is immutable after creation (F002 spec) - there's no way to change it. */
export async function renameCategory(
  id: string,
  request: UpdateCategoryRequest,
): Promise<Category> {
  return unwrap(apiClient.patch<Category>(`/categories/${id}`, request), DUPLICATE_NAME_MESSAGE)
}

export async function deleteCategory(id: string): Promise<void> {
  await unwrap(apiClient.delete<void>(`/categories/${id}`), CONFLICT_MESSAGE)
}
