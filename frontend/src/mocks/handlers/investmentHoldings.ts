import { http, HttpResponse } from 'msw'
import type { InvestmentHolding } from '../../api/investments/investmentHoldings'
import { seedInvestmentAccount } from './accounts'
import { createStore } from '../store'

/**
 * Seed data returned by the default `GET /api/investment-holdings` handler below (F022 spec, ADR
 * 0020), all inside the seeded INVESTMENT account: "Tesouro Selic 2029" (`iprod-selic`) has no
 * history (deletable), "Bitcoin" (`iprod-btc`) has some (`hasHistory`, so close-only) and
 * `needsSnapshot` (a buy newer than its latest snapshot, see `seedBitcoinBuyTransfer` in
 * `transfers.ts`), and "Old CDB" (`iprod-old`) is closed.
 */
export const seedInvestmentHoldings: InvestmentHolding[] = [
  {
    id: 'iholding-selic',
    productId: 'iprod-selic',
    accountId: seedInvestmentAccount.id,
    closedDate: null,
    closed: false,
    additionalNotes: null,
    hasHistory: false,
    needsSnapshot: false,
    latestSnapshot: null,
  },
  {
    id: 'iholding-btc',
    productId: 'iprod-btc',
    accountId: seedInvestmentAccount.id,
    closedDate: null,
    closed: false,
    additionalNotes: null,
    hasHistory: true,
    needsSnapshot: true,
    latestSnapshot: { date: '2026-08-05', balance: 900 },
  },
  {
    id: 'iholding-old',
    productId: 'iprod-old',
    accountId: seedInvestmentAccount.id,
    closedDate: '2026-03-01',
    closed: true,
    additionalNotes: null,
    hasHistory: false,
    needsSnapshot: false,
    latestSnapshot: null,
  },
]

const HOLDINGS_URL = '/api/investment-holdings'

interface HoldingRequestBody {
  productId: string
  accountId: string
  additionalNotes?: string | null
}

/** Shared with the products/snapshots handlers, which create/move a holding's latest snapshot. */
export const investmentHoldingsStore = createStore(seedInvestmentHoldings)
const holdings = investmentHoldingsStore

/**
 * Default success-path handlers for the investment holdings endpoints (F022's REST API), backed
 * by an in-memory store restored after each test (see `categories.ts`).
 */
export const investmentHoldingsHandlers = [
  http.get(HOLDINGS_URL, ({ request }) => {
    const url = new URL(request.url)
    const productId = url.searchParams.get('productId')
    const accountId = url.searchParams.get('accountId')
    const rows = productId
      ? holdings.list().filter((h) => h.productId === productId)
      : accountId
        ? holdings.list().filter((h) => h.accountId === accountId)
        : holdings.list()
    return HttpResponse.json(rows)
  }),

  http.get(`${HOLDINGS_URL}/:id`, ({ params }) => {
    const holding = holdings.find(params.id as string)
    if (!holding) return new HttpResponse(null, { status: 404 })
    return HttpResponse.json(holding)
  }),

  http.post(HOLDINGS_URL, async ({ request }) => {
    const body = (await request.json()) as HoldingRequestBody
    const created = holdings.add({
      id: holdings.nextId('iholding'),
      productId: body.productId,
      accountId: body.accountId,
      closedDate: null,
      closed: false,
      additionalNotes: body.additionalNotes ?? null,
      hasHistory: false,
      needsSnapshot: false,
      latestSnapshot: null,
    })
    return HttpResponse.json(created, { status: 201 })
  }),

  http.patch(`${HOLDINGS_URL}/:id`, async ({ request, params }) => {
    const body = (await request.json()) as { additionalNotes?: string | null }
    const updated = holdings.replace(params.id as string, (row) => ({
      ...row,
      additionalNotes: body.additionalNotes ?? null,
    }))
    return updated ? HttpResponse.json(updated) : new HttpResponse(null, { status: 404 })
  }),

  http.post(`${HOLDINGS_URL}/:id/close`, ({ params }) => {
    const closed = holdings.replace(params.id as string, (row) => ({
      ...row,
      closedDate: '2026-09-15',
      closed: true,
    }))
    return closed ? HttpResponse.json(closed) : new HttpResponse(null, { status: 404 })
  }),

  http.delete(`${HOLDINGS_URL}/:id`, ({ params }) => {
    holdings.remove(params.id as string)
    return new HttpResponse(null, { status: 204 })
  }),
]

/**
 * `409` variants (applied via `server.use(...)`): the backend sends no message text, so the
 * bodies are placeholders and what the UI shows comes from the client's own `conflictMessage`.
 */
export const investmentHoldingDeleteConflictHandler = http.delete(`${HOLDINGS_URL}/:id`, () =>
  HttpResponse.json({ message: 'Holding has history' }, { status: 409 }),
)

export const investmentHoldingCloseConflictHandler = http.post(`${HOLDINGS_URL}/:id/close`, () =>
  HttpResponse.json({ message: 'Already closed' }, { status: 409 }),
)

/** Duplicate (product, account) pair, or a non-investment account. */
export const investmentHoldingCreateConflictHandler = http.post(HOLDINGS_URL, () =>
  HttpResponse.json({ message: 'Conflict' }, { status: 409 }),
)
