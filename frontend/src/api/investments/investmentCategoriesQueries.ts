import { useQuery } from '@tanstack/react-query'
import { API_KEY_ROOT, STALE_TIME } from '../core/queryClient'
import { getInvestmentCategories } from './investmentCategories'

export const investmentCategoryKeys = {
  all: [API_KEY_ROOT, 'investment-categories'] as const,
  list: () => [...investmentCategoryKeys.all, 'list'] as const,
}

/** The two-level taxonomy (categories with their sub-categories nested). */
export function useInvestmentCategories() {
  return useQuery({
    queryKey: investmentCategoryKeys.list(),
    queryFn: getInvestmentCategories,
    staleTime: STALE_TIME.reference,
  })
}
