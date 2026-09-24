import { apiClient } from './client'
import { unwrap } from './apiError'
import type { components } from './generated/schema'

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
