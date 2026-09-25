import { apiClient } from '../core/client'
import { unwrap } from '../core/apiError'
import type { components } from '../generated/schema'

/** An investment sub-category as returned by its own endpoints (PRD S5.8, F008). */
export interface InvestmentSubcategory {
  id: string
  investmentCategoryId: string
  name: string
}

export type CreateInvestmentSubcategoryRequest =
  components['schemas']['CreateInvestmentSubcategoryRequest']
export type UpdateInvestmentSubcategoryRequest =
  components['schemas']['UpdateInvestmentSubcategoryRequest']

// The backend sends no message text, so every expected 409 needs its own wording here.
export const CONFLICT_MESSAGE =
  'This sub-category is used by an investment product - reclassify those products before deleting it.'

export const DUPLICATE_NAME_MESSAGE = 'This category already has a sub-category with this name.'

/** Creates a sub-category under a category; its parent can never be changed afterwards. */
export async function createInvestmentSubcategory(
  request: CreateInvestmentSubcategoryRequest,
): Promise<InvestmentSubcategory> {
  return unwrap(
    apiClient.post<InvestmentSubcategory>('/investment-subcategories', request),
    DUPLICATE_NAME_MESSAGE,
  )
}

/** Renames a sub-category (name only - re-parenting is not possible). */
export async function renameInvestmentSubcategory(
  id: string,
  request: UpdateInvestmentSubcategoryRequest,
): Promise<InvestmentSubcategory> {
  return unwrap(
    apiClient.patch<InvestmentSubcategory>(`/investment-subcategories/${id}`, request),
    DUPLICATE_NAME_MESSAGE,
  )
}

/** Deletes a sub-category; a 409 means a product still uses it. */
export async function deleteInvestmentSubcategory(id: string): Promise<void> {
  await unwrap(apiClient.delete<void>(`/investment-subcategories/${id}`), CONFLICT_MESSAGE)
}
