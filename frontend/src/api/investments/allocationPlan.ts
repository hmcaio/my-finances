import { apiClient } from '../core/client'
import { unwrap } from '../core/apiError'
import type { components } from '../generated/schema'

/** One entry of an {@link AllocationPlanVersion} (F026, ADR 0023): a target percentage for an FII. */
export interface AllocationPlanEntry {
  investmentProductId: string
  targetPercentage: number
}

/**
 * The allocation plan version effective for a month, or its history (F026, ADR 0023): entries
 * must sum to exactly 100%, every entry's product classified under the "REITs (FIIs)"
 * sub-category.
 */
export interface AllocationPlanVersion {
  id: string
  planId: string
  entries: AllocationPlanEntry[]
  /** `yyyy-MM`. */
  effectiveFrom: string
}

export type SetAllocationPlanRequest = components['schemas']['SetAllocationPlanRequest']

/** The version effective for the current month, or `null` if no allocation has ever been set. */
export async function getCurrentAllocationPlan(): Promise<AllocationPlanVersion | null> {
  const response = await apiClient.get<AllocationPlanVersion>('/fii/allocation-plan', {
    validateStatus: (status) => status === 200 || status === 204,
  })
  return response.status === 204 ? null : response.data
}

/** Every version ever set, oldest first, or an empty list if none has ever been set. */
export async function getAllocationPlanVersions(): Promise<AllocationPlanVersion[]> {
  return unwrap(apiClient.get<AllocationPlanVersion[]>('/fii/allocation-plan/versions'))
}

/**
 * Creates a new version, or replaces the existing one for `effectiveFrom` if one already exists
 * (same-month correction). `type: urn:my-finances:error:allocation-plan-entry-not-fii` (409) means
 * a product isn't classified under the FII sub-category; `allocation-plan-sum-invalid`/
 * `allocation-plan-duplicate-product` (both 400) distinguish the entries not summing to exactly
 * 100% from the same product appearing twice (F031/ADR 0026 - `apiError.ts`'s
 * `PROBLEM_TYPE_MESSAGES` covers all three, no call-site override needed).
 */
export async function setAllocationPlan(
  request: SetAllocationPlanRequest,
): Promise<AllocationPlanVersion> {
  return unwrap(apiClient.put<AllocationPlanVersion>('/fii/allocation-plan', request))
}
