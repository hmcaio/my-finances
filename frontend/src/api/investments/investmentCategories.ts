import { apiClient } from '../core/client'
import { unwrap } from '../core/apiError'
import type { components } from '../generated/schema'

/** A sub-category as nested under its category in the categories response. */
export interface InvestmentSubcategoryEntry {
  id: string
  name: string
}

/**
 * An investment category (PRD S5.8, F008) with its sub-categories nested: one small call feeds the
 * settings screen and every product picker.
 */
export interface InvestmentCategory {
  id: string
  name: string
  subcategories: InvestmentSubcategoryEntry[]
}

/** Matches the backend's `TextFieldConstraints.MAX_NAME_LENGTH`, so inputs can cap what is typed. */
export const INVESTMENT_NAME_MAX_LENGTH = 100

export type CreateInvestmentCategoryRequest =
  components['schemas']['CreateInvestmentCategoryRequest']
export type UpdateInvestmentCategoryRequest =
  components['schemas']['UpdateInvestmentCategoryRequest']

// The backend sends no message text, so every expected 409 needs its own wording here.
export const CONFLICT_MESSAGE =
  'This category still has sub-categories or is used by an investment product - delete or reclassify those first.'

export const DUPLICATE_NAME_MESSAGE = 'An investment category with this name already exists.'

/** Fetches every category with its sub-categories, both levels sorted by name. */
export async function getInvestmentCategories(): Promise<InvestmentCategory[]> {
  return unwrap(apiClient.get<InvestmentCategory[]>('/investment-categories'))
}

export async function createInvestmentCategory(
  request: CreateInvestmentCategoryRequest,
): Promise<InvestmentCategory> {
  return unwrap(
    apiClient.post<InvestmentCategory>('/investment-categories', request),
    DUPLICATE_NAME_MESSAGE,
  )
}

export async function renameInvestmentCategory(
  id: string,
  request: UpdateInvestmentCategoryRequest,
): Promise<InvestmentCategory> {
  return unwrap(
    apiClient.patch<InvestmentCategory>(`/investment-categories/${id}`, request),
    DUPLICATE_NAME_MESSAGE,
  )
}

/** Deletes a category; a 409 means it still has sub-categories or a product uses it. */
export async function deleteInvestmentCategory(id: string): Promise<void> {
  await unwrap(apiClient.delete<void>(`/investment-categories/${id}`), CONFLICT_MESSAGE)
}
