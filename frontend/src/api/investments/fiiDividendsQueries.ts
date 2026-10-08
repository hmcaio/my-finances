import { useMutation, useQuery } from '@tanstack/react-query'
import { API_KEY_ROOT } from '../core/queryClient'
import {
  getDividends,
  getDividendTotalsByMonth,
  getDividendTotalsByTicker,
  type DividendFilter,
} from './fiiDividends'
import { createTransaction } from '../transactions/transactions'

export const fiiDividendKeys = {
  all: [API_KEY_ROOT, 'fii-dividends'] as const,
  list: (filter: DividendFilter) => [...fiiDividendKeys.all, 'list', filter] as const,
  totalsByTicker: (filter: Omit<DividendFilter, 'productId'>) =>
    [...fiiDividendKeys.all, 'totals-by-ticker', filter] as const,
  totalsByMonth: (filter: Omit<DividendFilter, 'productId'>) =>
    [...fiiDividendKeys.all, 'totals-by-month', filter] as const,
}

export function useDividends(filter: DividendFilter = {}) {
  return useQuery({
    queryKey: fiiDividendKeys.list(filter),
    queryFn: () => getDividends(filter),
  })
}

export function useDividendTotalsByTicker(filter: Omit<DividendFilter, 'productId'> = {}) {
  return useQuery({
    queryKey: fiiDividendKeys.totalsByTicker(filter),
    queryFn: () => getDividendTotalsByTicker(filter),
  })
}

export function useDividendTotalsByMonth(filter: Omit<DividendFilter, 'productId'> = {}) {
  return useQuery({
    queryKey: fiiDividendKeys.totalsByMonth(filter),
    queryFn: () => getDividendTotalsByMonth(filter),
  })
}

/**
 * Registers a dividend as a plain `Transaction` in the dedicated dividend category (F026 spec:
 * "a dedicated form, not the generic transaction form" - but it still just creates a
 * `Transaction`, so this reuses `createTransaction` rather than a bespoke endpoint).
 */
export function useRegisterDividend() {
  return useMutation({ mutationFn: createTransaction })
}
