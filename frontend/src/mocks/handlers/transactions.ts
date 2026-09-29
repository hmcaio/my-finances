import { http, HttpResponse } from 'msw'
import type {
  CategorySpend,
  FuelType,
  Transaction,
  TransactionType,
} from '../../api/transactions/transactions'
import { seedCategories } from './categories'
import { createStore } from '../store'

/**
 * Seed data returned by the default `GET /api/transactions` handler below. Spans both seed
 * accounts (`acct-1` open, `acct-2` closed - see `accounts.ts`) and both seed categories (`cat-1`
 * expense, `cat-2` income - see `categories.ts`) so filter tests have something to discriminate
 * on. Exported so tests can assert against it directly (F015 spec's F002 backfill pattern).
 */
/** A transaction with no fuel details (every field F024 added, all `null`) - the shape every
 * non-fuel seed/created row shares. */
const NO_FUEL_DETAILS = {
  vehicleId: null,
  fuelType: null,
  liters: null,
  pricePerLiter: null,
  kmSinceLastFill: null,
  odometer: null,
  kmPerLiter: null,
  amountPerKm: null,
  litersPerKm: null,
} as const

export const seedTransactions: Transaction[] = [
  {
    id: 'txn-1',
    date: '2026-01-05',
    amount: 42.5,
    categoryId: 'cat-1',
    type: 'EXPENSE',
    accountId: 'acct-1',
    paymentMethodId: 'pm-1',
    recurringTemplateVersionId: null,
    description: 'Weekly groceries',
    additionalNotes: null,
    ...NO_FUEL_DETAILS,
  },
  {
    id: 'txn-2',
    date: '2026-01-20',
    amount: 3000,
    categoryId: 'cat-2',
    type: 'INCOME',
    accountId: 'acct-1',
    paymentMethodId: 'pm-2',
    recurringTemplateVersionId: null,
    description: 'Monthly salary deposit',
    additionalNotes: 'Direct deposit from employer',
    ...NO_FUEL_DETAILS,
  },
]

/**
 * Named lookup for the seeded groceries transaction (`txn-1`), so call sites identify it by name
 * instead of indexing into `seedTransactions` by position (frontend test audit's F6 finding).
 */
export const seedGroceriesTransaction = seedTransactions.find(
  (t) => t.description === 'Weekly groceries',
)!

/** Seed data returned by the default `GET /api/transactions/spend-by-category` handler. */
export const seedCategorySpend: CategorySpend[] = [{ categoryId: 'cat-1', total: 42.5 }]

const TRANSACTIONS_URL = '/api/transactions'

interface TransactionRequestBody {
  date: string
  amount: number
  categoryId: string
  accountId: string
  paymentMethodId: string
  description: string
  additionalNotes?: string
  vehicleId?: string
  fuelType?: FuelType
  liters?: number
  pricePerLiter?: number
  kmSinceLastFill?: number
  odometer?: number
}

function typeForCategory(categoryId: string): TransactionType {
  return seedCategories.find((category) => category.id === categoryId)?.type ?? 'EXPENSE'
}

/**
 * The fuel fields + computed ratios for a request body (F024): mirrors `FuelRatiosQuery` - every
 * ratio `null` unless `kmSinceLastFill` is given, everything `null` when there's no `vehicleId`.
 */
function fuelFieldsFrom(body: TransactionRequestBody) {
  if (!body.vehicleId) return NO_FUEL_DETAILS
  const km = body.kmSinceLastFill
  const liters = body.liters ?? 0
  return {
    vehicleId: body.vehicleId,
    fuelType: body.fuelType ?? null,
    liters: body.liters ?? null,
    pricePerLiter: body.pricePerLiter ?? null,
    kmSinceLastFill: km ?? null,
    odometer: body.odometer ?? null,
    kmPerLiter: km ? km / liters : null,
    amountPerKm: km ? body.amount / km : null,
    litersPerKm: km ? liters / km : null,
  }
}

const transactions = createStore(seedTransactions)

/**
 * Default success-path handlers for every transactions endpoint (F004's REST API), backed by an
 * in-memory store restored after each test (see `categories.ts`). List applies the same filter
 * dimensions the real backend does and paginates with the same page/size defaults (page 0,
 * size 20), so `TransactionsPage`'s filter/pagination UI has real behavior to test against without
 * a database. Type is derived from the category, like the real backend does.
 */
