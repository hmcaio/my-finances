import { apiClient } from './client'
import { unwrap } from './apiError'

/**
 * One month of a product's value series (F009 spec): raw data, nothing derived. `value` is `null`
 * before the first snapshot; `contributed` is that month's buys minus sells (cash moved, taxes
 * included); `units` is the running buys minus sells of quantity, `null` when the product has no
 * recorded quantities. `month` is `YYYY-MM`.
 */
export interface ValueSeriesPoint {
  month: string
  value: number | null
  contributed: number
  units: number | null
}

export interface ProductValueSeries {
  productId: string
  points: ValueSeriesPoint[]
}

export interface ValueSeriesParams {
  /** `YYYY-MM`; defaults to eleven months before `to` on the backend. */
  from?: string
  /** `YYYY-MM`; defaults to the current month. */
  to?: string
  /** Omitted: one series per product. */
  productId?: string
}

export async function getInvestmentValueSeries(
  params: ValueSeriesParams = {},
): Promise<ProductValueSeries[]> {
  return unwrap(apiClient.get<ProductValueSeries[]>('/investments/value-series', { params }))
}
