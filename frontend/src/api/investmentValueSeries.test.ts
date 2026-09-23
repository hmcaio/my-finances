import { describe, expect, it } from 'vitest'
import { http, HttpResponse } from 'msw'
import { server } from '../mocks/server'
import { seedBitcoinSeries } from '../mocks/handlers/investmentValueSeries'
import { getInvestmentValueSeries } from './investmentValueSeries'

describe('investment value series API client', () => {
  it('returns one series per product when no product is given', async () => {
    await expect(getInvestmentValueSeries()).resolves.toEqual([seedBitcoinSeries])
  })

  it('passes from, to and productId as query params and returns the points', async () => {
    let sent: Record<string, string> = {}
    server.use(
      http.get('/api/investments/value-series', ({ request }) => {
        sent = Object.fromEntries(new URL(request.url).searchParams)
        return HttpResponse.json([seedBitcoinSeries])
      }),
    )

    const [series] = await getInvestmentValueSeries({
      from: '2026-06',
      to: '2026-08',
      productId: 'iprod-btc',
    })

    expect(sent).toEqual({ from: '2026-06', to: '2026-08', productId: 'iprod-btc' })
    expect(series.points).toHaveLength(3)
    // Null before the first snapshot, raw contributions and units afterwards.
    expect(series.points[0].value).toBeNull()
    expect(series.points[2]).toEqual({
      month: '2026-08',
      value: 900,
      contributed: 500,
      units: 0.01,
    })
  })
})
