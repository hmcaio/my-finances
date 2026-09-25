import { useQuery } from '@tanstack/react-query'
import { API_KEY_ROOT } from '../core/queryClient'
import { getInvestmentAllocation, type AllocationParams } from './investmentAllocation'

export const investmentAllocationKeys = {
  all: [API_KEY_ROOT, 'investment-allocation'] as const,
  list: (params: AllocationParams) => [...investmentAllocationKeys.all, 'list', params] as const,
}

export function useInvestmentAllocation(params: AllocationParams = {}) {
  return useQuery({
    queryKey: investmentAllocationKeys.list(params),
    queryFn: () => getInvestmentAllocation(params),
  })
}
