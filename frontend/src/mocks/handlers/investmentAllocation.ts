import { http, HttpResponse } from 'msw'
import type { AllocationRow } from '../../api/investmentAllocation'

/**
 * Seed rows of `GET /api/investments/allocation?groupBy=CATEGORY`: Fixed Income (stale - a trade
 * is newer than its latest snapshot) and Crypto. Exported so tests can assert against them.
 */
export const seedAllocationByCategory: AllocationRow[] = [
  {
    categoryId: 'icat-crypto',
    categoryName: 'Crypto',
    subcategoryId: null,
    subcategoryName: null,
    totalValue: 900,
    needsSnapshot: false,
  },
  {
    categoryId: 'icat-fixed',
    categoryName: 'Fixed Income',
    subcategoryId: null,
    subcategoryName: null,
    totalValue: 3000,
    needsSnapshot: true,
  },
]

/**
 * The same data with `groupBy=SUBCATEGORY`: Fixed Income splits into CDB and Tesouro Selic (whose
 * totals sum to the category's), Crypto has a null sub-category slice (a product without one).
 */
export const seedAllocationBySubcategory: AllocationRow[] = [
  {
    categoryId: 'icat-crypto',
    categoryName: 'Crypto',
    subcategoryId: null,
    subcategoryName: null,
    totalValue: 900,
    needsSnapshot: false,
  },
  {
    categoryId: 'icat-fixed',
    categoryName: 'Fixed Income',
    subcategoryId: 'isub-cdb',
    subcategoryName: 'CDB',
    totalValue: 1000,
    needsSnapshot: true,
  },
  {
    categoryId: 'icat-fixed',
    categoryName: 'Fixed Income',
    subcategoryId: 'isub-selic',
    subcategoryName: 'Tesouro Selic',
    totalValue: 2000,
    needsSnapshot: false,
  },
]

export const investmentAllocationHandlers = [
  http.get('/api/investments/allocation', ({ request }) => {
    const groupBy = new URL(request.url).searchParams.get('groupBy') ?? 'CATEGORY'
    return HttpResponse.json(
      groupBy === 'SUBCATEGORY' ? seedAllocationBySubcategory : seedAllocationByCategory,
    )
  }),
]
