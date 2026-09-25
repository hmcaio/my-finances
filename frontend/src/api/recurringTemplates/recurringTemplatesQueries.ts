import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { API_KEY_ROOT, STALE_TIME } from '../core/queryClient'
import {
  confirmPendingRecurringOccurrence,
  createRecurringTemplate,
  dismissPendingRecurringOccurrence,
  getPendingRecurringOccurrences,
  getRecurringTemplates,
  reactivateRecurringTemplate,
  setRecurringTemplateCap,
  stopRecurringTemplate,
  type ConfirmPendingOccurrenceRequest,
  type UpdateRecurringTemplateCapRequest,
} from './recurringTemplates'

export const recurringTemplateKeys = {
  all: [API_KEY_ROOT, 'recurring-templates'] as const,
  list: () => [...recurringTemplateKeys.all, 'list'] as const,
  pending: () => [...recurringTemplateKeys.all, 'pending'] as const,
}

export function useRecurringTemplates() {
  return useQuery({ queryKey: recurringTemplateKeys.list(), queryFn: getRecurringTemplates })
}

/**
 * Pending occurrences. The read has a side effect (lazy catch-up generation, ADR 0003), so it is
 * never served from cache: `staleTime: 0` refetches on every mount and window focus.
 */
export function usePendingRecurringOccurrences() {
  return useQuery({
    queryKey: recurringTemplateKeys.pending(),
    queryFn: getPendingRecurringOccurrences,
    staleTime: STALE_TIME.none,
  })
}

export function useCreateRecurringTemplate() {
  return useMutation({ mutationFn: createRecurringTemplate })
}

export function useSetRecurringTemplateCap() {
  return useMutation({
    mutationFn: ({ id, ...request }: UpdateRecurringTemplateCapRequest & { id: string }) =>
      setRecurringTemplateCap(id, request),
  })
}

export function useStopRecurringTemplate() {
  return useMutation({ mutationFn: stopRecurringTemplate })
}

export function useReactivateRecurringTemplate() {
  return useMutation({ mutationFn: reactivateRecurringTemplate })
}

export function useConfirmPendingOccurrence() {
  return useMutation({
    mutationFn: ({ id, ...request }: ConfirmPendingOccurrenceRequest & { id: string }) =>
      confirmPendingRecurringOccurrence(id, request),
  })
}

/**
 * Dismissing creates nothing, so it opts out of the global invalidate-everything rule and only
 * refetches the pending list (the other dashboard widgets don't move).
 */
export function useDismissPendingOccurrence() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: dismissPendingRecurringOccurrence,
    meta: { skipInvalidate: true },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: recurringTemplateKeys.pending() }),
  })
}
