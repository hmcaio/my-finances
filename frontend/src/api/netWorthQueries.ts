import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { API_KEY_ROOT } from './queryClient'
import { getNetWorthTrend, type NetWorthTrendParams } from './netWorth'

export const netWorthKeys = {
  all: [API_KEY_ROOT, 'net-worth'] as const,
  trend: (params: NetWorthTrendParams) => [...netWorthKeys.all, 'trend', params] as const,
}

/** The net worth series; switching granularity keeps the previous series until the next arrives. */
export function useNetWorthTrend(params: NetWorthTrendParams = {}) {
  return useQuery({
    queryKey: netWorthKeys.trend(params),
    queryFn: () => getNetWorthTrend(params),
    placeholderData: keepPreviousData,
  })
}
