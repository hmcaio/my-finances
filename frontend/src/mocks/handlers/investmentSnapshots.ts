import { http, HttpResponse } from 'msw'
import type { InvestmentSnapshot } from '../../api/investmentSnapshots'

/**
 * Seed snapshots of the seeded Bitcoin product (`iprod-btc`, see `investmentProducts.ts`), most
 * recent first like the real endpoint. Exported so tests can assert against it.
 */
export const seedBitcoinSnapshots: InvestmentSnapshot[] = [
  { id: 'isnap-2', productId: 'iprod-btc', date: '2026-08-05', balance: 900 },
  { id: 'isnap-1', productId: 'iprod-btc', date: '2026-07-31', balance: 800 },
]

const PRODUCTS_URL = '/api/investment-products'

interface SnapshotRequestBody {
  date: string
  balance: number
}

/**
 * Default success-path handlers for the snapshot endpoints (F009's REST API), request-echoing like
 * the other aggregates' handlers: a POST answers `201` with the entry, or `200` when the date
 * matches an existing seed snapshot (the real endpoint's replace-in-place), without mutating the
 * seed. Products other than the seeded Bitcoin one have no snapshots.
 */
export const investmentSnapshotsHandlers = [
  http.get(`${PRODUCTS_URL}/:productId/snapshots`, ({ params }) =>
    HttpResponse.json(seedBitcoinSnapshots.filter((s) => s.productId === params.productId)),
  ),

  http.post(`${PRODUCTS_URL}/:productId/snapshots`, async ({ request, params }) => {
    const body = (await request.json()) as SnapshotRequestBody
    const existing = seedBitcoinSnapshots.find(
      (s) => s.productId === params.productId && s.date === body.date,
    )
    const snapshot: InvestmentSnapshot = {
      id: existing?.id ?? 'isnap-new',
      productId: params.productId as string,
      date: body.date,
      balance: body.balance,
    }
    return HttpResponse.json(snapshot, { status: existing ? 200 : 201 })
  }),
]
