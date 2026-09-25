import { http, HttpResponse } from 'msw'
import type { Transfer } from '../../api/transfers/transfers'
import { createStore } from '../store'

/**
 * Seed data returned by the default `GET /api/transfers` handler below. Spans both seed accounts
 * (`acct-1` open, `acct-2` closed - see `accounts.ts`) so filter tests have something to
 * discriminate on. Exported so tests can assert against it directly (F015 spec's F002 backfill
 * pattern).
 */
export const seedTransfers: Transfer[] = [
  {
    id: 'trf-1',
    date: '2026-01-10',
    fromAccountId: 'acct-1',
    toAccountId: 'acct-3',
    amount: 200,
    description: 'Credit card payment',
    additionalNotes: null,
    investmentProductId: null,
    quantity: null,
    unitPrice: null,
    taxes: null,
  },
  {
    id: 'trf-2',
    date: '2026-01-25',
    fromAccountId: 'acct-3',
    toAccountId: 'acct-1',
    amount: 50,
    description: 'Refund',
    additionalNotes: 'Overpaid last month',
    investmentProductId: null,
    quantity: null,
    unitPrice: null,
    taxes: null,
  },
]

/**
 * A buy of the seeded Bitcoin product (F009): checking into the seeded INVESTMENT account with
 * quantity, unit price and taxes. Kept OUT of `seedTransfers` - that list feeds the general list
 * tests, where a tagged transfer would be a surprise - and served only when the list is filtered by
 * its product (`investmentProductId=iprod-btc`).
 */
export const seedBitcoinBuyTransfer: Transfer = {
  id: 'trf-btc-buy',
  date: '2026-08-10',
  fromAccountId: 'acct-1',
  toAccountId: 'acct-inv',
  amount: 1005,
  description: 'Buy Bitcoin',
  additionalNotes: null,
  investmentProductId: 'iprod-btc',
  quantity: 0.01,
  unitPrice: 100000,
  taxes: 5,
}

/** The matching sell (out of the INVESTMENT account, no trade details recorded). */
export const seedBitcoinSellTransfer: Transfer = {
  id: 'trf-btc-sell',
  date: '2026-08-20',
  fromAccountId: 'acct-inv',
  toAccountId: 'acct-1',
  amount: 500,
  description: 'Sell some Bitcoin',
  additionalNotes: null,
  investmentProductId: 'iprod-btc',
  quantity: null,
  unitPrice: null,
  taxes: null,
}

const seedProductTrades: Transfer[] = [seedBitcoinBuyTransfer, seedBitcoinSellTransfer]

/**
 * Named lookup for the seeded credit-card-payment transfer (`trf-1`), so call sites identify it by
 * name instead of indexing into `seedTransfers` by position (frontend test audit's F6 finding).
 */
export const seedCreditCardPaymentTransfer = seedTransfers.find(
  (t) => t.description === 'Credit card payment',
)!

const TRANSFERS_URL = '/api/transfers'

interface TransferRequestBody {
  date: string
  fromAccountId: string
  toAccountId: string
  amount: number
  description: string
  additionalNotes?: string
  investmentProductId?: string
  quantity?: number
  unitPrice?: number
  taxes?: number
}

/** The response for a create/edit request: the body echoed back, trade fields defaulting to null. */
function toTransfer(id: string, body: TransferRequestBody): Transfer {
  return {
    id,
    date: body.date,
    fromAccountId: body.fromAccountId,
    toAccountId: body.toAccountId,
    amount: body.amount,
    description: body.description,
    additionalNotes: body.additionalNotes ?? null,
    investmentProductId: body.investmentProductId ?? null,
    quantity: body.quantity ?? null,
    unitPrice: body.unitPrice ?? null,
    taxes: body.taxes ?? null,
  }
}

const transfers = createStore([...seedTransfers, ...seedProductTrades])
const seedProductTradeIds = new Set(seedProductTrades.map((t) => t.id))

/**
 * Default success-path handlers for every transfers endpoint (F005's REST API). List applies the
 * same filter dimensions the real backend does (including `accountId` matching either side, PRD
 * S6.9), against an in-memory store
 * (restored after each test by `resetStores()`, see `categories.ts`), and paginates with the same
 * page/size defaults (page 0, size 20) as F004's transactions handlers.
 */
export const transfersHandlers = [
  http.get(TRANSFERS_URL, ({ request }) => {
    const url = new URL(request.url)
    const dateFrom = url.searchParams.get('dateFrom')
    const dateTo = url.searchParams.get('dateTo')
    const accountId = url.searchParams.get('accountId')
    const investmentProductId = url.searchParams.get('investmentProductId')
    const page = Number(url.searchParams.get('page') ?? '0')
    const size = Number(url.searchParams.get('size') ?? '20')

    // Only a product-filtered list includes the (otherwise hidden) seeded product trades.
    const filtered = transfers
      .list()
      .filter((t) => (investmentProductId ? true : !seedProductTradeIds.has(t.id)))
      .filter((t) => !investmentProductId || t.investmentProductId === investmentProductId)
      .filter((t) => !dateFrom || t.date >= dateFrom)
      .filter((t) => !dateTo || t.date <= dateTo)
      .filter((t) => !accountId || t.fromAccountId === accountId || t.toAccountId === accountId)
      .sort((a, b) => (a.date < b.date ? 1 : -1))

    const start = page * size
    const content = filtered.slice(start, start + size)

    return HttpResponse.json({
      content,
      page: {
        size,
        number: page,
        totalElements: filtered.length,
        totalPages: Math.max(1, Math.ceil(filtered.length / size)),
      },
    })
  }),

  http.get(`${TRANSFERS_URL}/:id`, ({ params }) => {
    const transfer = transfers.find(params.id as string)
    if (!transfer) return new HttpResponse(null, { status: 404 })
    return HttpResponse.json(transfer)
  }),

  http.post(TRANSFERS_URL, async ({ request }) => {
    const body = (await request.json()) as TransferRequestBody
    const created = transfers.add(toTransfer(transfers.nextId('trf'), body))
    return HttpResponse.json(created, { status: 201 })
  }),

  http.patch(`${TRANSFERS_URL}/:id`, async ({ request, params }) => {
    const body = (await request.json()) as TransferRequestBody
    const updated = transfers.replace(params.id as string, () =>
      toTransfer(params.id as string, body),
    )
    return updated ? HttpResponse.json(updated) : new HttpResponse(null, { status: 404 })
  }),

  http.delete(`${TRANSFERS_URL}/:id`, ({ params }) => {
    transfers.remove(params.id as string)
    return new HttpResponse(null, { status: 204 })
  }),
]

/**
 * `409` variant for the closed-account-rejection case (F005 spec: "Both accounts must be open ...
 * at creation time") - applied via `server.use(...)` in tests that exercise that path, same
 * pattern as `transactionClosedAccountConflictHandler`.
 */
export const transferClosedAccountConflictHandler = http.post(TRANSFERS_URL, () =>
  HttpResponse.json({ message: 'Account is closed' }, { status: 409 }),
)
