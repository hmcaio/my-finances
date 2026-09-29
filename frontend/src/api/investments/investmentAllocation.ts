import { apiClient } from '../core/client'
import { unwrap } from '../core/apiError'

export type AllocationGrouping = 'CATEGORY' | 'SUBCATEGORY' | 'ACCOUNT'

/**
 * One slice of the allocation view (PRD S6.6, F009; `ACCOUNT` grouping added by F023): the latest
 * snapshot per product (or, for `ACCOUNT`, per holding), summed per group. With `CATEGORY` grouping
 * the sub-category fields are always `null`; with `SUBCATEGORY` they are `null` only for the slice
 * of products that have no sub-category; with `ACCOUNT` the category/sub-category fields are always
 * `null` and `accountId`/`accountName` are populated instead. `needsSnapshot` is true when any
 * product (or, for `ACCOUNT`, any holding) in the slice has a trade newer than its latest snapshot.
 */
export interface AllocationRow {
  categoryId: string | null
  categoryName: string | null
  subcategoryId: string | null
  subcategoryName: string | null
  accountId: string | null
  accountName: string | null
  totalValue: number
  needsSnapshot: boolean
}

export interface AllocationParams {
  /** `YYYY-MM-DD`; defaults to today on the backend. */
  asOf?: string
  /** Defaults to `CATEGORY` on the backend. */
  groupBy?: AllocationGrouping
}

export async function getInvestmentAllocation(
  params: AllocationParams = {},
): Promise<AllocationRow[]> {
  return unwrap(apiClient.get<AllocationRow[]>('/investments/allocation', { params }))
}
