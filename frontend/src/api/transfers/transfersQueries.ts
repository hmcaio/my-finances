import { keepPreviousData, useMutation, useQuery } from '@tanstack/react-query'
import { API_KEY_ROOT } from '../core/queryClient'
import {
  createTransfer,
  deleteTransfer,
  editTransfer,
  getTransfers,
  type TransferFilter,
  type UpdateTransferRequest,
} from './transfers'

export const transferKeys = {
  all: [API_KEY_ROOT, 'transfers'] as const,
  list: (filter: TransferFilter, page: number, size?: number) =>
    [...transferKeys.all, 'list', { filter, page, size }] as const,
}

/** One page of transfers; the previous page stays on screen while the next one loads. */
export function useTransfers(filter: TransferFilter = {}, page = 0, size?: number) {
  return useQuery({
    queryKey: transferKeys.list(filter, page, size),
    queryFn: () => getTransfers(filter, page, size),
    placeholderData: keepPreviousData,
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
