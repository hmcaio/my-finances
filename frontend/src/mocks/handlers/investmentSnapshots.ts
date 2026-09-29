import { http, HttpResponse } from 'msw'
import type { InvestmentSnapshot } from '../../api/investments/investmentSnapshots'
import { investmentHoldingsStore } from './investmentHoldings'
import { createStore } from '../store'

/**
 * Seed snapshots of the seeded Bitcoin holding (`iholding-btc`, see `investmentHoldings.ts`), most
 * recent first like the real endpoint. Exported so tests can assert against it.
 */
export const seedBitcoinSnapshots: InvestmentSnapshot[] = [
  { id: 'isnap-2', holdingId: 'iholding-btc', date: '2026-08-05', balance: 900 },
  { id: 'isnap-1', holdingId: 'iholding-btc', date: '2026-07-31', balance: 800 },
]

const HOLDINGS_URL = '/api/investment-holdings'

interface SnapshotRequestBody {
  date: string
  balance: number
}

const snapshots = createStore(seedBitcoinSnapshots)

/** Re-derives a holding's `latestSnapshot`/`hasHistory` from its remaining snapshots. */
function syncLatestSnapshot(holdingId: string) {
  const latest = snapshots
    .list()
    .filter((s) => s.holdingId === holdingId)
    .sort((a, b) => (a.date < b.date ? 1 : -1))[0]
  investmentHoldingsStore.replace(holdingId, (holding) => ({
    ...holding,
    latestSnapshot: latest ? { date: latest.date, balance: latest.balance } : null,
  }))
}

/**
 * Default success-path handlers for the snapshot endpoints (F009's REST API, moved to the holding
 * by F022/ADR 0020), backed by an in-memory store restored after each test: a POST answers `201`
 * with the entry, or `200` when the date matches an existing snapshot (the real endpoint's
 * replace-in-place), and moves the holding's `latestSnapshot` when the entry is the newest.
 * Holdings other than the seeded Bitcoin one have no snapshots. PUT/DELETE edit or remove one (404
 * for an unknown snapshot, 409 for an occupied date on edit) and keep the holding's
 * `latestSnapshot` in step; a test that needs the closed-holding 409 overrides the handler.
 */
export const investmentSnapshotsHandlers = [
  http.get(`${HOLDINGS_URL}/:holdingId/snapshots`, ({ params }) =>
    HttpResponse.json(
      snapshots
        .list()
        .filter((s) => s.holdingId === params.holdingId)
        .sort((a, b) => (a.date < b.date ? 1 : -1)),
    ),
  ),

  http.post(`${HOLDINGS_URL}/:holdingId/snapshots`, async ({ request, params }) => {
    const body = (await request.json()) as SnapshotRequestBody
    const holdingId = params.holdingId as string
    const existing = snapshots.list().find((s) => s.holdingId === holdingId && s.date === body.date)
    const snapshot: InvestmentSnapshot = {
      id: existing?.id ?? snapshots.nextId('isnap'),
      holdingId,
      date: body.date,
      balance: body.balance,
    }
    if (existing) snapshots.replace(existing.id, () => snapshot)
    else snapshots.add(snapshot)
    investmentHoldingsStore.replace(holdingId, (holding) =>
      !holding.latestSnapshot || holding.latestSnapshot.date <= body.date
        ? {
            ...holding,
            hasHistory: true,
            needsSnapshot: false,
            latestSnapshot: { date: body.date, balance: body.balance },
          }
        : holding,
    )
    return HttpResponse.json(snapshot, { status: existing ? 200 : 201 })
  }),

  // Edit: 404 for an unknown snapshot or another holding's, 409 when the date is taken by another
  // snapshot; otherwise replaces the row and re-derives the holding's latestSnapshot.
  http.put(`${HOLDINGS_URL}/:holdingId/snapshots/:snapshotId`, async ({ request, params }) => {
    const body = (await request.json()) as SnapshotRequestBody
    const holdingId = params.holdingId as string
    const existing = snapshots.find(params.snapshotId as string)
    if (!existing || existing.holdingId !== holdingId)
      return new HttpResponse(null, { status: 404 })
    if (
      snapshots
        .list()
        .some((s) => s.holdingId === holdingId && s.id !== existing.id && s.date === body.date)
    )
      return new HttpResponse(null, { status: 409 })
    const updated: InvestmentSnapshot = { ...existing, date: body.date, balance: body.balance }
    snapshots.replace(existing.id, () => updated)
    syncLatestSnapshot(holdingId)
    return HttpResponse.json(updated)
  }),

  http.delete(`${HOLDINGS_URL}/:holdingId/snapshots/:snapshotId`, ({ params }) => {
    const holdingId = params.holdingId as string
    const existing = snapshots.find(params.snapshotId as string)
    if (!existing || existing.holdingId !== holdingId)
      return new HttpResponse(null, { status: 404 })
    snapshots.remove(existing.id)
    syncLatestSnapshot(holdingId)
    return new HttpResponse(null, { status: 204 })
  }),
]
