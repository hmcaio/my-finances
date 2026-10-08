import { useQuery } from '@tanstack/react-query'
import { API_KEY_ROOT } from '../core/queryClient'
import { getFiiPortfolio } from './fiiPortfolio'
import type { InvestmentProductStatus } from './investmentProducts'

export const fiiPortfolioKeys = {
  all: [API_KEY_ROOT, 'fii-portfolio'] as const,
  list: (status: InvestmentProductStatus, month: string) =>
    [...fiiPortfolioKeys.all, 'list', status, month] as const,
}

/** `month` (`YYYY-MM`) defaults to the current month (Addendum: Month Selector). */
export function useFiiPortfolio(status: InvestmentProductStatus = 'OPEN', month: string) {
  return useQuery({
    queryKey: fiiPortfolioKeys.list(status, month),
    queryFn: () => getFiiPortfolio(status, month),
  })
}
