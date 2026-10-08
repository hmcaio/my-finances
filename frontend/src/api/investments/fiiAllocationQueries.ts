import { useQuery } from '@tanstack/react-query'
import { API_KEY_ROOT } from '../core/queryClient'
import {
  getFiiAllocation,
  type FiiAllocationBasis,
  type FiiAllocationGroupBy,
} from './fiiAllocation'

export const fiiAllocationKeys = {
  all: [API_KEY_ROOT, 'fii-allocation'] as const,
  list: (basis: FiiAllocationBasis, groupBy: FiiAllocationGroupBy, month: string) =>
    [...fiiAllocationKeys.all, 'list', basis, groupBy, month] as const,
}

/** `month` (`YYYY-MM`) defaults to the current month (Addendum: Month Selector). */
export function useFiiAllocation(
  basis: FiiAllocationBasis,
  groupBy: FiiAllocationGroupBy,
  month: string,
) {
  return useQuery({
    queryKey: fiiAllocationKeys.list(basis, groupBy, month),
    queryFn: () => getFiiAllocation(basis, groupBy, month),
  })
}
