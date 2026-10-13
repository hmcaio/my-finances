import { apiClient } from '../core/client'
import { unwrap } from '../core/apiError'
import type { components } from '../generated/schema'

export type CategoryType = 'INCOME' | 'EXPENSE'

/**
 * A Category as returned by the API (PRD S5.1). `builtIn` marks the one fallback row per type
 * ("Other Expense" / "Other Income" by default). `fuelCategory` (F024, ADR 0021) marks the single
 * dedicated fuel category - a Transaction carries fuel details if and only if its category is this
 * one. `dividendCategory` (F026, ADR 0023) is the same shape for the single dedicated dividend
 * category - a Transaction carries an `investmentHoldingId` if and only if its category is this
 * one. All three flags are independent, but identical in effect: each one blocks both rename and
 * delete (issue #117 - `builtIn` used to stay freely renamable; it no longer does). Use {@link
 * isProtectedCategory} rather than checking the three fields ad hoc.
 */
export interface Category {
  id: string
  name: string
  type: CategoryType
  builtIn: boolean
  fuelCategory: boolean
  dividendCategory: boolean
}

/** Whether `category`'s rename/delete actions should be hidden/rejected (issue #117). */
export function isProtectedCategory(category: Category): boolean {
  return category.builtIn || category.fuelCategory || category.dividendCategory
}

export type CreateCategoryRequest = components['schemas']['CreateCategoryRequest']
export type UpdateCategoryRequest = components['schemas']['UpdateCategoryRequest']

/** Matches the backend's `TextFieldConstraints.MAX_NAME_LENGTH`, so inputs can cap what is typed. */
export const CATEGORY_NAME_MAX_LENGTH = 100

/**
 * Fetches every category. Reused as a dropdown-options source by F004 (Transactions), F006
 * (Budgets), and F007 (Recurring Templates), per F002 spec.
 */
export async function getCategories(): Promise<Category[]> {
  return unwrap(apiClient.get<Category[]>('/categories'))
}

export async function createCategory(request: CreateCategoryRequest): Promise<Category> {
  return unwrap(apiClient.post<Category>('/categories', request))
}

/** Renames a category. Type is immutable after creation (F002 spec) - there's no way to change it.
 * A `409` is one of `CategoryNameAlreadyExistsException`/`BuiltInCategoryException`/
 * `FuelCategoryException`/`DividendCategoryException` (F031/ADR 0026: see `PROBLEM_TYPE_MESSAGES`
 * in `apiError.ts`). */
export async function renameCategory(
  id: string,
  request: UpdateCategoryRequest,
): Promise<Category> {
  return unwrap(apiClient.patch<Category>(`/categories/${id}`, request))
}

/** Deletes an unreferenced, unprotected category; a `409` is one of `BuiltInCategoryException`/
 * `FuelCategoryException`/`DividendCategoryException`/`CategoryInUseException` (F031/ADR 0026). */
export async function deleteCategory(id: string): Promise<void> {
  await unwrap(apiClient.delete<void>(`/categories/${id}`))
}
