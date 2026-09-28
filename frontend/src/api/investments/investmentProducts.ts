import { apiClient } from '../core/client'
import { unwrap } from '../core/apiError'
import type { components } from '../generated/schema'

/**
 * An investment product as returned by the API (PRD S5.8, F022 spec): pure taxonomy for an
 * instrument, classified by a category and an optional sub-category, globally unique by name.
 * `accountId`/`closedDate`/`hasHistory`/`needsSnapshot`/`latestSnapshot` moved to
 * `InvestmentHolding` (F022/ADR 0020) - the same instrument at two brokers is one product with two
 * holdings.
 */
export interface InvestmentProduct {
  id: string
  investmentCategoryId: string
  /** `null` when the product is classified by category only (e.g. Crypto). */
  investmentSubcategoryId: string | null
  name: string
  /** `null` when the product carries no remark. */
  additionalNotes: string | null
}

export type CreateInvestmentProductRequest = components['schemas']['CreateInvestmentProductRequest']
export type UpdateInvestmentProductRequest = components['schemas']['UpdateInvestmentProductRequest']

// The backend sends no message text, so every expected 409 needs its own wording here.
export const SAVE_CONFLICT_MESSAGE =
  'The product could not be saved: its name must already be unique, the account must be an open investment account, and the sub-category must belong to the chosen category.'
export const DELETE_CONFLICT_MESSAGE =
  'This product still has a holding (even a closed, empty one) and cannot be deleted - remove its holdings first.'

/** Fetches every product (pure taxonomy - no longer filterable by account, F023 adds that). */
export async function getInvestmentProducts(): Promise<InvestmentProduct[]> {
  return unwrap(apiClient.get<InvestmentProduct[]>('/investment-products'))
}

export async function getInvestmentProduct(id: string): Promise<InvestmentProduct> {
  return unwrap(apiClient.get<InvestmentProduct>(`/investment-products/${id}`))
}

/** Creates the product and its first holding together (a two-write, `@Transactional` use case). */
export async function createInvestmentProduct(
  request: CreateInvestmentProductRequest,
): Promise<InvestmentProduct> {
  return unwrap(
    apiClient.post<InvestmentProduct>('/investment-products', request),
    SAVE_CONFLICT_MESSAGE,
  )
}

/** Full replace (PATCH): reclassifying a product regroups its past allocation too (F008 spec). */
export async function editInvestmentProduct(
  id: string,
  request: UpdateInvestmentProductRequest,
): Promise<InvestmentProduct> {
  return unwrap(
    apiClient.patch<InvestmentProduct>(`/investment-products/${id}`, request),
    SAVE_CONFLICT_MESSAGE,
  )
}

/** Deletes a product; only succeeds with zero holdings (a 409 means remove them first). */
export async function deleteInvestmentProduct(id: string): Promise<void> {
  await unwrap(apiClient.delete<void>(`/investment-products/${id}`), DELETE_CONFLICT_MESSAGE)
}
