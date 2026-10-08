import { useQuery } from '@tanstack/react-query'
import { API_KEY_ROOT } from '../core/queryClient'
import {
  getFiiAllocation,
  type FiiAllocationBasis,
  type FiiAllocationGroupBy,
} from './fiiAllocation'

export const fiiAllocationKeys = {
  all: [API_KEY_ROOT, 'fii-allocation'] as const,
  list: (basis: FiiAllocationBasis, groupBy: FiiAllocationGroupBy) =>
    [...fiiAllocationKeys.all, 'list', basis, groupBy] as const,
}

export function useFiiAllocation(basis: FiiAllocationBasis, groupBy: FiiAllocationGroupBy) {
  return useQuery({
    queryKey: fiiAllocationKeys.list(basis, groupBy),
    queryFn: () => getFiiAllocation(basis, groupBy),
  })
}
