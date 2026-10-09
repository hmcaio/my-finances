import { useQuery } from '@tanstack/react-query'
import { API_KEY_ROOT } from '../core/queryClient'
import { getTradeConfirmationLinesByProduct } from './tradeConfirmationLines'

export const tradeConfirmationLineKeys = {
  all: [API_KEY_ROOT, 'trade-confirmation-lines'] as const,
  byProduct: (productId: string) =>
    [...tradeConfirmationLineKeys.all, 'by-product', productId] as const,
}

/** Every line trading one product (F027), for `InvestmentProductDetailPage`'s trade table. */
export function useTradeConfirmationLinesByProduct(productId: string | undefined) {
  return useQuery({
    queryKey: tradeConfirmationLineKeys.byProduct(productId ?? ''),
    queryFn: () =>
      productId
        ? getTradeConfirmationLinesByProduct(productId)
        : Promise.reject(new Error('Missing product id.')),
    enabled: productId !== undefined,
  })
}
