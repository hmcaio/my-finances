import { apiClient } from '../../core/client'
import { unwrap } from '../../core/apiError'
import type { components } from '../../generated/schema'

/**
 * One row of the snapshot sheet (F040, PRD S6.6): an open holding with the names the sheet shows
 * and its most recent snapshot, `null` while it has none.
 */
export interface SnapshotSheetRow {
  holdingId: string
  productId: string
  productName: string
  ticker: string | null
  accountId: string
  accountName: string
  latestSnapshot: { date: string; balance: number } | null
}

export type RecordSnapshotSheetRequest = components['schemas']['RecordSnapshotSheetRequest']

/** How many snapshots the batch created and how many replaced a same-day one. */
export interface RecordSnapshotSheetResult {
  created: number
  replaced: number
}

/** Every open holding with its latest snapshot, ordered by account then product. */
export async function getSnapshotSheet(): Promise<SnapshotSheetRow[]> {
  return unwrap(apiClient.get<SnapshotSheetRow[]>('/investment-snapshot-sheet'))
}

/**
 * Records one snapshot per entry on `request.date`, all or none. `404`
 * (`investment-snapshot-sheet-holdings-not-found`) and `400` carry `ApiError.fieldErrors` with the
 * indexed `entries[i].holdingId` / `entries[i].balance` of every offending entry; `409`
 * (`investment-snapshot-date-taken`) is a concurrent write to one of the holdings and dates.
 */
export async function recordSnapshotSheet(
  request: RecordSnapshotSheetRequest,
): Promise<RecordSnapshotSheetResult> {
  return unwrap(apiClient.post<RecordSnapshotSheetResult>('/investment-snapshot-sheet', request))
}
