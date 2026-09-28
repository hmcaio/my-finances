import { useMutation, useQuery } from '@tanstack/react-query'
import { API_KEY_ROOT } from '../core/queryClient'
import {
  createInvestmentProduct,
  deleteInvestmentProduct,
  editInvestmentProduct,
  getInvestmentProduct,
  getInvestmentProducts,
  type UpdateInvestmentProductRequest,
} from './investmentProducts'

export const investmentProductKeys = {
  all: [API_KEY_ROOT, 'investment-products'] as const,
  list: () => [...investmentProductKeys.all, 'list'] as const,
  detail: (id: string) => [...investmentProductKeys.all, 'detail', id] as const,
}

/** Every product (pure taxonomy, F022 - no longer filterable by account). */
export function useInvestmentProducts() {
  return useQuery({
    queryKey: investmentProductKeys.list(),
    queryFn: () => getInvestmentProducts(),
  })
}

export function useInvestmentProduct(id: string | undefined) {
  return useQuery({
    queryKey: investmentProductKeys.detail(id ?? ''),
    queryFn: () =>
      id ? getInvestmentProduct(id) : Promise.reject(new Error('Missing product id.')),
    enabled: id !== undefined,
  })
}

export function useCreateInvestmentProduct() {
  return useMutation({ mutationFn: createInvestmentProduct })
}

export function useEditInvestmentProduct() {
  return useMutation({
    mutationFn: ({ id, ...request }: UpdateInvestmentProductRequest & { id: string }) =>
      editInvestmentProduct(id, request),
  })
}

export function useDeleteInvestmentProduct() {
  return useMutation({ mutationFn: deleteInvestmentProduct })
}
