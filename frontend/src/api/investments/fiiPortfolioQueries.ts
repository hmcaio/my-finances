import { useQuery } from '@tanstack/react-query'
import { API_KEY_ROOT } from '../core/queryClient'
import { getFiiPortfolio } from './fiiPortfolio'
import type { InvestmentProductStatus } from './investmentProducts'

export const fiiPortfolioKeys = {
  all: [API_KEY_ROOT, 'fii-portfolio'] as const,
  list: (status: InvestmentProductStatus) => [...fiiPortfolioKeys.all, 'list', status] as const,
}

export function useFiiPortfolio(status: InvestmentProductStatus = 'OPEN') {
  return useQuery({
    queryKey: fiiPortfolioKeys.list(status),
    queryFn: () => getFiiPortfolio(status),
  })
}
