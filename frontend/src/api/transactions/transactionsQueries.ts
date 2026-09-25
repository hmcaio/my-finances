import { keepPreviousData, useMutation, useQuery } from '@tanstack/react-query'
import { API_KEY_ROOT } from '../core/queryClient'
import {
  createTransaction,
  deleteTransaction,
  editTransaction,
  getSpendByCategory,
  getTransactions,
  type TransactionFilter,
  type UpdateTransactionRequest,
} from './transactions'

export const transactionKeys = {
  all: [API_KEY_ROOT, 'transactions'] as const,
  list: (filter: TransactionFilter, page: number, size?: number) =>
    [...transactionKeys.all, 'list', { filter, page, size }] as const,
  spendByCategory: (month: string) => [...transactionKeys.all, 'spend-by-category', month] as const,
}

/** One page of transactions; the previous page stays on screen while the next one loads. */
export function useTransactions(filter: TransactionFilter = {}, page = 0, size?: number) {
  return useQuery({
    queryKey: transactionKeys.list(filter, page, size),
    queryFn: () => getTransactions(filter, page, size),
    placeholderData: keepPreviousData,
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
