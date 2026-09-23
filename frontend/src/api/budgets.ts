import { apiClient } from './client'
import { unwrap } from './apiError'
import type { components } from './generated/schema'

/**
 * A Budget as returned by the API (PRD S5.6, F006 spec): one per expense category, with its cap as
 * of the current real-world month. `currentCap`/`currentCapEffectiveFrom` are `null` on the rare
 * case where a Budget exists but no version is effective yet (its only version's `effectiveFrom`
 * is still in the future).
 */
export interface Budget {
  id: string
  categoryId: string
  currentCap: number | null
  currentCapEffectiveFrom: string | null
}

/** One row of the budget-vs-actual report (F006 spec's `GET /api/budgets/report`). `cap` is
 * `null` when no version is effective yet for the queried month. */
export interface BudgetReportLine {
  categoryId: string
  cap: number | null
  actual: number
}

export type CreateBudgetRequest = components['schemas']['CreateBudgetRequest']
export type UpdateBudgetCapRequest = components['schemas']['UpdateBudgetCapRequest']

export const CREATE_CONFLICT_MESSAGE =
  'This category cannot be budgeted - it may already have a budget, or not be an expense category.'

/** Fetches every Budget, each with its cap as of the current month (F006 spec). */
export async function getBudgets(): Promise<Budget[]> {
  return unwrap(apiClient.get<Budget[]>('/budgets'))
}

/**
 * Creates a Budget for a category plus its first cap. A `409` covers two distinct cases (a
 * non-expense category, or a category that's already budgeted) - the create form (F006 spec:
 * "pick an unbudgeted expense category") only offers unbudgeted expense categories in the first
 * place, so this is a fallback message for if the picker's own data is stale.
 */
export async function createBudget(request: CreateBudgetRequest): Promise<Budget> {
  return unwrap(apiClient.post<Budget>('/budgets', request), CREATE_CONFLICT_MESSAGE)
}

/**
 * Sets a Budget's cap, effective from the given month (F006 spec's `PATCH .../cap`). No `409` case
 * here - unlike create, the only expected error is an unknown budget id (404).
 */
export async function setBudgetCap(id: string, request: UpdateBudgetCapRequest): Promise<Budget> {
  return unwrap(apiClient.patch<Budget>(`/budgets/${id}/cap`, request))
}

/** Budget-vs-actual for every budgeted category, for a given month (`YYYY-MM`, F006 spec). */
export async function getBudgetReport(month: string): Promise<BudgetReportLine[]> {
  return unwrap(apiClient.get<BudgetReportLine[]>('/budgets/report', { params: { month } }))
}
