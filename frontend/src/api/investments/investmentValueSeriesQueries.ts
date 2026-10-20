import { useQuery } from '@tanstack/react-query'
import { API_KEY_ROOT } from '../core/queryClient'
import { getInvestmentValueSeries, type ValueSeriesParams } from './investmentValueSeries'

export const investmentValueSeriesKeys = {
  all: [API_KEY_ROOT, 'investment-value-series'] as const,
  list: (params: ValueSeriesParams) => [...investmentValueSeriesKeys.all, 'list', params] as const,
}

export function useInvestmentValueSeries(params: ValueSeriesParams = {}) {
  return useQuery({
    queryKey: investmentValueSeriesKeys.list(params),
    queryFn: () => getInvestmentValueSeries(params),
  })
}
