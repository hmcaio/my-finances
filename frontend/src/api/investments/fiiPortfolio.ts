import { apiClient } from '../core/client'
import { unwrap } from '../core/apiError'
import type { InvestmentProductStatus } from './investmentProducts'

/**
 * One row of the FII portfolio summary (F026, ADR 0023): a product classified under the "REITs
 * (FIIs)" sub-category, aggregated across every one of its holdings/accounts. `cotasHeld`/
 * `amountContributed` are a running total over every tagged trade to date - the same computed-not-
 * stored style F009's value series already uses for `units`/`contributed`.
 */
export interface FiiPortfolioRow {
  productId: string
  ticker: string | null
  name: string
  segmentId: string | null
  cotasHeld: number
  amountContributed: number
  currentValue: number
  /** `null` while the product has no snapshot yet. */
  latestSnapshotDate: string | null
  needsSnapshot: boolean
  hasOpenHolding: boolean
}

/**
 * Fetches the FII portfolio summary, filtered by holding status (defaults to `OPEN`), as of
 * `month` (`YYYY-MM`, defaults to the current month - Addendum: Month Selector). A past month's
 * trades/snapshots on or before its as-of date (computed server-side); `needsSnapshot` is always
 * `false` for a past month, never recomputed against it.
 */
export async function getFiiPortfolio(
  status: InvestmentProductStatus = 'OPEN',
  month?: string,
): Promise<FiiPortfolioRow[]> {
  return unwrap(apiClient.get<FiiPortfolioRow[]>('/fii/portfolio', { params: { status, month } }))
}
