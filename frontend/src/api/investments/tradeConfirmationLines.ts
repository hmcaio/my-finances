import { apiClient } from '../core/client'
import { unwrap } from '../core/apiError'

/** One matching line of `GET /api/trade-confirmation-lines?productId=` (F027 spec, ADR 0024):
 * replaces `InvestmentProductDetailPage`'s old use of `GET /api/transfers?investmentProductId=`
 * for its trade table, now one row per line rather than per confirmation. Carries its parent
 * `transferId` so the frontend can link back to the full confirmation (other lines, real total
 * taxes) - the full confirmation's taxes aren't repeated here, since they cover the whole
 * settlement, not this one line's product. */
export interface TradeConfirmationLineRecord {
  transferId: string
  date: string
  side: 'BUY' | 'SELL'
  quantity: number
  unitPrice: number
  resultingBalance: number | null
}

/** Every line trading `productId`, most recent first. `404` for an unknown product. */
export async function getTradeConfirmationLinesByProduct(
  productId: string,
): Promise<TradeConfirmationLineRecord[]> {
  return unwrap(
    apiClient.get<TradeConfirmationLineRecord[]>('/trade-confirmation-lines', {
      params: { productId },
    }),
  )
}
