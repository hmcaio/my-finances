import { apiClient } from '../core/client'
import { unwrap } from '../core/apiError'

export type AllocationGrouping = 'CATEGORY' | 'SUBCATEGORY'

/**
 * One slice of the allocation view (PRD S6.6, F009): the latest snapshot per product, summed per
 * group. With `CATEGORY` grouping the sub-category fields are always `null`; with `SUBCATEGORY`
 * they are `null` only for the slice of products that have no sub-category. `needsSnapshot` is
 * true when any product in the slice has a trade newer than its latest snapshot.
 */
export interface AllocationRow {
  categoryId: string
  categoryName: string
  subcategoryId: string | null
  subcategoryName: string | null
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
