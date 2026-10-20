import { keepPreviousData, useMutation, useQuery } from '@tanstack/react-query'
import { API_KEY_ROOT } from '../core/queryClient'
import {
  createInvestmentProduct,
  deleteInvestmentProduct,
  editInvestmentProduct,
  getInvestmentProduct,
  getInvestmentProducts,
  getInvestmentProductsPage,
  type InvestmentProductFilter,
  type UpdateInvestmentProductRequest,
} from './investmentProducts'

export const investmentProductKeys = {
  all: [API_KEY_ROOT, 'investment-products'] as const,
  list: () => [...investmentProductKeys.all, 'list'] as const,
  page: (filter: InvestmentProductFilter, page: number, size: number) =>
    [...investmentProductKeys.all, 'page', filter, page, size] as const,
  detail: (id: string) => [...investmentProductKeys.all, 'detail', id] as const,
}

/**
 * Every product, regardless of status (pure taxonomy, F022 - no longer filterable by account).
 * Used by name-lookup callers (`InvestmentProductsSection`, the transfer form) - the global,
 * filtered Products list (F023) uses {@link useInvestmentProductsPage} instead.
 */
export function useInvestmentProducts() {
  return useQuery({
    queryKey: investmentProductKeys.list(),
    queryFn: () => getInvestmentProducts(),
  })
}

/** The global, filtered/paginated product list (F023 spec's Products tab). */
export function useInvestmentProductsPage(
  filter: InvestmentProductFilter,
  page: number,
  size: number,
) {
  return useQuery({
    queryKey: investmentProductKeys.page(filter, page, size),
    queryFn: () => getInvestmentProductsPage(filter, page, size),
    placeholderData: keepPreviousData,
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
