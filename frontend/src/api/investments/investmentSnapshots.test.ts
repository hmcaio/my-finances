import { describe, expect, it } from 'vitest'
import { http, HttpResponse } from 'msw'
import { server } from '../../mocks/server'
import { seedBitcoinSnapshots } from '../../mocks/handlers/investmentSnapshots'
import { getInvestmentSnapshots, recordInvestmentSnapshot } from './investmentSnapshots'

describe('investment snapshots API client', () => {
  it('getInvestmentSnapshots returns the product history, most recent first', async () => {
    await expect(getInvestmentSnapshots('iprod-btc')).resolves.toEqual(seedBitcoinSnapshots)
  })

  it('getInvestmentSnapshots of a product with none returns an empty list', async () => {
    await expect(getInvestmentSnapshots('iprod-selic')).resolves.toEqual([])
  })

  it('recordInvestmentSnapshot posts date and balance to the product and returns the snapshot', async () => {
    let sentBody: unknown = null
    let sentUrl = ''
    server.use(
      http.post('/api/investment-products/:productId/snapshots', async ({ request }) => {
        sentBody = await request.json()
        sentUrl = new URL(request.url).pathname
        return HttpResponse.json(
          { id: 'isnap-x', productId: 'iprod-selic', date: '2026-09-01', balance: 0 },
          { status: 201 },
        )
      }),
    )

    const snapshot = await recordInvestmentSnapshot('iprod-selic', {
      date: '2026-09-01',
      balance: 0,
    })

    expect(sentUrl).toBe('/api/investment-products/iprod-selic/snapshots')
    expect(sentBody).toEqual({ date: '2026-09-01', balance: 0 })
    expect(snapshot).toMatchObject({ productId: 'iprod-selic', date: '2026-09-01', balance: 0 })
  })

  it('recordInvestmentSnapshot on an existing date returns the replaced snapshot', async () => {
    const snapshot = await recordInvestmentSnapshot('iprod-btc', {
      date: seedBitcoinSnapshots[0].date,
      balance: 950,
    })

    expect(snapshot).toMatchObject({ id: seedBitcoinSnapshots[0].id, balance: 950 })
  })
})
