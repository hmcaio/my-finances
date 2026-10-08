import { useMutation, useQuery } from '@tanstack/react-query'
import { API_KEY_ROOT, STALE_TIME } from '../core/queryClient'
import {
  createInvestmentSegment,
  deleteInvestmentSegment,
  getInvestmentSegments,
  renameInvestmentSegment,
  type UpdateInvestmentSegmentRequest,
} from './investmentSegments'

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

export function useCreateInvestmentSegment() {
  return useMutation({ mutationFn: createInvestmentSegment })
}

export function useRenameInvestmentSegment() {
  return useMutation({
    mutationFn: ({ id, ...request }: UpdateInvestmentSegmentRequest & { id: string }) =>
      renameInvestmentSegment(id, request),
  })
}

export function useDeleteInvestmentSegment() {
  return useMutation({ mutationFn: deleteInvestmentSegment })
}
