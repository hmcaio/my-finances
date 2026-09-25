import { http, HttpResponse } from 'msw'
import type { InvestmentProduct } from '../../api/investments/investmentProducts'
import { seedInvestmentAccount } from './accounts'
import { createStore } from '../store'

/**
 * Seed data returned by the default `GET /api/investment-products` handler below, all inside the
 * seeded INVESTMENT account. "Tesouro Selic 2029" has no history (deletable), "Bitcoin" has some
 * (`hasHistory`, so close-only), is classified by category only and `needsSnapshot` (a buy newer
 * than its latest snapshot, see `seedBitcoinBuyTransfer`), and "Old CDB" is closed.
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
    needsSnapshot: false,
    latestSnapshot: null,
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
    needsSnapshot: true,
    latestSnapshot: { date: '2026-08-05', balance: 900 },
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
    needsSnapshot: false,
    latestSnapshot: null,
  },
]

const PRODUCTS_URL = '/api/investment-products'

interface ProductRequestBody {
  accountId: string
  investmentCategoryId: string
  investmentSubcategoryId?: string | null
  name: string
}

/** Shared with the snapshot handlers, which move a product's latest snapshot. */
export const investmentProductsStore = createStore(seedInvestmentProducts)
const products = investmentProductsStore

/**
 * Default success-path handlers for the investment products endpoints (F008's REST API), backed
 * by an in-memory store restored after each test (see `categories.ts`).
 */
export const investmentProductsHandlers = [
  http.get(PRODUCTS_URL, ({ request }) => {
    const accountId = new URL(request.url).searchParams.get('accountId')
    const rows = accountId
      ? products.list().filter((p) => p.accountId === accountId)
      : products.list()
    return HttpResponse.json(rows)
  }),

  http.get(`${PRODUCTS_URL}/:id`, ({ params }) => {
    const product = products.find(params.id as string)
    if (!product) return new HttpResponse(null, { status: 404 })
    return HttpResponse.json(product)
  }),

  http.post(PRODUCTS_URL, async ({ request }) => {
    const body = (await request.json()) as ProductRequestBody
    const created = products.add({
      id: products.nextId('iprod'),
      accountId: body.accountId,
      investmentCategoryId: body.investmentCategoryId,
      investmentSubcategoryId: body.investmentSubcategoryId ?? null,
      name: body.name,
      closedDate: null,
      closed: false,
      hasHistory: false,
      needsSnapshot: false,
      latestSnapshot: null,
    })
    return HttpResponse.json(created, { status: 201 })
  }),

  http.patch(`${PRODUCTS_URL}/:id`, async ({ request, params }) => {
    const body = (await request.json()) as ProductRequestBody
    const updated = products.replace(params.id as string, (row) => ({
      ...row,
      accountId: body.accountId,
      investmentCategoryId: body.investmentCategoryId,
      investmentSubcategoryId: body.investmentSubcategoryId ?? null,
      name: body.name,
    }))
    return updated ? HttpResponse.json(updated) : new HttpResponse(null, { status: 404 })
  }),

  http.post(`${PRODUCTS_URL}/:id/close`, ({ params }) => {
    const closed = products.replace(params.id as string, (row) => ({
      ...row,
      closedDate: '2026-09-15',
      closed: true,
    }))
    return closed ? HttpResponse.json(closed) : new HttpResponse(null, { status: 404 })
  }),

  http.delete(`${PRODUCTS_URL}/:id`, ({ params }) => {
    products.remove(params.id as string)
    return new HttpResponse(null, { status: 204 })
  }),
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
