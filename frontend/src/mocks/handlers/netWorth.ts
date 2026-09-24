import { http, HttpResponse } from 'msw'
import type { NetWorthPoint } from '../../api/netWorth'

/**
 * Seed trend: a checking balance falling with a credit card spend, then an investment snapshot.
 * Month-end dates so the same rows serve both granularities in tests. Exported so tests can assert
 * against them.
 */
export const seedNetWorthTrend: NetWorthPoint[] = [
  { date: '2026-06-30', netWorth: 1000, assets: 1000, liabilities: 0, investments: 0 },
  { date: '2026-07-31', netWorth: 900, assets: 1000, liabilities: 100, investments: 0 },
  { date: '2026-08-31', netWorth: 1700, assets: 1000, liabilities: 100, investments: 800 },
]

export const netWorthHandlers = [
  http.get('/api/net-worth', ({ request }) => {
    const asOf = new URL(request.url).searchParams.get('asOf')
    const latest = seedNetWorthTrend[seedNetWorthTrend.length - 1]
    return HttpResponse.json({ ...latest, date: asOf ?? latest.date })
  }),
  http.get('/api/net-worth/trend', () => HttpResponse.json(seedNetWorthTrend)),
]