export const transactionsHandlers = [
  // Registered before the `/:id` route so `spend-by-category` isn't taken for an id.
  http.get(`${TRANSACTIONS_URL}/spend-by-category`, () => HttpResponse.json(seedCategorySpend)),

  http.get(TRANSACTIONS_URL, ({ request }) => {
    const url = new URL(request.url)
    const dateFrom = url.searchParams.get('dateFrom')
    const dateTo = url.searchParams.get('dateTo')
    const categoryId = url.searchParams.get('categoryId')
    const accountId = url.searchParams.get('accountId')
    const paymentMethodId = url.searchParams.get('paymentMethodId')
    const page = Number(url.searchParams.get('page') ?? '0')
    const size = Number(url.searchParams.get('size') ?? '20')

    const filtered = transactions
      .list()
      .filter((t) => !dateFrom || t.date >= dateFrom)
      .filter((t) => !dateTo || t.date <= dateTo)
      .filter((t) => !categoryId || t.categoryId === categoryId)
      .filter((t) => !accountId || t.accountId === accountId)
      .filter((t) => !paymentMethodId || t.paymentMethodId === paymentMethodId)
      .sort((a, b) => (a.date < b.date ? 1 : -1))

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

  http.get(`${TRANSACTIONS_URL}/:id`, ({ params }) => {
    const transaction = transactions.find(params.id as string)
    if (!transaction) return new HttpResponse(null, { status: 404 })
    return HttpResponse.json(transaction)
  }),

  http.post(TRANSACTIONS_URL, async ({ request }) => {
    const body = (await request.json()) as TransactionRequestBody
    const created = transactions.add({
      id: transactions.nextId('txn'),
      date: body.date,
      amount: body.amount,
      categoryId: body.categoryId,
      type: typeForCategory(body.categoryId),
      accountId: body.accountId,
      paymentMethodId: body.paymentMethodId,
      recurringTemplateVersionId: null,
      description: body.description,
      additionalNotes: body.additionalNotes ?? null,
      ...fuelFieldsFrom(body),
    })
    return HttpResponse.json(created, { status: 201 })
  }),

  http.patch(`${TRANSACTIONS_URL}/:id`, async ({ request, params }) => {
    const body = (await request.json()) as TransactionRequestBody
    const updated = transactions.replace(params.id as string, () => ({
      id: params.id as string,
      date: body.date,
      amount: body.amount,
      categoryId: body.categoryId,
      type: typeForCategory(body.categoryId),
      accountId: body.accountId,
      paymentMethodId: body.paymentMethodId,
      recurringTemplateVersionId: null,
      description: body.description,
      additionalNotes: body.additionalNotes ?? null,
      ...fuelFieldsFrom(body),
    }))
    return updated ? HttpResponse.json(updated) : new HttpResponse(null, { status: 404 })
  }),

  http.delete(`${TRANSACTIONS_URL}/:id`, ({ params }) => {
    transactions.remove(params.id as string)
    return new HttpResponse(null, { status: 204 })
  }),

  // F024: GET /api/vehicles/:id/fuel-history - registered here (not `vehicles.ts`) since it
  // returns Transaction-shaped rows filtered from this file's own store.
  http.get('/api/vehicles/:id/fuel-history', ({ request, params }) => {
    const url = new URL(request.url)
    const from = url.searchParams.get('from')
    const to = url.searchParams.get('to')
    const rows = transactions
      .list()
      .filter((t) => t.vehicleId === params.id)
      .filter((t) => !from || t.date >= from)
      .filter((t) => !to || t.date <= to)
      .sort((a, b) => (a.date < b.date ? -1 : 1))
    return HttpResponse.json(rows)
  }),
]

/**
 * `409` variant for the closed-account-rejection case (F004 spec's "reject inserting a
 * transaction against a closed Account") - applied via `server.use(...)` in tests that exercise
 * that path, same pattern as `accountAlreadyClosedConflictHandler`.
 */
export const transactionClosedAccountConflictHandler = http.post(TRANSACTIONS_URL, () =>
  HttpResponse.json({ message: 'Account is closed' }, { status: 409 }),
)
