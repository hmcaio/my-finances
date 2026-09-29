import { http, HttpResponse } from 'msw'
import type { InvestmentHolding } from '../../api/investments/investmentHoldings'
import type { InvestmentProduct } from '../../api/investments/investmentProducts'
import { investmentHoldingsStore } from './investmentHoldings'
import { createStore } from '../store'

/**
 * Seed data returned by the default `GET /api/investment-products` handler below (F022 spec: pure
 * taxonomy; `closed` added by F023). "Tesouro Selic 2029" has a holding with no history
 * (deletable), "Bitcoin" has a holding with some (`hasHistory`, close-only) and `needsSnapshot` (a
 * buy newer than its latest snapshot, see `seedBitcoinBuyTransfer`), and "Old CDB" has a closed
 * holding (so it's the only one of the three with `closed: true`) - see `seedInvestmentHoldings` in
 * `investmentHoldings.ts` for the per-account details.
 */
export const seedInvestmentProducts: InvestmentProduct[] = [
  {
    id: 'iprod-selic',
    investmentCategoryId: 'icat-fixed',
    investmentSubcategoryId: 'isub-selic',
    name: 'Tesouro Selic 2029',
    additionalNotes: null,
    closed: false,
  },
  {
    id: 'iprod-btc',
    investmentCategoryId: 'icat-crypto',
    investmentSubcategoryId: null,
    name: 'Bitcoin',
    additionalNotes: null,
    closed: false,
  },
  {
    id: 'iprod-old',
    investmentCategoryId: 'icat-fixed',
    investmentSubcategoryId: 'isub-cdb',
    name: 'Old CDB',
    additionalNotes: null,
    closed: true,
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
 * `closed` (F023) is derived from the product's holdings, never trusted from the stored row - a
 * holding can close/reopen through other handlers (`investmentHoldings.ts`), so it's recomputed on
 * every response, the same "computed on read" the real backend does.
 */
function withClosed(product: InvestmentProduct): InvestmentProduct {
  const holdings = investmentHoldingsStore.list().filter((h) => h.productId === product.id)
  const closed = holdings.length === 0 || holdings.every((h) => h.closed)
  return { ...product, closed }
}

/**
 * Default success-path handlers for the investment products endpoints (F022 spec: pure taxonomy;
 * F023 added filtering/pagination and the derived `closed` field), backed by an in-memory store
 * restored after each test (see `categories.ts`). Create is a two-write use case like the real
 * backend: it also creates the product's first holding. Applies the same filter dimensions and
 * pagination defaults (page 0, size 20) as the real backend, so `InvestmentProductsListSection`'s
 * filter/pagination UI has real behavior to test against without a database.
 */
export const investmentProductsHandlers = [
  http.get(PRODUCTS_URL, ({ request }) => {
    const url = new URL(request.url)
    const categoryId = url.searchParams.get('categoryId')
    const subcategoryId = url.searchParams.get('subcategoryId')
    const accountId = url.searchParams.get('accountId')
    const name = url.searchParams.get('name')
    const status = url.searchParams.get('status') ?? 'OPEN'
    const page = Number(url.searchParams.get('page') ?? '0')
    const size = Number(url.searchParams.get('size') ?? '20')

    const filtered = products
      .list()
      .map(withClosed)
      .filter((p) => !categoryId || p.investmentCategoryId === categoryId)
      .filter((p) => !subcategoryId || p.investmentSubcategoryId === subcategoryId)
      .filter(
        (p) =>
          !accountId ||
          investmentHoldingsStore
            .list()
            .some((h) => h.productId === p.id && h.accountId === accountId),
      )
      .filter((p) => !name || p.name.toLowerCase().includes(name.toLowerCase()))
      .filter((p) => status === 'ALL' || (status === 'CLOSED' ? p.closed : !p.closed))
      .sort((a, b) => a.name.localeCompare(b.name))

    const start = page * size
    const content = filtered.slice(start, start + size)

    return HttpResponse.json({
      content,
      page: {
        size,
        number: page,
        totalElements: filtered.length,
        totalPages: Math.max(1, Math.ceil(filtered.length / size)),
      },
    })
  }),

  http.get(`${PRODUCTS_URL}/:id`, ({ params }) => {
    const product = products.find(params.id as string)
    if (!product) return new HttpResponse(null, { status: 404 })
    return HttpResponse.json(withClosed(product))
  }),

  http.post(PRODUCTS_URL, async ({ request }) => {
    const body = (await request.json()) as ProductRequestBody
    const created = products.add({
      id: products.nextId('iprod'),
      investmentCategoryId: body.investmentCategoryId,
      investmentSubcategoryId: body.investmentSubcategoryId ?? null,
      name: body.name,
      additionalNotes: body.additionalNotes ?? null,
      closed: false,
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
    return HttpResponse.json(withClosed(created), { status: 201 })
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
    return updated
      ? HttpResponse.json(withClosed(updated))
      : new HttpResponse(null, { status: 404 })
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
