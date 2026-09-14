import { assertOk } from './apiError'
import type { components } from './generated/schema'

// Local dev only: backend is always http://localhost:8080 (PRD S7.1). See src/api/health.ts.
const API_BASE_URL = 'http://localhost:8080'

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

/**
 * Fetches every category. Reused as a dropdown-options source by F004 (Transactions), F006
 * (Budgets), and F007 (Recurring Templates), per F002 spec.
 */
export async function getCategories(): Promise<Category[]> {
  const response = await fetch(`${API_BASE_URL}/api/categories`)
  await assertOk(response)
  return (await response.json()) as Category[]
}

export async function createCategory(request: CreateCategoryRequest): Promise<Category> {
  const response = await fetch(`${API_BASE_URL}/api/categories`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
  })
  await assertOk(response)
  return (await response.json()) as Category
}

/** Renames a category. Type is immutable after creation (F002 spec) - there's no way to change it. */
export async function renameCategory(
  id: string,
  request: UpdateCategoryRequest,
): Promise<Category> {
  const response = await fetch(`${API_BASE_URL}/api/categories/${id}`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
  })
  await assertOk(response)
  return (await response.json()) as Category
}

export async function deleteCategory(id: string): Promise<void> {
  const response = await fetch(`${API_BASE_URL}/api/categories/${id}`, { method: 'DELETE' })
  await assertOk(response, CONFLICT_MESSAGE)
}
