import { apiClient } from '../core/client'
import { unwrap } from '../core/apiError'
import type { components } from '../generated/schema'

export type CategoryType = 'INCOME' | 'EXPENSE'

/**
 * A Category as returned by the API (PRD S5.1). `builtIn` marks the one fallback row per type
 * ("Other Expense" / "Other Income" by default) - it can be renamed but never deleted.
 */
export interface Category {
  id: string
  name: string
  type: CategoryType
  builtIn: boolean
}

export type CreateCategoryRequest = components['schemas']['CreateCategoryRequest']
export type UpdateCategoryRequest = components['schemas']['UpdateCategoryRequest']

/** Matches the backend's `TextFieldConstraints.MAX_NAME_LENGTH`, so inputs can cap what is typed. */
export const CATEGORY_NAME_MAX_LENGTH = 100

export const CONFLICT_MESSAGE =
  'This category is used by existing transactions, budgets, or recurring templates — reassign them before deleting it.'

export const DUPLICATE_NAME_MESSAGE = 'A category with this name already exists.'

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

/** Deletes an unreferenced, non-built-in category; a 409 means something still uses it. */
export async function deleteCategory(id: string): Promise<void> {
  await unwrap(apiClient.delete<void>(`/categories/${id}`), CONFLICT_MESSAGE)
}
