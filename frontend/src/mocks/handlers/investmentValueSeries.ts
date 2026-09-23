import { http, HttpResponse } from 'msw'
import type { ProductValueSeries } from '../../api/investmentValueSeries'

/**
 * Seed series of the seeded Bitcoin product (`iprod-btc`) over June-August 2026: no snapshot yet in
 * June (`value: null`), the buy in August as a contribution, units recorded by the buy. Exported so
 * tests can assert against it.
 */
export const seedBitcoinSeries: ProductValueSeries = {
  productId: 'iprod-btc',
  points: [
    { month: '2026-06', value: null, contributed: 0, units: 0 },
    { month: '2026-07', value: 800, contributed: 0, units: 0 },
    { month: '2026-08', value: 900, contributed: 500, units: 0.01 },
  ],
}

/** A series for any other product: nothing recorded (all values null, no units). */
function emptySeries(productId: string): ProductValueSeries {
  return {
    productId,
    points: seedBitcoinSeries.points.map((p) => ({
      month: p.month,
      value: null,
      contributed: 0,
      units: null,
    })),
  }
}

export const investmentValueSeriesHandlers = [
  http.get('/api/investments/value-series', ({ request }) => {
    const productId = new URL(request.url).searchParams.get('productId')
    if (!productId) return HttpResponse.json([seedBitcoinSeries])
    return HttpResponse.json([
      productId === seedBitcoinSeries.productId ? seedBitcoinSeries : emptySeries(productId),
    ])
  }),
]
