import { apiClient } from '../core/client'
import { unwrap } from '../core/apiError'

export type FiiAllocationBasis = 'ACTUAL' | 'PLANNED'
export type FiiAllocationGroupBy = 'TICKER' | 'SEGMENT'

/**
 * One slice of a FII allocation chart (F026, ADR 0023): either a product (grouped by ticker) or a
 * segment, with its percentage of the FII-only total. `key` is `null` for the "No segment" bucket.
 * `totalValue` is the slice's current value (basis `ACTUAL`); `null` for basis `PLANNED`, which
 * has no value of its own, only a target percentage.
 */
export interface FiiAllocationRow {
  key: string | null
  label: string
  totalValue: number | null
  percentage: number
}

export async function getFiiAllocation(
  basis: FiiAllocationBasis,
  groupBy: FiiAllocationGroupBy,
): Promise<FiiAllocationRow[]> {
  return unwrap(
    apiClient.get<FiiAllocationRow[]>('/fii/allocation', { params: { basis, groupBy } }),
  )
}
