import { http, HttpResponse } from 'msw'
import type { InvestmentSnapshot } from '../../api/investments/investmentSnapshots'
import { investmentProductsStore } from './investmentProducts'
import { createStore } from '../store'

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

const snapshots = createStore(seedBitcoinSnapshots)

/** Re-derives a product's `latestSnapshot`/`hasHistory` from its remaining snapshots. */
function syncLatestSnapshot(productId: string) {
  const latest = snapshots
    .list()
    .filter((s) => s.productId === productId)
    .sort((a, b) => (a.date < b.date ? 1 : -1))[0]
  investmentProductsStore.replace(productId, (product) => ({
    ...product,
    latestSnapshot: latest ? { date: latest.date, balance: latest.balance } : null,
  }))
}

/**
 * Default success-path handlers for the snapshot endpoints (F009's REST API), backed by an
 * in-memory store restored after each test: a POST answers `201` with the entry, or `200` when
 * the date matches an existing snapshot (the real endpoint's replace-in-place), and moves the
 * product's `latestSnapshot` when the entry is the newest. Products other than the seeded Bitcoin
 * one have no snapshots. PUT/DELETE edit or remove one (404 for an unknown snapshot, 409 for an
 * occupied date on edit) and keep the product's `latestSnapshot` in step; a test that needs the
 * closed-product 409 overrides the handler.
 */
export const investmentSnapshotsHandlers = [
  http.get(`${PRODUCTS_URL}/:productId/snapshots`, ({ params }) =>
    HttpResponse.json(
      snapshots
        .list()
        .filter((s) => s.productId === params.productId)
        .sort((a, b) => (a.date < b.date ? 1 : -1)),
    ),
  ),

  http.post(`${PRODUCTS_URL}/:productId/snapshots`, async ({ request, params }) => {
    const body = (await request.json()) as SnapshotRequestBody
    const productId = params.productId as string
    const existing = snapshots.list().find((s) => s.productId === productId && s.date === body.date)
    const snapshot: InvestmentSnapshot = {
      id: existing?.id ?? snapshots.nextId('isnap'),
      productId,
      date: body.date,
      balance: body.balance,
    }
    if (existing) snapshots.replace(existing.id, () => snapshot)
    else snapshots.add(snapshot)
    investmentProductsStore.replace(productId, (product) =>
      !product.latestSnapshot || product.latestSnapshot.date <= body.date
        ? {
            ...product,
            hasHistory: true,
            needsSnapshot: false,
            latestSnapshot: { date: body.date, balance: body.balance },
          }
        : product,
    )
    return HttpResponse.json(snapshot, { status: existing ? 200 : 201 })
  }),

  // Edit: 404 for an unknown snapshot or another product's, 409 when the date is taken by another
  // snapshot; otherwise replaces the row and re-derives the product's latestSnapshot.
  http.put(`${PRODUCTS_URL}/:productId/snapshots/:snapshotId`, async ({ request, params }) => {
    const body = (await request.json()) as SnapshotRequestBody
    const productId = params.productId as string
    const existing = snapshots.find(params.snapshotId as string)
    if (!existing || existing.productId !== productId)
      return new HttpResponse(null, { status: 404 })
    if (
      snapshots
        .list()
        .some((s) => s.productId === productId && s.id !== existing.id && s.date === body.date)
    )
      return new HttpResponse(null, { status: 409 })
    const updated: InvestmentSnapshot = { ...existing, date: body.date, balance: body.balance }
    snapshots.replace(existing.id, () => updated)
    syncLatestSnapshot(productId)
    return HttpResponse.json(updated)
  }),

  http.delete(`${PRODUCTS_URL}/:productId/snapshots/:snapshotId`, ({ params }) => {
    const productId = params.productId as string
    const existing = snapshots.find(params.snapshotId as string)
    if (!existing || existing.productId !== productId)
      return new HttpResponse(null, { status: 404 })
    snapshots.remove(existing.id)
    syncLatestSnapshot(productId)
    return new HttpResponse(null, { status: 204 })
  }),
]
