import { apiClient } from '../core/client'
import { unwrap } from '../core/apiError'
import type { components } from '../generated/schema'

/**
 * An investment snapshot as returned by the API (PRD S5.8, F009, rekeyed by holding for
 * F022/ADR 0020): the manually entered value of a holding on a date, and the sole source of its
 * current value. One per holding per date.
 */
export interface InvestmentSnapshot {
  id: string
  holdingId: string
  date: string
  balance: number
}

export type RecordSnapshotRequest = components['schemas']['RecordSnapshotRequest']
export type UpdateSnapshotRequest = components['schemas']['UpdateSnapshotRequest']

/** A holding's snapshots, most recent first. */
export async function getInvestmentSnapshots(holdingId: string): Promise<InvestmentSnapshot[]> {
  return unwrap(apiClient.get<InvestmentSnapshot[]>(`/investment-holdings/${holdingId}/snapshots`))
}

/**
 * Records the holding's value on a date; a second entry for the same day replaces the first (the
 * backend answers `201` for a new snapshot and `200` for a replacement, the body is the snapshot
 * either way). `balance` may be `0` - a liquidated position.
 */
export async function recordInvestmentSnapshot(
  holdingId: string,
  request: RecordSnapshotRequest,
): Promise<InvestmentSnapshot> {
  return unwrap(
    apiClient.post<InvestmentSnapshot>(`/investment-holdings/${holdingId}/snapshots`, request),
  )
}

/**
 * Edits a snapshot's date and balance. `409` when the new date already holds another snapshot of
 * the holding, or when the holding is closed and the edit would leave its latest snapshot non-zero
 * (the backend sends no text and one status for both, so the message names both).
 */
export async function updateInvestmentSnapshot(
  holdingId: string,
  snapshotId: string,
  request: UpdateSnapshotRequest,
): Promise<InvestmentSnapshot> {
  return unwrap(
    apiClient.put<InvestmentSnapshot>(
      `/investment-holdings/${holdingId}/snapshots/${snapshotId}`,
      request,
    ),
    'Could not save: another snapshot already has that date, or the holding is closed and this would leave it with a non-zero latest snapshot.',
  )
}

/**
 * Deletes a snapshot. `409` when the holding is closed and the remaining latest snapshot would be
 * non-zero.
 */
export async function deleteInvestmentSnapshot(
  holdingId: string,
  snapshotId: string,
): Promise<void> {
  await unwrap(
    apiClient.delete<void>(`/investment-holdings/${holdingId}/snapshots/${snapshotId}`),
    'Could not delete: the holding is closed and its latest snapshot would no longer be zero.',
  )
}
