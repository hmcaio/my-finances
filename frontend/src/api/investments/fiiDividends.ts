import { apiClient } from '../core/client'
import { unwrap } from '../core/apiError'

/** One dividend transaction (F026, ADR 0023), with its ticker joined in. */
export interface DividendRow {
  transactionId: string
  date: string
  amount: number
  investmentHoldingId: string
  productId: string | null
  ticker: string | null
  productName: string | null
  description: string
}

export interface DividendTotalByTicker {
  productId: string | null
  ticker: string | null
  amount: number
}

export interface DividendTotalByMonth {
  /** `yyyy-MM`. */
  month: string
  amount: number
}

/** Optional filter dimensions for {@link getDividends}. */
export interface DividendFilter {
  productId?: string
  from?: string
  to?: string
}

export async function getDividends(filter: DividendFilter = {}): Promise<DividendRow[]> {
  return unwrap(apiClient.get<DividendRow[]>('/fii/dividends', { params: filter }))
}

export async function getDividendTotalsByTicker(
  filter: Omit<DividendFilter, 'productId'> = {},
): Promise<DividendTotalByTicker[]> {
  return unwrap(
    apiClient.get<DividendTotalByTicker[]>('/fii/dividends/totals', {
      params: { ...filter, groupBy: 'TICKER' },
    }),
  )
}

export async function getDividendTotalsByMonth(
  filter: Omit<DividendFilter, 'productId'> = {},
): Promise<DividendTotalByMonth[]> {
  return unwrap(
    apiClient.get<DividendTotalByMonth[]>('/fii/dividends/totals', {
      params: { ...filter, groupBy: 'MONTH' },
    }),
  )
}
