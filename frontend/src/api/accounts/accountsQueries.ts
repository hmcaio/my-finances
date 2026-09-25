import { keepPreviousData, useMutation, useQuery } from '@tanstack/react-query'
import { API_KEY_ROOT } from '../core/queryClient'
import {
  closeAccount,
  createAccount,
  editAccount,
  getAccount,
  getAccounts,
  type UpdateAccountRequest,
} from './accounts'

export const accountKeys = {
  all: [API_KEY_ROOT, 'accounts'] as const,
  list: (includeClosed: boolean) => [...accountKeys.all, 'list', { includeClosed }] as const,
  detail: (id: string) => [...accountKeys.all, 'detail', id] as const,
}

/**
 * The account list. Toggling `includeClosed` keeps the previous rows on screen while the other
 * list loads, so the table doesn't fall back to its skeleton.
 */
export function useAccounts(includeClosed = false) {
  return useQuery({
    queryKey: accountKeys.list(includeClosed),
    queryFn: () => getAccounts(includeClosed),
    placeholderData: keepPreviousData,
  })
}

export function useAccount(id: string | undefined) {
  return useQuery({
    queryKey: accountKeys.detail(id ?? ''),
    queryFn: () => (id ? getAccount(id) : Promise.reject(new Error('Missing account id.'))),
  })
}

export function useCreateAccount() {
  return useMutation({ mutationFn: createAccount })
}

export function useEditAccount() {
  return useMutation({
    mutationFn: ({ id, ...request }: UpdateAccountRequest & { id: string }) =>
      editAccount(id, request),
  })
}

export function useCloseAccount() {
  return useMutation({ mutationFn: closeAccount })
}
