import { http, HttpResponse } from 'msw'
import type {
  RecordSnapshotSheetRequest,
  SnapshotSheetRow,
} from '../../api/investments/products/investmentSnapshotSheet'
import type { InvestmentSnapshot } from '../../api/investments/products/investmentSnapshots'
import { accountsStore } from './accounts'
import { investmentHoldingsStore } from './investmentHoldings'
import { investmentProductsStore } from './investmentProducts'
import { investmentSnapshotsStore } from './investmentSnapshots'

const SHEET_URL = '/api/investment-snapshot-sheet'

/**
 * Default success-path handlers for the snapshot sheet (F040's REST API), over the holding, product,
 * account and snapshot stores so a save shows up in the next `GET`, as the real refetch does:
 *
 * - `GET` lists the open holdings (closed ones left out) joined to their product and account names,
 *   ordered by account then product, each with its latest snapshot.
 * - `POST` answers `404` (`investment-snapshot-sheet-holdings-not-found`) with one
 *   `entries[i].holdingId` error per holding that is not in the store, writing nothing; otherwise it
 *   records every entry (a same-day snapshot is replaced) and answers `{ created, replaced }`.
 *
 * A test that needs a `409`, or a `400`, overrides the handler.
 */
export const investmentSnapshotSheetHandlers = [
  http.get(SHEET_URL, () => {
    const rows: SnapshotSheetRow[] = investmentHoldingsStore
      .list()
      .filter((holding) => !holding.closed)
      .map((holding) => {
        const product = investmentProductsStore.find(holding.productId)
        const account = accountsStore.find(holding.accountId)
        return {
          holdingId: holding.id,
          productId: holding.productId,
          productName: product?.name ?? '',
          ticker: product?.ticker ?? null,
          accountId: holding.accountId,
          accountName: account?.name ?? '',
          latestSnapshot: holding.latestSnapshot,
        }
      })
      .sort(
        (a, b) =>
          a.accountName.localeCompare(b.accountName) || a.productName.localeCompare(b.productName),
      )
    return HttpResponse.json(rows)
  }),

  http.post(SHEET_URL, async ({ request }) => {
    const body = (await request.json()) as RecordSnapshotSheetRequest
    const missing = body.entries
      .map((entry, index) => ({ entry, index }))
      .filter(({ entry }) => !investmentHoldingsStore.find(entry.holdingId))
    if (missing.length > 0) {
      return HttpResponse.json(
        {
          type: 'urn:my-finances:error:investment-snapshot-sheet-holdings-not-found',
          status: 404,
          errors: missing.map(({ index }) => ({
            field: `entries[${index}].holdingId`,
            code: 'investment-holding-not-found',
          })),
        },
        { status: 404 },
      )
    }
    let created = 0
    let replaced = 0
    for (const { holdingId, balance } of body.entries) {
      const existing = investmentSnapshotsStore
        .list()
        .find((s) => s.holdingId === holdingId && s.date === body.date)
      const snapshot: InvestmentSnapshot = {
        id: existing?.id ?? investmentSnapshotsStore.nextId('isnap'),
        holdingId,
        date: body.date,
        balance,
      }
      if (existing) {
        investmentSnapshotsStore.replace(existing.id, () => snapshot)
        replaced += 1
      } else {
        investmentSnapshotsStore.add(snapshot)
        created += 1
      }
      investmentHoldingsStore.replace(holdingId, (holding) =>
        !holding.latestSnapshot || holding.latestSnapshot.date <= body.date
          ? {
              ...holding,
              hasHistory: true,
              needsSnapshot: false,
              latestSnapshot: { date: body.date, balance },
            }
          : holding,
      )
    }
    return HttpResponse.json({ created, replaced })
  }),
]
