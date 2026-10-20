import { useMutation, useQuery } from '@tanstack/react-query'
import { API_KEY_ROOT } from '../core/queryClient'
import {
  createTransfer,
  deleteTransfer,
  editTransfer,
  getTransfer,
  type UpdateTransferRequest,
} from './transfers'

export const transferKeys = {
  all: [API_KEY_ROOT, 'transfers'] as const,
  detail: (id: string) => [...transferKeys.all, 'detail', id] as const,
}

/**
 * One transfer by id, disabled while `id` is `null` (F027: resolving a `?focus=` deep link to the
 * full trade confirmation regardless of the period; F037: the Ledger's edit loads it the same way).
 * `gcTime: 0`: nothing is kept once the edit dialog stops observing it, so reopening the same row
 * always loads the saved values, never a stale copy from before the last edit.
 */
export function useTransfer(id: string | null) {
  return useQuery({
    queryKey: transferKeys.detail(id ?? ''),
    queryFn: () => getTransfer(id!),
    enabled: id !== null,
    gcTime: 0,
  })
}

export function useCreateTransfer() {
  return useMutation({ mutationFn: createTransfer })
}

export function useEditTransfer() {
  return useMutation({
    mutationFn: ({ id, ...request }: UpdateTransferRequest & { id: string }) =>
      editTransfer(id, request),
  })
}

export function useDeleteTransfer() {
  return useMutation({ mutationFn: deleteTransfer })
}
