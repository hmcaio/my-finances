import { apiClient } from '../core/client'
import { unwrap } from '../core/apiError'
import type { components } from '../generated/schema'

/** `ACTIVE`/`ACHIEVED`/`ARCHIVED`, set manually (PRD S5.16, F039). */
export type GoalStatus = 'ACTIVE' | 'ACHIEVED' | 'ARCHIVED'

/** One product allocated to a goal, with its name for display. */
export interface GoalAllocation {
  productId: string
  productName: string
  percentage: number
}

/**
 * A goal as the list, the detail and every write return it (PRD S5.16, F039, ADR 0034). Amounts
 * are in today's money. `progressPercent` may exceed 100; `targetReached` is the automatic
 * indicator (allocated value at least the target) and never changes `status`. The base scenario
 * gives `projectedShortfall` for a dated goal (`<= 0` means on track) or `monthsToTarget` for a
 * dateless one (`null`: not within 50 years).
 */
export interface Goal {
  id: string
  name: string
  notes: string | null
  targetAmount: number
  targetDate: string | null
  plannedMonthlyContribution: number
  returnPessimistic: number
  returnBase: number
  returnOptimistic: number
  status: GoalStatus
  showOnDashboard: boolean
  allocatedValue: number
  progressPercent: number
  targetReached: boolean
  projectedShortfall: number | null
  monthsToTarget: number | null
  allocations: GoalAllocation[]
}

/** One goal on the dashboard card, from the base scenario. */
export interface GoalSummary {
  id: string
  name: string
  targetAmount: number
  targetDate: string | null
  allocatedValue: number
  progressPercent: number
  targetReached: boolean
  projectedShortfall: number | null
  monthsToTarget: number | null
}

export type ProjectionScenarioName = 'PESSIMISTIC' | 'BASE' | 'OPTIMISTIC'

/** One month of a projected or historical series; `month` is `YYYY-MM`. */
export interface GoalPoint {
  month: string
  value: number
}

/**
 * One scenario of a projection. Dated goal: `projectedValueAtTargetDate`, `shortfallAtTargetDate`
 * (negative = surplus) and `requiredMonthlyContribution` (`null` when the target month is already
 * here and the target is not met), `monthsToTarget` `null`. Dateless goal: only `monthsToTarget`
 * (`null` = not within 50 years).
 */
export interface ScenarioProjection {
  scenario: ProjectionScenarioName
  annualReturn: number
  points: GoalPoint[]
  projectedValueAtTargetDate: number | null
  shortfallAtTargetDate: number | null
  requiredMonthlyContribution: number | null
  monthsToTarget: number | null
}

export interface GoalProjection {
  allocatedValue: number
  latestSnapshotDate: string | null
  scenarios: ScenarioProjection[]
}

export interface EmergencyFundSuggestion {
  months: number
  averageMonthlyExpense: number
  suggestedTarget: number
  monthsAveraged: number
}

/** A non-archived goal an investment product is allocated to. */
export interface ProductGoalAllocation {
  goalId: string
  goalName: string
  percentage: number
}

export type CreateGoalRequest = components['schemas']['CreateGoalRequest']
export type UpdateGoalRequest = components['schemas']['UpdateGoalRequest']
export type ReplaceAllocationsRequest = components['schemas']['ReplaceAllocationsRequest']

export interface HistoryParams {
  /** `YYYY-MM`; defaults to eleven months before `to` on the backend. */
  from?: string
  /** `YYYY-MM`; defaults to the current month. */
  to?: string
}

/**
 * Lists the goals in the standard order (dated first by date, then dateless by name), each with
 * its allocations and where it stands. Archived goals only with `includeArchived`.
 */
export async function getGoals(includeArchived = false): Promise<Goal[]> {
  return unwrap(apiClient.get<Goal[]>('/goals', { params: { includeArchived } }))
}

/** One goal, archived or not. `404` `goal-not-found` for an unknown id. */
export async function getGoal(id: string): Promise<Goal> {
  return unwrap(apiClient.get<Goal>(`/goals/${id}`))
}

/** `400` for a domain violation or a target date that is not after today. */
export async function createGoal(request: CreateGoalRequest): Promise<Goal> {
  return unwrap(apiClient.post<Goal>('/goals', request))
}

/**
 * Full-replace edit, including the status (also how a goal is archived or restored). `409`
 * `goal-allocation-exceeds-product` when restoring an archived goal whose percentages no longer
 * fit.
 */
export async function editGoal(id: string, request: UpdateGoalRequest): Promise<Goal> {
  return unwrap(apiClient.patch<Goal>(`/goals/${id}`, request))
}

/** `409` `goal-archived` when turning it on for an archived goal. */
export async function setGoalShowOnDashboard(id: string, showOnDashboard: boolean): Promise<Goal> {
  return unwrap(apiClient.patch<Goal>(`/goals/${id}/show-on-dashboard`, { showOnDashboard }))
}

/** Hard delete; the goal's allocations go with it. */
export async function deleteGoal(id: string): Promise<void> {
  await unwrap(apiClient.delete<void>(`/goals/${id}`))
}

/**
 * Replaces the goal's whole allocation set (an empty list clears it). `409`
 * `goal-allocation-exceeds-product` when a product would pass 100% across non-archived goals,
 * `goal-archived` for an archived goal.
 */
export async function replaceGoalAllocations(
  id: string,
  request: ReplaceAllocationsRequest,
): Promise<void> {
  await unwrap(apiClient.put<unknown>(`/goals/${id}/allocations`, request))
}

export async function getGoalProjection(id: string): Promise<GoalProjection> {
  return unwrap(apiClient.get<GoalProjection>(`/goals/${id}/projection`))
}

/** The goal's monthly values as of its current allocation. */
export async function getGoalHistory(id: string, params: HistoryParams = {}): Promise<GoalPoint[]> {
  return unwrap(apiClient.get<GoalPoint[]>(`/goals/${id}/history`, { params }))
}

/** The goals flagged to show on the dashboard, from the base scenario. */
export async function getGoalDashboard(): Promise<GoalSummary[]> {
  return unwrap(apiClient.get<GoalSummary[]>('/goals/dashboard'))
}

/** `months` x the average monthly expense of the last 12 full months (1..120). */
export async function getEmergencyFundSuggestion(months: number): Promise<EmergencyFundSuggestion> {
  return unwrap(
    apiClient.get<EmergencyFundSuggestion>('/goals/emergency-fund-suggestion', {
      params: { months },
    }),
  )
}

/** The non-archived goals an investment product is allocated to. */
export async function getProductGoalAllocations(
  productId: string,
): Promise<ProductGoalAllocation[]> {
  return unwrap(
    apiClient.get<ProductGoalAllocation[]>(`/investment-products/${productId}/goal-allocations`),
  )
}
