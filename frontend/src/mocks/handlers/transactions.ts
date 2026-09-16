import { http, HttpResponse } from 'msw'
import type { Transaction, TransactionType } from '../../api/transactions'
import { seedCategories } from './categories'

/**
 * Seed data returned by the default `GET /api/transactions` handler below. Spans both seed
 * accounts (`acct-1` open, `acct-2` closed - see `accounts.ts`) and both seed categories (`cat-1`
 * expense, `cat-2` income - see `categories.ts`) so filter tests have something to discriminate
 * on. Exported so tests can assert against it directly (F015 spec's F002 backfill pattern).
 */
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
    note: 'Weekly groceries',
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
    note: 'Monthly salary deposit',
  },
]

const TRANSACTIONS_URL = '/api/transactions'

interface TransactionRequestBody {
  date: string
  amount: number
  categoryId: string
  accountId: string
  paymentMethodId: string
  note?: string
}

function typeForCategory(categoryId: string): TransactionType {
  return seedCategories.find((category) => category.id === categoryId)?.type ?? 'EXPENSE'
}

/**
 * Default success-path handlers for every transactions endpoint (F004's REST API). List applies
 * the same filter dimensions the real backend does, against `seedTransactions`, and paginates
 * with the same page/size defaults (page 0, size 20) - so `TransactionsPage`'s filter/pagination
 * UI has real behavior to test against without a database. Create/edit echo the request body back
 * (with type derived from the category, like the real backend) rather than mutating
 * `seedTransactions`, so every test starts from the same fixture regardless of execution order.
 */
export const transactionsHandlers = [
  http.get(TRANSACTIONS_URL, ({ request }) => {
    const url = new URL(request.url)
    const dateFrom = url.searchParams.get('dateFrom')
    const dateTo = url.searchParams.get('dateTo')
    const categoryId = url.searchParams.get('categoryId')
    const accountId = url.searchParams.get('accountId')
    const paymentMethodId = url.searchParams.get('paymentMethodId')
    const page = Number(url.searchParams.get('page') ?? '0')
    const size = Number(url.searchParams.get('size') ?? '20')

    const filtered = seedTransactions
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
    const transaction = seedTransactions.find((t) => t.id === params.id)
    if (!transaction) return new HttpResponse(null, { status: 404 })
    return HttpResponse.json(transaction)
  }),

  http.post(TRANSACTIONS_URL, async ({ request }) => {
    const body = (await request.json()) as TransactionRequestBody
    const created: Transaction = {
      id: 'txn-new',
      date: body.date,
      amount: body.amount,
      categoryId: body.categoryId,
      type: typeForCategory(body.categoryId),
      accountId: body.accountId,
      paymentMethodId: body.paymentMethodId,
      recurringTemplateVersionId: null,
      note: body.note ?? null,
    }
    return HttpResponse.json(created, { status: 201 })
  }),

  http.patch(`${TRANSACTIONS_URL}/:id`, async ({ request, params }) => {
    const body = (await request.json()) as TransactionRequestBody
    const updated: Transaction = {
      id: params.id as string,
      date: body.date,
      amount: body.amount,
      categoryId: body.categoryId,
      type: typeForCategory(body.categoryId),
      accountId: body.accountId,
      paymentMethodId: body.paymentMethodId,
      recurringTemplateVersionId: null,
      note: body.note ?? null,
    }
    return HttpResponse.json(updated)
  }),

  http.delete(`${TRANSACTIONS_URL}/:id`, () => new HttpResponse(null, { status: 204 })),
]

/**
 * `409` variant for the closed-account-rejection case (F004 spec's "reject inserting a
 * transaction against a closed Account") - applied via `server.use(...)` in tests that exercise
 * that path, same pattern as `accountAlreadyClosedConflictHandler`.
 */
export const transactionClosedAccountConflictHandler = http.post(TRANSACTIONS_URL, () =>
  HttpResponse.json({ message: 'Account is closed' }, { status: 409 }),
)
