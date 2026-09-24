import { apiClient } from './client'
import { unwrap } from './apiError'

export type NetWorthGranularity = 'CHANGE_DATE' | 'MONTH'

/**
 * Net worth on one date (PRD S5.9, F010), computed on read. `assets` is checking/savings/cash,
 * `investments` the `INVESTMENT` accounts and `liabilities` the amount owed on credit cards (a
 * positive number); `netWorth = assets + investments - liabilities`. `date` is `YYYY-MM-DD`.
 */
export interface NetWorthPoint {
  date: string
  netWorth: number
  assets: number
  liabilities: number
  investments: number
}

export interface NetWorthTrendParams {
  /** `YYYY-MM-DD`; defaults to twelve months before `to` on the backend. */
  from?: string
  /** `YYYY-MM-DD`; defaults to today. The backend never reports dates after today. */
  to?: string
  /** Defaults to `CHANGE_DATE` on the backend. */
  granularity?: NetWorthGranularity
}

/** Net worth as of `asOf` (`YYYY-MM-DD`; defaults to today on the backend). */
export async function getNetWorth(asOf?: string): Promise<NetWorthPoint> {
  return unwrap(apiClient.get<NetWorthPoint>('/net-worth', { params: { asOf } }))
}

export async function getNetWorthTrend(params: NetWorthTrendParams = {}): Promise<NetWorthPoint[]> {
  return unwrap(apiClient.get<NetWorthPoint[]>('/net-worth/trend', { params }))
}
