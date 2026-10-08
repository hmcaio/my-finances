import { http, HttpResponse } from 'msw'
import type { FiiPortfolioRow } from '../../api/investments/fiiPortfolio'
import type { InvestmentProductStatus } from '../../api/investments/investmentProducts'

const PORTFOLIO_URL = '/api/fii/portfolio'

/**
 * Seed data returned by the default `GET /api/fii/portfolio` handler below (F026, ADR 0023). Not
 * derived from the other investment stores (unlike the real backend) - a hand-picked fixture is
 * simpler for the FII page's own tests, same spirit as `seedCategorySpend`.
 */
export const seedFiiPortfolio: FiiPortfolioRow[] = [
  {
    productId: 'iprod-knri11',
    ticker: 'KNRI11',
    name: 'Kinea Renda Imobiliaria',
    segmentId: 'iseg-shoppings',
    cotasHeld: 100,
    amountContributed: 10000,
    currentValue: 12000,
    latestSnapshotDate: '2026-01-01',
    needsSnapshot: false,
    hasOpenHolding: true,
  },
  {
    productId: 'iprod-hglg11',
    ticker: 'HGLG11',
    name: 'CSHG Logistica',
    segmentId: 'iseg-logistica',
    cotasHeld: 50,
    amountContributed: 5000,
    currentValue: 5500,
    latestSnapshotDate: '2026-01-01',
    needsSnapshot: true,
    hasOpenHolding: true,
  },
]

export const fiiPortfolioHandlers = [
  http.get(PORTFOLIO_URL, ({ request }) => {
    const url = new URL(request.url)
    const status = (url.searchParams.get('status') ?? 'OPEN') as InvestmentProductStatus
    const rows = seedFiiPortfolio.filter((row) =>
      status === 'ALL' ? true : status === 'OPEN' ? row.hasOpenHolding : !row.hasOpenHolding,
    )
    return HttpResponse.json(rows)
  }),
]
