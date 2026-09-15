import { http, HttpResponse } from 'msw'
import type { Account, AccountType } from '../../api/accounts'

/**
 * Seed data returned by the default `GET /api/accounts` handler below. Exported so tests can
 * assert against it directly instead of duplicating the fixture (F015 spec's F002 backfill,
 * extended here for F003).
 */
export const seedAccounts: Account[] = [
  {
    id: 'acct-1',
    name: 'Itau Checking',
    institution: 'Itau',
    type: 'CHECKING',
    openingBalance: 1000,
    openingBalanceDate: '2026-01-01',
    closedDate: null,
    closed: false,
    balance: 1000,
  },
  {
    id: 'acct-2',
    name: 'Old Savings',
    institution: 'Nubank',
    type: 'SAVINGS',
    openingBalance: 500,
    openingBalanceDate: '2025-01-01',
    closedDate: '2026-01-01',
    closed: true,
    balance: 500,
  },
]

const ACCOUNTS_URL = '/api/accounts'

interface CreateAccountRequestBody {
  name: string
  institution?: string
  type?: AccountType
  openingBalance?: number
  openingBalanceDate?: string
}

interface UpdateAccountRequestBody {
  name: string
  institution?: string
}

/**
 * Default success-path handlers for every accounts endpoint (F003's REST API). Requests are
 * answered purely from the request itself (echoing the body back with a generated/known id)
 * rather than mutating `seedAccounts`, so every test starts from the same fixture regardless of
 * execution order - same approach as `categories.ts`/`paymentMethods.ts`.
 */
export const accountsHandlers = [
  http.get(ACCOUNTS_URL, ({ request }) => {
    const includeClosed = new URL(request.url).searchParams.get('includeClosed') === 'true'
    const accounts = includeClosed ? seedAccounts : seedAccounts.filter((a) => !a.closed)
    return HttpResponse.json(accounts)
  }),

  http.get(`${ACCOUNTS_URL}/:id`, ({ params }) => {
    const account = seedAccounts.find((a) => a.id === params.id)
    if (!account) return new HttpResponse(null, { status: 404 })
    return HttpResponse.json(account)
  }),

  http.post(ACCOUNTS_URL, async ({ request }) => {
    const body = (await request.json()) as CreateAccountRequestBody
    const created: Account = {
      id: 'acct-new',
      name: body.name,
      institution: body.institution ?? null,
      type: body.type ?? 'CHECKING',
      openingBalance: body.openingBalance ?? 0,
      openingBalanceDate: body.openingBalanceDate ?? '2026-01-01',
      closedDate: null,
      closed: false,
      balance: body.openingBalance ?? 0,
    }
    return HttpResponse.json(created, { status: 201 })
  }),

  http.patch(`${ACCOUNTS_URL}/:id`, async ({ request, params }) => {
    const body = (await request.json()) as UpdateAccountRequestBody
    const existing = seedAccounts.find((a) => a.id === params.id)
    const updated: Account = {
      id: params.id as string,
      name: body.name,
      institution: body.institution ?? null,
      type: existing?.type ?? 'CHECKING',
      openingBalance: existing?.openingBalance ?? 0,
      openingBalanceDate: existing?.openingBalanceDate ?? '2026-01-01',
      closedDate: existing?.closedDate ?? null,
      closed: existing?.closed ?? false,
      balance: existing?.balance ?? 0,
    }
    return HttpResponse.json(updated)
  }),

  http.post(`${ACCOUNTS_URL}/:id/close`, ({ params }) => {
    const existing = seedAccounts.find((a) => a.id === params.id)
    const closed: Account = {
      id: params.id as string,
      name: existing?.name ?? 'Account',
      institution: existing?.institution ?? null,
      type: existing?.type ?? 'CHECKING',
      openingBalance: existing?.openingBalance ?? 0,
      openingBalanceDate: existing?.openingBalanceDate ?? '2026-01-01',
      closedDate: '2026-09-15',
      closed: true,
      balance: existing?.balance ?? 0,
    }
    return HttpResponse.json(closed)
  }),
]

/**
 * `409` variant for closing an already-closed account (F003's `AccountAlreadyClosedException`) -
 * applied via `server.use(...)` in tests that exercise that path.
 */
export const accountAlreadyClosedConflictHandler = http.post(`${ACCOUNTS_URL}/:id/close`, () =>
  HttpResponse.json({ message: 'Account is already closed' }, { status: 409 }),
)
