import { useMutation, useQuery } from '@tanstack/react-query'
import { API_KEY_ROOT } from '../core/queryClient'
import {
  createTransaction,
  deleteTransaction,
  editTransaction,
  getSpendByCategory,
  getTransaction,
  type UpdateTransactionRequest,
} from './transactions'

export const transactionKeys = {
  all: [API_KEY_ROOT, 'transactions'] as const,
  detail: (id: string) => [...transactionKeys.all, 'detail', id] as const,
  spendByCategory: (month: string) => [...transactionKeys.all, 'spend-by-category', month] as const,
}

/**
 * One transaction by id, disabled while `id` is `null` (F037: the Ledger's edit loads the full
 * record, which a ledger row does not carry). `gcTime: 0`: nothing is kept once the edit dialog
 * stops observing it, so reopening the same row always loads the saved values, never a stale copy
 * from before the last edit.
 */
export function useTransaction(id: string | null) {
  return useQuery({
    queryKey: transactionKeys.detail(id ?? ''),
    queryFn: () => getTransaction(id!),
    enabled: id !== null,
    gcTime: 0,
  })
}

/** Spend per expense category for one month (`YYYY-MM`). */
export function useSpendByCategory(month: string) {
  return useQuery({
    queryKey: transactionKeys.spendByCategory(month),
    queryFn: () => getSpendByCategory(month),
  })
}

export function useCreateTransaction() {
  return useMutation({ mutationFn: createTransaction })
}

export function useEditTransaction() {
  return useMutation({
    mutationFn: ({ id, ...request }: UpdateTransactionRequest & { id: string }) =>
      editTransaction(id, request),
  })
}

export function useDeleteTransaction() {
  return useMutation({ mutationFn: deleteTransaction })
}
