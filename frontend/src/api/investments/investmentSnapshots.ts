import { apiClient } from '../core/client'
import { unwrap } from '../core/apiError'
import type { components } from '../generated/schema'

/**
 * An investment snapshot as returned by the API (PRD S5.8, F009): the manually entered value of a
 * product on a date, and the sole source of its current value. One per product per date.
 */
export interface InvestmentSnapshot {
  id: string
  productId: string
  date: string
  balance: number
}

export type RecordSnapshotRequest = components['schemas']['RecordSnapshotRequest']
export type UpdateSnapshotRequest = components['schemas']['UpdateSnapshotRequest']

/** A product's snapshots, most recent first. */
export async function getInvestmentSnapshots(productId: string): Promise<InvestmentSnapshot[]> {
  return unwrap(apiClient.get<InvestmentSnapshot[]>(`/investment-products/${productId}/snapshots`))
}

/**
 * Records the product's value on a date; a second entry for the same day replaces the first (the
 * backend answers `201` for a new snapshot and `200` for a replacement, the body is the snapshot
 * either way). `balance` may be `0` - a liquidated position.
 */
export async function recordInvestmentSnapshot(
  productId: string,
  request: RecordSnapshotRequest,
): Promise<InvestmentSnapshot> {
  return unwrap(
    apiClient.post<InvestmentSnapshot>(`/investment-products/${productId}/snapshots`, request),
  )
}

/**
 * Edits a snapshot's date and balance. `409` when the new date already holds another snapshot of
 * the product, or when the product is closed and the edit would leave its latest snapshot non-zero
 * (the backend sends no text and one status for both, so the message names both).
 */
export async function updateInvestmentSnapshot(
  productId: string,
  snapshotId: string,
  request: UpdateSnapshotRequest,
): Promise<InvestmentSnapshot> {
  return unwrap(
    apiClient.put<InvestmentSnapshot>(
      `/investment-products/${productId}/snapshots/${snapshotId}`,
      request,
    ),
    'Could not save: another snapshot already has that date, or the product is closed and this would leave it with a non-zero latest snapshot.',
  )
}

/**
 * Deletes a snapshot. `409` when the product is closed and the remaining latest snapshot would be
 * non-zero.
 */
export async function deleteInvestmentSnapshot(
  productId: string,
  snapshotId: string,
): Promise<void> {
  await unwrap(
    apiClient.delete<void>(`/investment-products/${productId}/snapshots/${snapshotId}`),
    'Could not delete: the product is closed and its latest snapshot would no longer be zero.',
  )
}
