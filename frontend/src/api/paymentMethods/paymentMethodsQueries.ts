import { useQuery } from '@tanstack/react-query'
import { API_KEY_ROOT, STALE_TIME } from '../core/queryClient'
import { getPaymentMethods } from './paymentMethods'

export const paymentMethodKeys = {
  all: [API_KEY_ROOT, 'payment-methods'] as const,
  list: () => [...paymentMethodKeys.all, 'list'] as const,
}

export function usePaymentMethods() {
  return useQuery({
    queryKey: paymentMethodKeys.list(),
    queryFn: getPaymentMethods,
    staleTime: STALE_TIME.reference,
  })
}
