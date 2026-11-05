import { useMutation, useQuery } from '@tanstack/react-query'
import { API_KEY_ROOT } from '../core/queryClient'
import {
  createGoal,
  deleteGoal,
  editGoal,
  getEmergencyFundSuggestion,
  getGoal,
  getGoalDashboard,
  getGoalHistory,
  getGoalProjection,
  getGoals,
  getProductGoalAllocations,
  replaceGoalAllocations,
  setGoalShowOnDashboard,
  type HistoryParams,
  type ReplaceAllocationsRequest,
  type UpdateGoalRequest,
} from './goals'

export const goalKeys = {
  all: [API_KEY_ROOT, 'goals'] as const,
  list: (includeArchived: boolean) => [...goalKeys.all, 'list', includeArchived] as const,
  detail: (id: string) => [...goalKeys.all, 'detail', id] as const,
  projection: (id: string) => [...goalKeys.all, 'projection', id] as const,
  history: (id: string, params: HistoryParams) => [...goalKeys.all, 'history', id, params] as const,
  dashboard: () => [...goalKeys.all, 'dashboard'] as const,
  suggestion: (months: number) => [...goalKeys.all, 'emergency-fund-suggestion', months] as const,
  forProduct: (productId: string) => [...goalKeys.all, 'for-product', productId] as const,
}

/** The goals in the standard order; archived ones only when asked. */
export function useGoals(includeArchived: boolean) {
  return useQuery({
    queryKey: goalKeys.list(includeArchived),
    queryFn: () => getGoals(includeArchived),
  })
}

export function useGoal(id: string | undefined) {
  return useQuery({
    queryKey: goalKeys.detail(id ?? ''),
    queryFn: () => getGoal(id ?? ''),
    enabled: id !== undefined,
  })
}

export function useGoalProjection(id: string | undefined) {
  return useQuery({
    queryKey: goalKeys.projection(id ?? ''),
    queryFn: () => getGoalProjection(id ?? ''),
    enabled: id !== undefined,
  })
}

export function useGoalHistory(id: string | undefined, params: HistoryParams = {}) {
  return useQuery({
    queryKey: goalKeys.history(id ?? '', params),
    queryFn: () => getGoalHistory(id ?? '', params),
    enabled: id !== undefined,
  })
}

/** The dashboard card's goals; one request serves the whole card. */
export function useGoalDashboard() {
  return useQuery({ queryKey: goalKeys.dashboard(), queryFn: getGoalDashboard })
}

/** The emergency-fund helper's average; fetched only while the helper is open (`enabled`). */
export function useEmergencyFundSuggestion(months: number, enabled: boolean) {
  return useQuery({
    queryKey: goalKeys.suggestion(months),
    queryFn: () => getEmergencyFundSuggestion(months),
    enabled,
  })
}

/** The non-archived goals a product is allocated to (the product detail page's chips). */
export function useProductGoalAllocations(productId: string | undefined) {
  return useQuery({
    queryKey: goalKeys.forProduct(productId ?? ''),
    queryFn: () => getProductGoalAllocations(productId ?? ''),
    enabled: productId !== undefined,
  })
}

// Every successful mutation invalidates all queries (ADR 0016), which covers the goals, the
// dashboard, the projection and the per-product allocations; nothing is patched.

export function useCreateGoal() {
  return useMutation({ mutationFn: createGoal })
}

export function useEditGoal() {
  return useMutation({
    mutationFn: ({ id, ...request }: UpdateGoalRequest & { id: string }) => editGoal(id, request),
  })
}

export function useSetGoalShowOnDashboard() {
  return useMutation({
    mutationFn: ({ id, showOnDashboard }: { id: string; showOnDashboard: boolean }) =>
      setGoalShowOnDashboard(id, showOnDashboard),
  })
}

export function useDeleteGoal() {
  return useMutation({ mutationFn: (id: string) => deleteGoal(id) })
}

export function useReplaceGoalAllocations() {
  return useMutation({
    mutationFn: ({ id, ...request }: ReplaceAllocationsRequest & { id: string }) =>
      replaceGoalAllocations(id, request),
  })
}
