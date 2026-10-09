import { http, HttpResponse } from 'msw'
import type { TradeConfirmationLineRecord } from '../../api/investments/tradeConfirmationLines'
import { transfers } from './transfers'

const TRADE_CONFIRMATION_LINES_URL = '/api/trade-confirmation-lines'

/**
 * `GET /api/trade-confirmation-lines?productId=` (F027 spec, ADR 0024): every line of every
 * transfer's trade confirmation that trades `productId`, derived from the same transfers store
 * `transfers.ts`'s handlers read/write - a write there is visible here on the next request, same
 * as the real backend reading both tables. Replaces the old per-product filter on
 * `GET /api/transfers` for `InvestmentProductDetailPage`'s trade table.
 */
export const tradeConfirmationLinesHandlers = [
  http.get(TRADE_CONFIRMATION_LINES_URL, ({ request }) => {
    const url = new URL(request.url)
    const productId = url.searchParams.get('productId')
    if (!productId) return new HttpResponse(null, { status: 400 })

    const lines: TradeConfirmationLineRecord[] = transfers
      .list()
      .flatMap((t) =>
        (t.tradeConfirmation?.lines ?? [])
          .filter((l) => l.productId === productId)
          .map((l) => ({
            transferId: t.id,
            date: t.date,
            side: l.side,
            quantity: l.quantity,
            unitPrice: l.unitPrice,
            resultingBalance: l.resultingBalance,
          })),
      )
      .sort((a, b) => (a.date < b.date ? 1 : -1))

    return HttpResponse.json(lines)
  }),
]
