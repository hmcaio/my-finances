import { apiClient } from './client'
import { unwrap } from './apiError'
import type { components } from './generated/schema'

/**
 * An investment product as returned by the API (PRD S5.8, F008): a holding inside an INVESTMENT
 * account, classified by a category and an optional sub-category. `hasHistory` (snapshots or
 * buy/sell transfers, F009) is what decides between delete and close.
 */
export interface InvestmentProduct {
  id: string
  accountId: string
  investmentCategoryId: string
  /** `null` when the product is classified by category only (e.g. Crypto). */
  investmentSubcategoryId: string | null
  name: string
  closedDate: string | null
  closed: boolean
  hasHistory: boolean
  /** A trade is newer than the latest snapshot (F009): the value shown may be out of date. */
  needsSnapshot: boolean
  /** The most recent snapshot, `null` if none. */
  latestSnapshot: { date: string; balance: number } | null
}

export type CreateInvestmentProductRequest = components['schemas']['CreateInvestmentProductRequest']
export type UpdateInvestmentProductRequest = components['schemas']['UpdateInvestmentProductRequest']

// The backend sends no message text, so every expected 409 needs its own wording here.
export const SAVE_CONFLICT_MESSAGE =
  'The product could not be saved: its name must be unique within the account, the account must be an open investment account, and the sub-category must belong to the chosen category, and a product with history cannot move to another account.'
export const CLOSE_CONFLICT_MESSAGE =
  'This product could not be closed: it is already closed, or its latest snapshot still has value. Record a zero snapshot (or sell the entire position) first.'
export const DELETE_CONFLICT_MESSAGE =
  'This product has history (snapshots or trades) and cannot be deleted - close it instead.'

/** Fetches products, optionally only those of one account. */
export async function getInvestmentProducts(accountId?: string): Promise<InvestmentProduct[]> {
  return unwrap(
    apiClient.get<InvestmentProduct[]>('/investment-products', {
      params: accountId ? { accountId } : undefined,
    }),
  )
}

export async function getInvestmentProduct(id: string): Promise<InvestmentProduct> {
  return unwrap(apiClient.get<InvestmentProduct>(`/investment-products/${id}`))
}

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

/** Closes a product. Not reversible through the UI. */
export async function closeInvestmentProduct(id: string): Promise<InvestmentProduct> {
  return unwrap(
    apiClient.post<InvestmentProduct>(`/investment-products/${id}/close`),
    CLOSE_CONFLICT_MESSAGE,
  )
}

/** Deletes a product; only succeeds with zero history (a 409 means close it instead). */
export async function deleteInvestmentProduct(id: string): Promise<void> {
  await unwrap(apiClient.delete<void>(`/investment-products/${id}`), DELETE_CONFLICT_MESSAGE)
}
