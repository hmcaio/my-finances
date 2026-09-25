import { useMutation, useQuery } from '@tanstack/react-query'
import { API_KEY_ROOT } from './queryClient'
import {
  getInvestmentSnapshots,
  recordInvestmentSnapshot,
  type RecordSnapshotRequest,
} from './investmentSnapshots'

export const investmentSnapshotKeys = {
  all: [API_KEY_ROOT, 'investment-snapshots'] as const,
  list: (productId: string) => [...investmentSnapshotKeys.all, 'list', productId] as const,
}

export function useInvestmentSnapshots(productId: string) {
  return useQuery({
    queryKey: investmentSnapshotKeys.list(productId),
    queryFn: () => getInvestmentSnapshots(productId),
  })
}

export function useRecordInvestmentSnapshot() {
  return useMutation({
    mutationFn: ({ productId, ...request }: RecordSnapshotRequest & { productId: string }) =>
      recordInvestmentSnapshot(productId, request),
  })
}
