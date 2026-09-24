import { useMutation, useQuery } from '@tanstack/react-query'
import { API_KEY_ROOT } from './queryClient'
import {
  closeInvestmentProduct,
  createInvestmentProduct,
  deleteInvestmentProduct,
  editInvestmentProduct,
  getInvestmentProduct,
  getInvestmentProducts,
  type UpdateInvestmentProductRequest,
} from './investmentProducts'

export const investmentProductKeys = {
  all: [API_KEY_ROOT, 'investment-products'] as const,
  list: (accountId?: string) => [...investmentProductKeys.all, 'list', { accountId }] as const,
  detail: (id: string) => [...investmentProductKeys.all, 'detail', id] as const,
}

/** Products of one INVESTMENT account, or every product when `accountId` is omitted. */
export function useInvestmentProducts(accountId?: string) {
  return useQuery({
    queryKey: investmentProductKeys.list(accountId),
    queryFn: () => getInvestmentProducts(accountId),
  })
}

export function useInvestmentProduct(id: string | undefined) {
  return useQuery({
    queryKey: investmentProductKeys.detail(id ?? ''),
    queryFn: () =>
      id ? getInvestmentProduct(id) : Promise.reject(new Error('Missing product id.')),
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

export function useCloseInvestmentProduct() {
  return useMutation({ mutationFn: closeInvestmentProduct })
}

export function useDeleteInvestmentProduct() {
  return useMutation({ mutationFn: deleteInvestmentProduct })
}
