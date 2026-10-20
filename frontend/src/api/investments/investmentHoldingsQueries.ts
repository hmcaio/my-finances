import { useMutation, useQuery } from '@tanstack/react-query'
import { API_KEY_ROOT } from '../core/queryClient'
import {
  closeInvestmentHolding,
  createInvestmentHolding,
  deleteInvestmentHolding,
  editInvestmentHoldingNotes,
  getInvestmentHolding,
  getInvestmentHoldingsByAccount,
  getInvestmentHoldingsByProduct,
  type UpdateInvestmentHoldingRequest,
} from './investmentHoldings'

export const investmentHoldingKeys = {
  all: [API_KEY_ROOT, 'investment-holdings'] as const,
  byProduct: (productId: string) =>
    [...investmentHoldingKeys.all, 'by-product', productId] as const,
  byAccount: (accountId: string) =>
    [...investmentHoldingKeys.all, 'by-account', accountId] as const,
  detail: (id: string) => [...investmentHoldingKeys.all, 'detail', id] as const,
}

/** Every holding of one product, across every account it's held in. */
export function useInvestmentHoldingsByProduct(productId: string | undefined) {
  return useQuery({
    queryKey: investmentHoldingKeys.byProduct(productId ?? ''),
    queryFn: () =>
      productId
        ? getInvestmentHoldingsByProduct(productId)
        : Promise.reject(new Error('Missing product id.')),
    enabled: productId !== undefined,
  })
}

/** Every holding in one account, across every product held there. */
export function useInvestmentHoldingsByAccount(
  accountId: string | undefined,
  options: { enabled?: boolean } = {},
) {
  return useQuery({
    queryKey: investmentHoldingKeys.byAccount(accountId ?? ''),
    queryFn: () =>
      accountId
        ? getInvestmentHoldingsByAccount(accountId)
        : Promise.reject(new Error('Missing account id.')),
    enabled: accountId !== undefined && options.enabled !== false,
  })
}

export function useInvestmentHolding(id: string | undefined) {
  return useQuery({
    queryKey: investmentHoldingKeys.detail(id ?? ''),
    queryFn: () =>
      id ? getInvestmentHolding(id) : Promise.reject(new Error('Missing holding id.')),
    enabled: id !== undefined,
  })
}

export function useCreateInvestmentHolding() {
  return useMutation({ mutationFn: createInvestmentHolding })
}

export function useEditInvestmentHoldingNotes() {
  return useMutation({
    mutationFn: ({ id, ...request }: UpdateInvestmentHoldingRequest & { id: string }) =>
      editInvestmentHoldingNotes(id, request),
  })
}

export function useCloseInvestmentHolding() {
  return useMutation({ mutationFn: closeInvestmentHolding })
}

export function useDeleteInvestmentHolding() {
  return useMutation({ mutationFn: deleteInvestmentHolding })
}
