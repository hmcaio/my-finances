import { http, HttpResponse } from 'msw'
import type { AllocationRow } from '../../api/investments/investmentAllocation'
import { seedInvestmentAccount } from './accounts'

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
    accountId: null,
    accountName: null,
    totalValue: 900,
    needsSnapshot: false,
  },
  {
    categoryId: 'icat-fixed',
    categoryName: 'Fixed Income',
    subcategoryId: null,
    subcategoryName: null,
    accountId: null,
    accountName: null,
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
    accountId: null,
    accountName: null,
    totalValue: 900,
    needsSnapshot: false,
  },
  {
    categoryId: 'icat-fixed',
    categoryName: 'Fixed Income',
    subcategoryId: 'isub-cdb',
    subcategoryName: 'CDB',
    accountId: null,
    accountName: null,
    totalValue: 1000,
    needsSnapshot: true,
  },
  {
    categoryId: 'icat-fixed',
    categoryName: 'Fixed Income',
    subcategoryId: 'isub-selic',
    subcategoryName: 'Tesouro Selic',
    accountId: null,
    accountName: null,
    totalValue: 2000,
    needsSnapshot: false,
  },
]

/**
 * The same portfolio with `groupBy=ACCOUNT` (F023): every holding lives in the one seeded
 * INVESTMENT account, so its total is the same 3900 the category/sub-category rows sum to.
 */
export const seedAllocationByAccount: AllocationRow[] = [
  {
    categoryId: null,
    categoryName: null,
    subcategoryId: null,
    subcategoryName: null,
    accountId: seedInvestmentAccount.id,
    accountName: seedInvestmentAccount.name,
    totalValue: 3900,
    needsSnapshot: true,
  },
]

export const investmentAllocationHandlers = [
  http.get('/api/investments/allocation', ({ request }) => {
    const groupBy = new URL(request.url).searchParams.get('groupBy') ?? 'CATEGORY'
    if (groupBy === 'SUBCATEGORY') return HttpResponse.json(seedAllocationBySubcategory)
    if (groupBy === 'ACCOUNT') return HttpResponse.json(seedAllocationByAccount)
    return HttpResponse.json(seedAllocationByCategory)
  }),
]
