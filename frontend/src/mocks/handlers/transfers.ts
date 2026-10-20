import { http, HttpResponse } from 'msw'
import type { Transfer } from '../../api/transfers/transfers'
import { createStore } from '../store'

/**
 * Seed data for the transfers store (read by the single-transfer handler below and by the Ledger's,
 * `ledger.ts`). Spans both seed accounts
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
    taxes: null,
    tradeConfirmation: null,
  },
  {
    id: 'trf-2',
    date: '2026-01-25',
    fromAccountId: 'acct-3',
    toAccountId: 'acct-1',
    amount: 50,
    description: 'Refund',
    additionalNotes: 'Overpaid last month',
    taxes: null,
    tradeConfirmation: null,
  },
]

/**
 * A single-line BUY confirmation of the seeded Bitcoin product (F027, ADR 0024): checking into
 * the seeded INVESTMENT account with quantity, unit price and taxes. Kept OUT of `seedTransfers`
 * (the January fixtures); it is dated August, so it only shows in the Ledger for that month or all
 * time.
 */
export const seedBitcoinBuyTransfer: Transfer = {
  id: 'trf-btc-buy',
  date: '2026-08-10',
  fromAccountId: 'acct-1',
  toAccountId: 'acct-inv',
  amount: 1005,
  description: 'Buy Bitcoin',
  additionalNotes: null,
  taxes: 5,
  tradeConfirmation: {
    lines: [
      {
        productId: 'iprod-btc',
        side: 'BUY',
        quantity: 0.01,
        unitPrice: 100000,
        resultingBalance: null,
        closeHolding: false,
      },
    ],
  },
}

/** The matching sell (out of the INVESTMENT account, no taxes recorded). */
export const seedBitcoinSellTransfer: Transfer = {
  id: 'trf-btc-sell',
  date: '2026-08-20',
  fromAccountId: 'acct-inv',
  toAccountId: 'acct-1',
  amount: 500,
  description: 'Sell some Bitcoin',
  additionalNotes: null,
  taxes: 0,
  tradeConfirmation: {
    lines: [
      {
        productId: 'iprod-btc',
        side: 'SELL',
        quantity: 0.005,
        unitPrice: 100000,
        resultingBalance: null,
        closeHolding: false,
      },
    ],
  },
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

interface TransferLineRequestBody {
  productId: string
  side: 'BUY' | 'SELL'
  quantity: number
  unitPrice: number
  resultingBalance?: number
  closeHolding?: boolean
}

interface TransferRequestBody {
  date: string
  fromAccountId?: string
  toAccountId?: string
  amount?: number
  description: string
  additionalNotes?: string
  cashAccountId?: string
  investmentAccountId?: string
  tradeConfirmation?: { taxes: number; lines: TransferLineRequestBody[] }
}

/** The settlement's net total (mirrors the backend's `TradeConfirmation.netCost`): BUY adds, SELL
 * subtracts, taxes add once. Used only to derive `amount`/direction for the mock's response, same
 * as the real backend does. */
function netCost(body: NonNullable<TransferRequestBody['tradeConfirmation']>): number {
  const net = body.lines.reduce(
    (sum, line) => sum + (line.side === 'BUY' ? 1 : -1) * line.quantity * line.unitPrice,
    0,
  )
  return Math.round((net + body.taxes) * 100) / 100
}

/** The response for a create/edit request: the body echoed back, deriving amount/direction from a
 * trade confirmation's net settlement (F027) exactly as the real backend does. */
function toTransfer(id: string, body: TransferRequestBody): Transfer {
  if (body.tradeConfirmation) {
    const net = netCost(body.tradeConfirmation)
    const cost = net > 0
    return {
      id,
      date: body.date,
      fromAccountId: cost ? body.cashAccountId! : body.investmentAccountId!,
      toAccountId: cost ? body.investmentAccountId! : body.cashAccountId!,
      amount: Math.abs(net),
      description: body.description,
      additionalNotes: body.additionalNotes ?? null,
      taxes: body.tradeConfirmation.taxes,
      tradeConfirmation: {
        lines: body.tradeConfirmation.lines.map((l) => ({
          productId: l.productId,
          side: l.side,
          quantity: l.quantity,
          unitPrice: l.unitPrice,
          resultingBalance: l.resultingBalance ?? null,
          closeHolding: l.closeHolding ?? false,
        })),
      },
    }
  }
  return {
    id,
    date: body.date,
    fromAccountId: body.fromAccountId!,
    toAccountId: body.toAccountId!,
    amount: body.amount!,
    description: body.description,
    additionalNotes: body.additionalNotes ?? null,
    taxes: null,
    tradeConfirmation: null,
  }
}

/** Exported so `tradeConfirmationLines.ts`'s handler can derive its rows from the same live
 * transfers (seed + whatever a test creates/edits), rather than duplicating state. */
export const transfers = createStore([...seedTransfers, ...seedProductTrades])

/**
 * Default success-path handlers for the transfers endpoints the frontend calls (F005's REST API,
 * F027's trade confirmations), against an in-memory store restored after each test by
 * `resetStores()` (see `categories.ts`). The paged `GET /api/transfers` list has no frontend
 * caller since F037's Ledger (`ledger.ts` reads this store instead), so it has no handler here.
 */
export const transfersHandlers = [
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
  HttpResponse.json({ type: 'urn:my-finances:error:account-closed' }, { status: 409 }),
)
