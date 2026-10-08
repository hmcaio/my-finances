import { apiClient } from '../core/client'
import { unwrap } from '../core/apiError'
import type { components } from '../generated/schema'

/**
 * An investment product as returned by the API (PRD S5.8, F022 spec): pure taxonomy for an
 * instrument, classified by a category and an optional sub-category, globally unique by name.
 * `accountId`/`closedDate`/`hasHistory`/`needsSnapshot`/`latestSnapshot` moved to
 * `InvestmentHolding` (F022/ADR 0020) - the same instrument at two brokers is one product with two
 * holdings. `closed` is F023's derived, not-stored status: every holding closed, or none at all.
 */
export interface InvestmentProduct {
  id: string
  investmentCategoryId: string
  /** `null` when the product is classified by category only (e.g. Crypto). */
  investmentSubcategoryId: string | null
  name: string
  /** `null` when the product carries no remark. */
  additionalNotes: string | null
  closed: boolean
  /** `null` when the product carries no ticker (F026, ADR 0023). Generalized, not FII-only. */
  ticker: string | null
  /** `null` when the product carries no segment (F026, ADR 0023). Generalized, not FII-only. */
  segmentId: string | null
}

export type CreateInvestmentProductRequest = components['schemas']['CreateInvestmentProductRequest']
export type UpdateInvestmentProductRequest = components['schemas']['UpdateInvestmentProductRequest']

/** Derived, not stored - the same pattern as a holding's `needsSnapshot` (F023 spec). */
export type InvestmentProductStatus = 'OPEN' | 'CLOSED' | 'ALL'

/** Optional filter dimensions for the global product list (F023 spec's list query params). */
export interface InvestmentProductFilter {
  categoryId?: string
  subcategoryId?: string
  /** Has a holding there (open or closed), not "opened by". */
  accountId?: string
  /** Contains, case-insensitive. */
  name?: string
  /** Defaults to `OPEN` on the backend. */
  status?: InvestmentProductStatus
}

/** One page of products - mirrors the backend's `PagedModel` envelope (F023 spec). */
export interface InvestmentProductPage {
  content: InvestmentProduct[]
  page: {
    size: number
    number: number
    totalElements: number
    totalPages: number
  }
}

// The backend sends no message text, so every expected 409 needs its own wording here.
export const SAVE_CONFLICT_MESSAGE =
  'The product could not be saved: its name must already be unique, the account must be an open investment account, and the sub-category must belong to the chosen category.'
export const DELETE_CONFLICT_MESSAGE =
  'This product still has a holding (even a closed, empty one) and cannot be deleted - remove its holdings first.'

/**
 * Fetches a filtered, paginated page of products (F023 spec: the list endpoint became paginated,
 * a breaking shape change - fine pre-1.0/local app). `page` is 0-indexed; `page`/`size` default to
 * the backend's own defaults (0, 20) when omitted.
 */
export async function getInvestmentProductsPage(
  filter: InvestmentProductFilter = {},
  page?: number,
  size?: number,
): Promise<InvestmentProductPage> {
  return unwrap(
    apiClient.get<InvestmentProductPage>('/investment-products', {
      params: { ...filter, page, size },
    }),
  )
}

/**
 * Fetches every product regardless of status, across every page (pure taxonomy; used by callers
 * that resolve a product's name for a holding/trade that could reference a closed product, not the
 * new filtered/paginated Products list - F022's plain, unfiltered list is now paginated server-side,
 * so this loops pages to preserve the "every product" behavior those callers rely on).
 */
export async function getInvestmentProducts(): Promise<InvestmentProduct[]> {
  const all: InvestmentProduct[] = []
  let page = 0
  for (;;) {
    const result = await getInvestmentProductsPage({ status: 'ALL' }, page, 100)
    all.push(...result.content)
    if (page + 1 >= result.page.totalPages) break
    page += 1
  }
  return all
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
