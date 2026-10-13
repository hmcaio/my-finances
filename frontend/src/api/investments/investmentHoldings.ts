import { apiClient } from '../core/client'
import { unwrap } from '../core/apiError'
import type { components } from '../generated/schema'

/**
 * An investment holding as returned by the API (F022 spec, ADR 0020): the many-to-many link
 * between an `InvestmentProduct` and the `INVESTMENT` account it's held in. `closedDate`,
 * `hasHistory`, `needsSnapshot` and `latestSnapshot` moved here from the old product response -
 * the same instrument at two brokers is one product with two independent holdings.
 */
export interface InvestmentHolding {
  id: string
  productId: string
  accountId: string
  closedDate: string | null
  closed: boolean
  additionalNotes: string | null
  hasHistory: boolean
  /** A trade is newer than the latest snapshot (F009): the value shown may be out of date. */
  needsSnapshot: boolean
  /** The most recent snapshot, `null` if none. */
  latestSnapshot: { date: string; balance: number } | null
}

export type CreateInvestmentHoldingRequest = components['schemas']['CreateInvestmentHoldingRequest']
export type UpdateInvestmentHoldingRequest = components['schemas']['UpdateInvestmentHoldingRequest']

/** Holdings of one product, across every account it's held in. */
export async function getInvestmentHoldingsByProduct(
  productId: string,
): Promise<InvestmentHolding[]> {
  return unwrap(
    apiClient.get<InvestmentHolding[]>('/investment-holdings', { params: { productId } }),
  )
}

/** Holdings in one account, across every product held there. */
export async function getInvestmentHoldingsByAccount(
  accountId: string,
): Promise<InvestmentHolding[]> {
  return unwrap(
    apiClient.get<InvestmentHolding[]>('/investment-holdings', { params: { accountId } }),
  )
}

export async function getInvestmentHolding(id: string): Promise<InvestmentHolding> {
  return unwrap(apiClient.get<InvestmentHolding>(`/investment-holdings/${id}`))
}

/** Adds a product to another account (holdings are always created explicitly, F022 spec). A `409`
 * is `InvestmentAccountRequiredException` or `InvestmentHoldingAlreadyExistsException`
 * (F031/ADR 0026, see `PROBLEM_TYPE_MESSAGES` in `apiError.ts`). */
export async function createInvestmentHolding(
  request: CreateInvestmentHoldingRequest,
): Promise<InvestmentHolding> {
  return unwrap(apiClient.post<InvestmentHolding>('/investment-holdings', request))
}

export async function editInvestmentHoldingNotes(
  id: string,
  request: UpdateInvestmentHoldingRequest,
): Promise<InvestmentHolding> {
  return unwrap(apiClient.patch<InvestmentHolding>(`/investment-holdings/${id}`, request))
}

/** Closes a holding. Not reversible through the UI. A `409` is
 * `InvestmentHoldingAlreadyClosedException` or `InvestmentHoldingNotEmptyException`
 * (F031/ADR 0026). */
export async function closeInvestmentHolding(id: string): Promise<InvestmentHolding> {
  return unwrap(apiClient.post<InvestmentHolding>(`/investment-holdings/${id}/close`))
}

/** Deletes a holding; only succeeds with zero history (a `409` means close it instead,
 * `InvestmentHoldingHasHistoryException`, F031/ADR 0026). */
export async function deleteInvestmentHolding(id: string): Promise<void> {
  await unwrap(apiClient.delete<void>(`/investment-holdings/${id}`))
}
