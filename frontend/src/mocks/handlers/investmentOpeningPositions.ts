import { http, HttpResponse } from 'msw'
import type {
  OpeningPosition,
  OpeningPositionRequest,
} from '../../api/investments/investmentOpeningPositions'
import { investmentHoldingsStore } from './investmentHoldings'
import { investmentSnapshotsStore } from './investmentSnapshots'
import { createStore } from '../store'

/**
 * A position for the seeded Tesouro Selic holding (`iholding-selic`): 40 at 150.00 as of
 * 2026-07-01. Kept out of the default seed (no holding has a position until a test adds it, via
 * `investmentOpeningPositionsStore.add(...)` or a PUT), so pages that list holdings keep their
 * exact-state assertions (`docs/conventions/frontend-testing.md`).
 */
export const seedOpeningPosition: OpeningPosition = {
  id: 'iopen-1',
  holdingId: 'iholding-selic',
  asOfDate: '2026-07-01',
  quantity: 40,
  unitPrice: 150,
  amountContributed: 6000,
}

/** Shared with the products handler, whose create can carry an `openingPosition`. */
export const investmentOpeningPositionsStore = createStore<OpeningPosition>([])

const URL = '/api/investment-holdings/:holdingId/opening-position'
const PROBLEM_PREFIX = 'urn:my-finances:error:'

type PutBody = OpeningPositionRequest

/**
 * Stores a position for a holding the way the backend writes it: replaces the holding's existing
 * one in place, and with a `currentValue` records the snapshot dated `asOfDate` and moves the
 * holding's `latestSnapshot` (without one the holding is flagged as needing a snapshot).
 * Returns the stored position and whether it was created.
 */
export function savePosition(
  holdingId: string,
  body: PutBody,
): { position: OpeningPosition; created: boolean } {
  const existing = investmentOpeningPositionsStore.list().find((p) => p.holdingId === holdingId)
  const position: OpeningPosition = {
    id: existing?.id ?? investmentOpeningPositionsStore.nextId('iopen'),
    holdingId,
    asOfDate: body.asOfDate,
    quantity: body.quantity,
    unitPrice: body.unitPrice,
    amountContributed: body.quantity * body.unitPrice,
  }
  if (existing) investmentOpeningPositionsStore.replace(existing.id, () => position)
  else investmentOpeningPositionsStore.add(position)

  if (body.currentValue !== undefined && body.currentValue !== null) {
    const sameDay = investmentSnapshotsStore
      .list()
      .find((s) => s.holdingId === holdingId && s.date === body.asOfDate)
    if (sameDay) {
      investmentSnapshotsStore.replace(sameDay.id, (s) => ({ ...s, balance: body.currentValue! }))
    } else {
      investmentSnapshotsStore.add({
        id: investmentSnapshotsStore.nextId('isnap'),
        holdingId,
        date: body.asOfDate,
        balance: body.currentValue,
      })
    }
    investmentHoldingsStore.replace(holdingId, (h) => ({
      ...h,
      hasHistory: true,
      needsSnapshot: false,
      latestSnapshot: { date: body.asOfDate, balance: body.currentValue! },
    }))
  } else {
    investmentHoldingsStore.replace(holdingId, (h) => ({
      ...h,
      hasHistory: true,
      needsSnapshot: !h.latestSnapshot || h.latestSnapshot.date < body.asOfDate,
    }))
  }
  return { position, created: !existing }
}

function problem(type: string, status: number) {
  return HttpResponse.json({ type: `${PROBLEM_PREFIX}${type}` }, { status })
}

/**
 * Default success-path handlers for the opening-position endpoints (F038, ADR 0033), backed by an
 * in-memory store restored after each test: GET answers the not-found problem type (the empty
 * state) when the holding has none, an unknown holding is `investment-holding-not-found`; PUT
 * creates (`201`) or replaces (`200`); DELETE removes (`204`). A closed holding answers `409`
 * `investment-holding-closed` on PUT/DELETE, as the backend does. A test that needs the
 * as-of-date-after-trades `409` overrides the handler (`openingPositionAfterTradesHandler`).
 */
export const investmentOpeningPositionsHandlers = [
  http.get(URL, ({ params }) => {
    const holdingId = params.holdingId as string
    if (!investmentHoldingsStore.find(holdingId))
      return problem('investment-holding-not-found', 404)
    const position = investmentOpeningPositionsStore.list().find((p) => p.holdingId === holdingId)
    return position
      ? HttpResponse.json(position)
      : problem('investment-opening-position-not-found', 404)
  }),

  http.put(URL, async ({ request, params }) => {
    const holdingId = params.holdingId as string
    const holding = investmentHoldingsStore.find(holdingId)
    if (!holding) return problem('investment-holding-not-found', 404)
    if (holding.closed) return problem('investment-holding-closed', 409)
    const { position, created } = savePosition(holdingId, (await request.json()) as PutBody)
    return HttpResponse.json(position, { status: created ? 201 : 200 })
  }),

  http.delete(URL, ({ params }) => {
    const holdingId = params.holdingId as string
    const holding = investmentHoldingsStore.find(holdingId)
    if (!holding) return problem('investment-holding-not-found', 404)
    const position = investmentOpeningPositionsStore.list().find((p) => p.holdingId === holdingId)
    if (!position) return problem('investment-opening-position-not-found', 404)
    if (holding.closed) return problem('investment-holding-closed', 409)
    investmentOpeningPositionsStore.remove(position.id)
    return new HttpResponse(null, { status: 204 })
  }),
]

/** `409` variant of PUT: the as-of date is after the holding's earliest trade. */
export const openingPositionAfterTradesHandler = http.put(URL, () =>
  problem('opening-position-after-trades', 409),
)
