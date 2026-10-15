import { useQuery } from '@tanstack/react-query'
import { API_KEY_ROOT, STALE_TIME } from '../core/queryClient'
import { getInvestmentSegments } from './investmentSegments'

export const investmentSegmentKeys = {
  all: [API_KEY_ROOT, 'investment-segments'] as const,
  list: () => [...investmentSegmentKeys.all, 'list'] as const,
}

export function useInvestmentSegments() {
  return useQuery({
    queryKey: investmentSegmentKeys.list(),
    queryFn: getInvestmentSegments,
    staleTime: STALE_TIME.reference,
  })
}
