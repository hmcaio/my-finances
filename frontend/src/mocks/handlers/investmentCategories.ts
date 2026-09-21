import { http, HttpResponse } from 'msw'
import type { InvestmentCategory } from '../../api/investmentCategories'

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

interface CategoryRequestBody {
  name: string
}

/**
 * Default success-path handlers for the investment categories endpoints (F008's REST API). Same
 * request-echoing approach as `categories.ts`: no mutation of the seed, so every test starts from
 * the same fixture regardless of execution order.
 */
export const investmentCategoriesHandlers = [
  http.get(CATEGORIES_URL, () => HttpResponse.json(seedInvestmentCategories)),

  http.post(CATEGORIES_URL, async ({ request }) => {
    const body = (await request.json()) as CategoryRequestBody
    const created: InvestmentCategory = { id: 'icat-new', name: body.name, subcategories: [] }
    return HttpResponse.json(created, { status: 201 })
  }),

  http.patch(`${CATEGORIES_URL}/:id`, async ({ request, params }) => {
    const body = (await request.json()) as CategoryRequestBody
    const existing = seedInvestmentCategories.find((category) => category.id === params.id)
    const updated: InvestmentCategory = {
      id: params.id as string,
      name: body.name,
      subcategories: existing?.subcategories ?? [],
    }
    return HttpResponse.json(updated)
  }),

  http.delete(`${CATEGORIES_URL}/:id`, () => new HttpResponse(null, { status: 204 })),
]

/**
 * `409` variant for deleting a category that still has sub-categories or products
 * (`InvestmentCategoryInUseException`) - applied via `server.use(...)`. The backend sends no
 * message text, so the body is a placeholder: what the UI shows comes from the client's own
 * `conflictMessage`.
 */
export const investmentCategoryDeleteConflictHandler = http.delete(`${CATEGORIES_URL}/:id`, () =>
  HttpResponse.json({ message: 'Investment category is in use' }, { status: 409 }),
)

/** `409` variants for the duplicate-name case on create/rename. */
export const investmentCategoryCreateConflictHandler = http.post(CATEGORIES_URL, () =>
  HttpResponse.json({ message: 'Name already exists' }, { status: 409 }),
)

export const investmentCategoryRenameConflictHandler = http.patch(`${CATEGORIES_URL}/:id`, () =>
  HttpResponse.json({ message: 'Name already exists' }, { status: 409 }),
)
