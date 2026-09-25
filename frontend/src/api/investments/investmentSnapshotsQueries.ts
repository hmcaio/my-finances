import { useMutation, useQuery } from '@tanstack/react-query'
import { API_KEY_ROOT } from '../core/queryClient'
import {
  deleteInvestmentSnapshot,
  getInvestmentSnapshots,
  recordInvestmentSnapshot,
  updateInvestmentSnapshot,
  type RecordSnapshotRequest,
  type UpdateSnapshotRequest,
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

export function useUpdateInvestmentSnapshot() {
  return useMutation({
    mutationFn: ({
      productId,
      snapshotId,
      ...request
    }: UpdateSnapshotRequest & { productId: string; snapshotId: string }) =>
      updateInvestmentSnapshot(productId, snapshotId, request),
  })
}

export function useDeleteInvestmentSnapshot() {
  return useMutation({
    mutationFn: ({ productId, snapshotId }: { productId: string; snapshotId: string }) =>
      deleteInvestmentSnapshot(productId, snapshotId),
  })
}
