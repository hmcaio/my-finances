import { useMutation, useQuery } from '@tanstack/react-query'
import { API_KEY_ROOT } from '../core/queryClient'
import {
  getAllocationPlanVersions,
  getCurrentAllocationPlan,
  setAllocationPlan,
} from './allocationPlan'

export const allocationPlanKeys = {
  all: [API_KEY_ROOT, 'allocation-plan'] as const,
  current: () => [...allocationPlanKeys.all, 'current'] as const,
  versions: () => [...allocationPlanKeys.all, 'versions'] as const,
}

export function useCurrentAllocationPlan() {
  return useQuery({
    queryKey: allocationPlanKeys.current(),
    queryFn: getCurrentAllocationPlan,
  })
}

export function useAllocationPlanVersions() {
  return useQuery({
    queryKey: allocationPlanKeys.versions(),
    queryFn: getAllocationPlanVersions,
  })
}

export function useSetAllocationPlan() {
  return useMutation({ mutationFn: setAllocationPlan })
}
