import { http, HttpResponse } from 'msw'
import type {
  FiiAllocationBasis,
  FiiAllocationGroupBy,
  FiiAllocationRow,
} from '../../api/investments/fiiAllocation'

const ALLOCATION_URL = '/api/fii/allocation'

/**
 * Seed rows for the four basis/groupBy combinations (F026, ADR 0023) - a fixed fixture rather
 * than derived from `seedFiiPortfolio`/the allocation plan store, so each chart's test has a
 * stable, independent fixture to assert against.
 */
export const seedFiiAllocation: Record<string, FiiAllocationRow[]> = {
  'ACTUAL:TICKER': [
    { key: 'iprod-knri11', label: 'KNRI11', totalValue: 12000, percentage: 68.57 },
    { key: 'iprod-hglg11', label: 'HGLG11', totalValue: 5500, percentage: 31.43 },
  ],
  'ACTUAL:SEGMENT': [
    { key: 'iseg-shoppings', label: 'Shoppings', totalValue: 12000, percentage: 68.57 },
    { key: 'iseg-logistica', label: 'Logistica', totalValue: 5500, percentage: 31.43 },
  ],
  'PLANNED:TICKER': [
    { key: 'iprod-knri11', label: 'KNRI11', totalValue: null, percentage: 60 },
    { key: 'iprod-hglg11', label: 'HGLG11', totalValue: null, percentage: 40 },
  ],
  'PLANNED:SEGMENT': [
    { key: 'iseg-shoppings', label: 'Shoppings', totalValue: null, percentage: 60 },
    { key: 'iseg-logistica', label: 'Logistica', totalValue: null, percentage: 40 },
  ],
}

export const fiiAllocationHandlers = [
  http.get(ALLOCATION_URL, ({ request }) => {
    const url = new URL(request.url)
    const basis = url.searchParams.get('basis') as FiiAllocationBasis | null
    const groupBy = url.searchParams.get('groupBy') as FiiAllocationGroupBy | null
    if (!basis || !groupBy) {
      return HttpResponse.json({ message: 'basis and groupBy are required' }, { status: 400 })
    }
    return HttpResponse.json(seedFiiAllocation[`${basis}:${groupBy}`] ?? [])
  }),
]
