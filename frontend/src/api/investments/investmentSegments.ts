import { apiClient } from '../core/client'
import { unwrap } from '../core/apiError'
import type { components } from '../generated/schema'

/**
 * An investment segment (F026, ADR 0023): a flat, user-editable taxonomy entry describing what
 * kind of real estate an FII holds (Shoppings, Logistica, Papel, ...). Orthogonal to the
 * category/sub-category taxonomy - generalized on `InvestmentProduct`, but only the FII page
 * manages it for v1.
 */
export interface InvestmentSegment {
  id: string
  name: string
}

/** Matches the backend's `TextFieldConstraints.MAX_NAME_LENGTH`, so inputs can cap what is typed. */
export const INVESTMENT_SEGMENT_NAME_MAX_LENGTH = 100

export type CreateInvestmentSegmentRequest = components['schemas']['CreateInvestmentSegmentRequest']
export type UpdateInvestmentSegmentRequest = components['schemas']['UpdateInvestmentSegmentRequest']

export async function getInvestmentSegments(): Promise<InvestmentSegment[]> {
  return unwrap(apiClient.get<InvestmentSegment[]>('/investment-segments'))
}

export async function createInvestmentSegment(
  request: CreateInvestmentSegmentRequest,
): Promise<InvestmentSegment> {
  return unwrap(apiClient.post<InvestmentSegment>('/investment-segments', request))
}

export async function renameInvestmentSegment(
  id: string,
  request: UpdateInvestmentSegmentRequest,
): Promise<InvestmentSegment> {
  return unwrap(apiClient.patch<InvestmentSegment>(`/investment-segments/${id}`, request))
}

/** Deletes a segment; a `409` means a product still references it
 * (`InvestmentSegmentInUseException`, F031/ADR 0026). */
export async function deleteInvestmentSegment(id: string): Promise<void> {
  await unwrap(apiClient.delete<void>(`/investment-segments/${id}`))
}
