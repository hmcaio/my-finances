import { http, HttpResponse } from 'msw'
import type { InvestmentCategory } from '../../api/investments/investmentCategories'
import { createStore } from '../store'

/**
 * Seed data returned by the default `GET /api/investment-categories` handler below, in the
 * backend's order (both levels sorted by name). Exported so tests can assert against it instead of
 * duplicating the fixture. "Crypto" has no sub-categories, like the real seed.
 */
export const seedInvestmentCategories: InvestmentCategory[] = [
  { id: 'icat-crypto', name: 'Crypto', subcategories: [] },
  {
    id: 'icat-fixed',
    name: 'Fixed Income',
    subcategories: [
      { id: 'isub-cdb', name: 'CDB' },
      { id: 'isub-selic', name: 'Tesouro Selic' },
    ],
  },
  {
    id: 'icat-variable',
    name: 'Variable Income',
    subcategories: [{ id: 'isub-etfs', name: 'ETFs' }],
  },
]

const CATEGORIES_URL = '/api/investment-categories'

/** Read-only, like the backend store it mirrors (F034/ADR 0028) - no handler writes through it. */
export const investmentCategoriesStore = createStore(seedInvestmentCategories)

const byName = <T extends { name: string }>(a: T, b: T) =>
  a.name.localeCompare(b.name, undefined, { sensitivity: 'base' })

/** The backend's order: both levels sorted by name. */
function sortedCategories(): InvestmentCategory[] {
  return investmentCategoriesStore
    .list()
    .map((c) => ({ ...c, subcategories: [...c.subcategories].sort(byName) }))
    .sort(byName)
}

/**
 * Default success-path handler for the investment categories endpoint (F008's REST API), backed
 * by an in-memory store restored after each test (see `categories.ts`).
 *
 * F034/ADR 0028: read-only - the POST/PATCH/DELETE handlers and their 409 conflict variants (here
 * and on the now-deleted standalone investment-subcategories endpoints) were removed along with
 * the backend's write endpoints; every row (both levels) is fixed, migration-seeded content.
 */
export const investmentCategoriesHandlers = [
  http.get(CATEGORIES_URL, () => HttpResponse.json(sortedCategories())),
]
