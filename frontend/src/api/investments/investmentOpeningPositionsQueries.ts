import { useMutation, useQuery } from '@tanstack/react-query'
import { API_KEY_ROOT } from '../core/queryClient'
import {
  deleteOpeningPosition,
  getOpeningPosition,
  putOpeningPosition,
  type OpeningPositionRequest,
} from './investmentOpeningPositions'

export const openingPositionKeys = {
  all: [API_KEY_ROOT, 'investment-opening-positions'] as const,
  detail: (holdingId: string) => [...openingPositionKeys.all, 'detail', holdingId] as const,
}

/**
 * The holding's opening position, `null` when it has none (the not-found problem type is mapped
 * in `getOpeningPosition`, so the empty state is data, not an error). No retry: the app's default.
 */
export function useOpeningPosition(holdingId: string | undefined) {
  return useQuery({
    queryKey: openingPositionKeys.detail(holdingId ?? ''),
    queryFn: () =>
      holdingId ? getOpeningPosition(holdingId) : Promise.reject(new Error('Missing holding id.')),
    enabled: holdingId !== undefined,
  })
}

/**
 * Creates or replaces the position. Like every mutation, a success invalidates all queries
 * (ADR 0016), which covers the holding's position, the product's holdings, snapshots, trade lines,
 * the value series and the FII portfolio.
 */
export function useSaveOpeningPosition() {
  return useMutation({
    mutationFn: ({ holdingId, ...request }: OpeningPositionRequest & { holdingId: string }) =>
      putOpeningPosition(holdingId, request),
  })
}

export function useDeleteOpeningPosition() {
  return useMutation({
    mutationFn: (holdingId: string) => deleteOpeningPosition(holdingId),
  })
}
