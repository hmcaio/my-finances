import { useMutation, useQuery } from '@tanstack/react-query'
import { API_KEY_ROOT } from '../core/queryClient'
import {
  createBudget,
  getBudgetReport,
  getBudgets,
  setBudgetCap,
  type UpdateBudgetCapRequest,
} from './budgets'

export const budgetKeys = {
  all: [API_KEY_ROOT, 'budgets'] as const,
  list: () => [...budgetKeys.all, 'list'] as const,
  report: (month: string) => [...budgetKeys.all, 'report', month] as const,
}

export function useBudgets() {
  return useQuery({ queryKey: budgetKeys.list(), queryFn: getBudgets })
}

/** Budget-vs-actual for one month (`YYYY-MM`). */
export function useBudgetReport(month: string) {
  return useQuery({ queryKey: budgetKeys.report(month), queryFn: () => getBudgetReport(month) })
}

export function useCreateBudget() {
  return useMutation({ mutationFn: createBudget })
}

export function useSetBudgetCap() {
  return useMutation({
    mutationFn: ({ id, ...request }: UpdateBudgetCapRequest & { id: string }) =>
      setBudgetCap(id, request),
  })
}
