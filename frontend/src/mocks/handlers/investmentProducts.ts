import { http, HttpResponse } from 'msw'
import type { InvestmentHolding } from '../../api/investments/investmentHoldings'
import type { InvestmentProduct } from '../../api/investments/investmentProducts'
import { investmentHoldingsStore } from './investmentHoldings'
import { createStore } from '../store'

/**
 * Seed data returned by the default `GET /api/investment-products` handler below (F022 spec: pure
 * taxonomy). "Tesouro Selic 2029" has a holding with no history (deletable), "Bitcoin" has a
 * holding with some (`hasHistory`, close-only) and `needsSnapshot` (a buy newer than its latest
 * snapshot, see `seedBitcoinBuyTransfer`), and "Old CDB" has a closed holding - see
 * `seedInvestmentHoldings` in `investmentHoldings.ts` for the per-account details.
 */
export const seedInvestmentProducts: InvestmentProduct[] = [
  {
    id: 'iprod-selic',
    investmentCategoryId: 'icat-fixed',
    investmentSubcategoryId: 'isub-selic',
    name: 'Tesouro Selic 2029',
    additionalNotes: null,
  },
  {
    id: 'iprod-btc',
    investmentCategoryId: 'icat-crypto',
    investmentSubcategoryId: null,
    name: 'Bitcoin',
    additionalNotes: null,
  },
  {
    id: 'iprod-old',
    investmentCategoryId: 'icat-fixed',
    investmentSubcategoryId: 'isub-cdb',
    name: 'Old CDB',
    additionalNotes: null,
  },
]

const PRODUCTS_URL = '/api/investment-products'

interface ProductRequestBody {
  accountId: string
  investmentCategoryId: string
  investmentSubcategoryId?: string | null
  name: string
  additionalNotes?: string | null
}

/** Shared with the snapshot/holding handlers. */
export const investmentProductsStore = createStore(seedInvestmentProducts)
const products = investmentProductsStore

/**
 * Default success-path handlers for the investment products endpoints (F022 spec: pure taxonomy),
 * backed by an in-memory store restored after each test (see `categories.ts`). Create is a
 * two-write use case like the real backend: it also creates the product's first holding.
 */
export const investmentProductsHandlers = [
  http.get(PRODUCTS_URL, () => HttpResponse.json(products.list())),

  http.get(`${PRODUCTS_URL}/:id`, ({ params }) => {
    const product = products.find(params.id as string)
    if (!product) return new HttpResponse(null, { status: 404 })
    return HttpResponse.json(product)
  }),

  http.post(PRODUCTS_URL, async ({ request }) => {
    const body = (await request.json()) as ProductRequestBody
    const created = products.add({
      id: products.nextId('iprod'),
      investmentCategoryId: body.investmentCategoryId,
      investmentSubcategoryId: body.investmentSubcategoryId ?? null,
      name: body.name,
      additionalNotes: body.additionalNotes ?? null,
    })
    const holding: InvestmentHolding = {
      id: investmentHoldingsStore.nextId('iholding'),
      productId: created.id,
      accountId: body.accountId,
      closedDate: null,
      closed: false,
      additionalNotes: null,
      hasHistory: false,
      needsSnapshot: false,
      latestSnapshot: null,
    }
    investmentHoldingsStore.add(holding)
    return HttpResponse.json(created, { status: 201 })
  }),

  http.patch(`${PRODUCTS_URL}/:id`, async ({ request, params }) => {
    const body = (await request.json()) as ProductRequestBody
    const updated = products.replace(params.id as string, (row) => ({
      ...row,
      investmentCategoryId: body.investmentCategoryId,
      investmentSubcategoryId: body.investmentSubcategoryId ?? null,
      name: body.name,
      additionalNotes: body.additionalNotes ?? null,
    }))
    return updated ? HttpResponse.json(updated) : new HttpResponse(null, { status: 404 })
  }),

  http.delete(`${PRODUCTS_URL}/:id`, ({ params }) => {
    const id = params.id as string
    if (investmentHoldingsStore.list().some((h) => h.productId === id)) {
      return new HttpResponse(null, { status: 409 })
    }
    products.remove(id)
    return new HttpResponse(null, { status: 204 })
  }),
]

/**
 * `409` variants (applied via `server.use(...)`): the backend sends no message text, so the
 * bodies are placeholders and what the UI shows comes from the client's own `conflictMessage`.
 */
export const investmentProductDeleteConflictHandler = http.delete(`${PRODUCTS_URL}/:id`, () =>
  HttpResponse.json({ message: 'Product still has a holding' }, { status: 409 }),
)

/** Duplicate name, a non-investment account, or a mismatched sub-category. */
export const investmentProductCreateConflictHandler = http.post(PRODUCTS_URL, () =>
  HttpResponse.json({ message: 'Conflict' }, { status: 409 }),
)

export const investmentProductEditConflictHandler = http.patch(`${PRODUCTS_URL}/:id`, () =>
  HttpResponse.json({ message: 'Conflict' }, { status: 409 }),
)
