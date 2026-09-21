import { http, HttpResponse } from 'msw'
import type { InvestmentProduct } from '../../api/investmentProducts'
import { seedInvestmentAccount } from './accounts'

/**
 * Seed data returned by the default `GET /api/investment-products` handler below, all inside the
 * seeded INVESTMENT account. "Tesouro Selic 2029" has no history (deletable), "Bitcoin" has some
 * (`hasHistory`, so close-only) and is classified by category only, and "Old CDB" is closed.
 */
export const seedInvestmentProducts: InvestmentProduct[] = [
  {
    id: 'iprod-selic',
    accountId: seedInvestmentAccount.id,
    investmentCategoryId: 'icat-fixed',
    investmentSubcategoryId: 'isub-selic',
    name: 'Tesouro Selic 2029',
    closedDate: null,
    closed: false,
    hasHistory: false,
  },
  {
    id: 'iprod-btc',
    accountId: seedInvestmentAccount.id,
    investmentCategoryId: 'icat-crypto',
    investmentSubcategoryId: null,
    name: 'Bitcoin',
    closedDate: null,
    closed: false,
    hasHistory: true,
  },
  {
    id: 'iprod-old',
    accountId: seedInvestmentAccount.id,
    investmentCategoryId: 'icat-fixed',
    investmentSubcategoryId: 'isub-cdb',
    name: 'Old CDB',
    closedDate: '2026-03-01',
    closed: true,
    hasHistory: false,
  },
]

const PRODUCTS_URL = '/api/investment-products'

interface ProductRequestBody {
  accountId: string
  investmentCategoryId: string
  investmentSubcategoryId?: string | null
  name: string
}

/**
 * Default success-path handlers for the investment products endpoints (F008's REST API),
 * request-echoing like the other aggregates' handlers: no mutation of the seed.
 */
export const investmentProductsHandlers = [
  http.get(PRODUCTS_URL, ({ request }) => {
    const accountId = new URL(request.url).searchParams.get('accountId')
    const products = accountId
      ? seedInvestmentProducts.filter((p) => p.accountId === accountId)
      : seedInvestmentProducts
    return HttpResponse.json(products)
  }),

  http.get(`${PRODUCTS_URL}/:id`, ({ params }) => {
    const product = seedInvestmentProducts.find((p) => p.id === params.id)
    if (!product) return new HttpResponse(null, { status: 404 })
    return HttpResponse.json(product)
  }),

  http.post(PRODUCTS_URL, async ({ request }) => {
    const body = (await request.json()) as ProductRequestBody
    const created: InvestmentProduct = {
      id: 'iprod-new',
      accountId: body.accountId,
      investmentCategoryId: body.investmentCategoryId,
      investmentSubcategoryId: body.investmentSubcategoryId ?? null,
      name: body.name,
      closedDate: null,
      closed: false,
      hasHistory: false,
    }
    return HttpResponse.json(created, { status: 201 })
  }),

  http.patch(`${PRODUCTS_URL}/:id`, async ({ request, params }) => {
    const body = (await request.json()) as ProductRequestBody
    const existing = seedInvestmentProducts.find((p) => p.id === params.id)
    const updated: InvestmentProduct = {
      id: params.id as string,
      accountId: body.accountId,
      investmentCategoryId: body.investmentCategoryId,
      investmentSubcategoryId: body.investmentSubcategoryId ?? null,
      name: body.name,
      closedDate: existing?.closedDate ?? null,
      closed: existing?.closed ?? false,
      hasHistory: existing?.hasHistory ?? false,
    }
    return HttpResponse.json(updated)
  }),

  http.post(`${PRODUCTS_URL}/:id/close`, ({ params }) => {
    const existing = seedInvestmentProducts.find((p) => p.id === params.id)
    const closed: InvestmentProduct = {
      id: params.id as string,
      accountId: existing?.accountId ?? seedInvestmentAccount.id,
      investmentCategoryId: existing?.investmentCategoryId ?? 'icat-fixed',
      investmentSubcategoryId: existing?.investmentSubcategoryId ?? null,
      name: existing?.name ?? 'Product',
      closedDate: '2026-09-15',
      closed: true,
      hasHistory: existing?.hasHistory ?? false,
    }
    return HttpResponse.json(closed)
  }),

  http.delete(`${PRODUCTS_URL}/:id`, () => new HttpResponse(null, { status: 204 })),
]

/**
 * `409` variants (applied via `server.use(...)`): the backend sends no message text, so the
 * bodies are placeholders and what the UI shows comes from the client's own `conflictMessage`.
 */
export const investmentProductDeleteConflictHandler = http.delete(`${PRODUCTS_URL}/:id`, () =>
  HttpResponse.json({ message: 'Product has history' }, { status: 409 }),
)

export const investmentProductCloseConflictHandler = http.post(`${PRODUCTS_URL}/:id/close`, () =>
  HttpResponse.json({ message: 'Already closed' }, { status: 409 }),
)

/** Duplicate name in the account, a non-investment account, or a mismatched sub-category. */
export const investmentProductCreateConflictHandler = http.post(PRODUCTS_URL, () =>
  HttpResponse.json({ message: 'Conflict' }, { status: 409 }),
)

export const investmentProductEditConflictHandler = http.patch(`${PRODUCTS_URL}/:id`, () =>
  HttpResponse.json({ message: 'Conflict' }, { status: 409 }),
)
