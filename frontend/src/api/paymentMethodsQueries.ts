import { useMutation, useQuery } from '@tanstack/react-query'
import { API_KEY_ROOT, STALE_TIME } from './queryClient'
import {
  createPaymentMethod,
  deletePaymentMethod,
  getPaymentMethods,
  renamePaymentMethod,
  type UpdatePaymentMethodRequest,
} from './paymentMethods'

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

export function useCreatePaymentMethod() {
  return useMutation({ mutationFn: createPaymentMethod })
}

export function useRenamePaymentMethod() {
  return useMutation({
    mutationFn: ({ id, ...request }: UpdatePaymentMethodRequest & { id: string }) =>
      renamePaymentMethod(id, request),
  })
}

export function useDeletePaymentMethod() {
  return useMutation({ mutationFn: deletePaymentMethod })
}
