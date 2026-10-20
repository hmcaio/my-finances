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
  list: (holdingId: string) => [...investmentSnapshotKeys.all, 'list', holdingId] as const,
}

export function useInvestmentSnapshots(holdingId: string | undefined) {
  return useQuery({
    queryKey: investmentSnapshotKeys.list(holdingId ?? ''),
    queryFn: () =>
      holdingId
        ? getInvestmentSnapshots(holdingId)
        : Promise.reject(new Error('Missing holding id.')),
    enabled: holdingId !== undefined,
  })
}

export function useRecordInvestmentSnapshot() {
  return useMutation({
    mutationFn: ({ holdingId, ...request }: RecordSnapshotRequest & { holdingId: string }) =>
      recordInvestmentSnapshot(holdingId, request),
  })
}

export function useUpdateInvestmentSnapshot() {
  return useMutation({
    mutationFn: ({
      holdingId,
      snapshotId,
      ...request
    }: UpdateSnapshotRequest & { holdingId: string; snapshotId: string }) =>
      updateInvestmentSnapshot(holdingId, snapshotId, request),
  })
}

export function useDeleteInvestmentSnapshot() {
  return useMutation({
    mutationFn: ({ holdingId, snapshotId }: { holdingId: string; snapshotId: string }) =>
      deleteInvestmentSnapshot(holdingId, snapshotId),
  })
}
