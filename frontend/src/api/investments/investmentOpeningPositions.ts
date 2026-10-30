import { ApiError, unwrap } from '../core/apiError'
import { apiClient } from '../core/client'
import type { components } from '../generated/schema'

/**
 * A holding's opening position as returned by the API (PRD S5.15, F038, ADR 0033): its starting
 * quantity and average unit price, recorded without moving cash. `amountContributed` is
 * `quantity x unitPrice`. The current value is not part of it - it lives in the holding's
 * snapshots.
 */
export interface OpeningPosition {
  id: string
  holdingId: string
  asOfDate: string
  quantity: number
  unitPrice: number
  amountContributed: number
}

export type OpeningPositionRequest = components['schemas']['OpeningPositionRequest']

/** The backend's problem `type` for "this holding has no opening position" (the empty state). */
export const OPENING_POSITION_NOT_FOUND_TYPE =
  'urn:my-finances:error:investment-opening-position-not-found'

/**
 * The holding's opening position, or `null` when it has none: that `404` problem type is the empty
 * state, not a failure (an unknown holding is a different type and still throws).
 */
export async function getOpeningPosition(holdingId: string): Promise<OpeningPosition | null> {
  try {
    return await unwrap(
      apiClient.get<OpeningPosition>(`/investment-holdings/${holdingId}/opening-position`),
    )
  } catch (err) {
    if (err instanceof ApiError && err.type === OPENING_POSITION_NOT_FOUND_TYPE) return null
    throw err
  }
}

/**
 * Creates the holding's opening position, or replaces the one it has (`201` vs `200`, the body is
 * the position either way). A `currentValue` also records the holding's snapshot dated `asOfDate`.
 * `409` for a closed holding or an as-of date after the holding's earliest trade; `400` for a
 * future as-of date.
 */
export async function putOpeningPosition(
  holdingId: string,
  request: OpeningPositionRequest,
): Promise<OpeningPosition> {
  return unwrap(
    apiClient.put<OpeningPosition>(`/investment-holdings/${holdingId}/opening-position`, request),
  )
}

/** Removes the position; snapshots are left alone. `409` for a closed holding. */
export async function deleteOpeningPosition(holdingId: string): Promise<void> {
  await unwrap(apiClient.delete<void>(`/investment-holdings/${holdingId}/opening-position`))
}
