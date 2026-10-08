import { apiClient } from '../core/client'
import { unwrap } from '../core/apiError'

export type FiiAllocationBasis = 'ACTUAL' | 'PLANNED'
export type FiiAllocationGroupBy = 'TICKER' | 'SEGMENT'

/**
 * One slice of a FII allocation chart (F026, ADR 0023): either a product (grouped by ticker) or a
 * segment, with its percentage of the FII-only total. `key` is `null` for the "No segment" bucket.
 * `totalValue` is the slice's current value (basis `ACTUAL`); `null` for basis `PLANNED`, which
 * has no value of its own, only a target percentage. `segmentId` (Addendum - Nested Allocation
 * Charts) is set on a `TICKER`-groupBy row (its own segment, `null` if unsegmented), always `null`
 * on a `SEGMENT`-groupBy row (`key` already carries that there) - lets the frontend nest a ticker
 * chart's arcs under their parent segment's arc.
 */
export interface FiiAllocationRow {
  key: string | null
  label: string
  totalValue: number | null
  percentage: number
  segmentId: string | null
}

/**
 * `month` (`YYYY-MM`, defaults to the current month - Addendum: Month Selector) drives both bases:
 * `ACTUAL` converts it server-side to an as-of date (month-end, except the current month evaluated
 * at today); `PLANNED` resolves the allocation-plan version effective for that month.
 */
export async function getFiiAllocation(
  basis: FiiAllocationBasis,
  groupBy: FiiAllocationGroupBy,
  month?: string,
): Promise<FiiAllocationRow[]> {
  return unwrap(
    apiClient.get<FiiAllocationRow[]>('/fii/allocation', { params: { basis, groupBy, month } }),
  )
}
