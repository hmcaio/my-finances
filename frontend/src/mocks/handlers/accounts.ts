import { http, HttpResponse } from 'msw'
import type { Account, AccountType } from '../../api/accounts/accounts'
import { createStore } from '../store'

/**
 * Seed data returned by the default `GET /api/accounts` handler below. Exported so tests can
 * assert against it directly instead of duplicating the fixture (F015 spec's F002 backfill,
 * extended here for F003).
 */
export const seedAccounts: Account[] = [
  {
    id: 'acct-1',
    name: 'Itau Checking',
    institutionId: 'inst-1',
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
    institutionId: 'inst-2',
    type: 'SAVINGS',
    openingBalance: 500,
    openingBalanceDate: '2025-01-01',
    closedDate: '2026-01-01',
    closed: true,
    balance: 500,
  },
  {
    id: 'acct-3',
    name: 'Nubank Credit Card',
    institutionId: 'inst-2',
    type: 'CREDIT_CARD',
    openingBalance: 200,
    openingBalanceDate: '2026-01-01',
    closedDate: null,
    closed: false,
    balance: 200,
  },
]

/**
 * An INVESTMENT account (F008): no opening balance or date, balance 0 until snapshots exist (F009).
 * Kept OUT of `seedAccounts` on purpose - that list feeds the pickers on the transaction, transfer
 * and recurring pages, where an investment account must not appear - and served by id only, so the
 * detail page and the products section can be tested against it.
 */
export const seedInvestmentAccount: Account = {
  id: 'acct-inv',
  name: 'XP Investimentos',
  institutionId: 'inst-2',
  type: 'INVESTMENT',
  openingBalance: null,
  openingBalanceDate: null,
  closedDate: null,
  closed: false,
  balance: 0,
}

/**
 * Named lookup for the seeded open checking account (`acct-1`), so call sites identify it by name
 * instead of indexing into `seedAccounts` by position (frontend test audit's F6 finding).
 */
export const seedCheckingAccount = seedAccounts.find((a) => a.name === 'Itau Checking')!

const ACCOUNTS_URL = '/api/accounts'

const accounts = createStore([...seedAccounts, seedInvestmentAccount])

interface CreateAccountRequestBody {
  name: string
  institutionId: string
  type?: AccountType
  openingBalance?: number
  openingBalanceDate?: string
}

interface UpdateAccountRequestBody {
  name: string
  institutionId: string
}

/**
 * Default success-path handlers for every accounts endpoint (F003's REST API), backed by an
 * in-memory store restored after each test (see `categories.ts`). The investment account is in
 * the store but out of the list responses, like `seedAccounts`' doc comment says.
 */
export const accountsHandlers = [
  http.get(ACCOUNTS_URL, ({ request }) => {
    const includeClosed = new URL(request.url).searchParams.get('includeClosed') === 'true'
    const visible = accounts
      .list()
      .filter((a) => a.id !== seedInvestmentAccount.id && (includeClosed || !a.closed))
    return HttpResponse.json(visible)
  }),

  http.get(`${ACCOUNTS_URL}/:id`, ({ params }) => {
    const account = accounts.find(params.id as string)
    if (!account) return new HttpResponse(null, { status: 404 })
    return HttpResponse.json(account)
  }),

  http.post(ACCOUNTS_URL, async ({ request }) => {
    const body = (await request.json()) as CreateAccountRequestBody
    const type = body.type ?? 'CHECKING'
    // An INVESTMENT account has no opening balance/date (F008); the backend answers 0 for its balance.
    const isInvestment = type === 'INVESTMENT'
    const created = accounts.add({
      id: accounts.nextId('acct'),
      name: body.name,
      institutionId: body.institutionId,
      type,
      openingBalance: isInvestment ? null : (body.openingBalance ?? 0),
      openingBalanceDate: isInvestment ? null : (body.openingBalanceDate ?? '2026-01-01'),
      closedDate: null,
      closed: false,
      balance: isInvestment ? 0 : (body.openingBalance ?? 0),
    })
    return HttpResponse.json(created, { status: 201 })
  }),

  http.patch(`${ACCOUNTS_URL}/:id`, async ({ request, params }) => {
    const body = (await request.json()) as UpdateAccountRequestBody
    const updated = accounts.replace(params.id as string, (row) => ({
      ...row,
      name: body.name,
      institutionId: body.institutionId,
    }))
    return updated ? HttpResponse.json(updated) : new HttpResponse(null, { status: 404 })
  }),

  http.post(`${ACCOUNTS_URL}/:id/close`, ({ params }) => {
    const closed = accounts.replace(params.id as string, (row) => ({
      ...row,
      closedDate: '2026-09-15',
      closed: true,
    }))
    return closed ? HttpResponse.json(closed) : new HttpResponse(null, { status: 404 })
  }),
]

/**
 * `409` variant for closing an already-closed account (F003's `AccountAlreadyClosedException`) -
 * applied via `server.use(...)` in tests that exercise that path.
 */
export const accountAlreadyClosedConflictHandler = http.post(`${ACCOUNTS_URL}/:id/close`, () =>
  HttpResponse.json({ message: 'Account is already closed' }, { status: 409 }),
)

/**
 * `409` variant for the duplicate-name case on create/edit (post-F007 schema audit's
 * `AccountNameAlreadyExistsException`) - see `categoryCreateConflictHandler` in `categories.ts`.
 */
export const accountCreateConflictHandler = http.post(ACCOUNTS_URL, () =>
  HttpResponse.json({ message: 'Account name already exists' }, { status: 409 }),
)

export const accountEditConflictHandler = http.patch(`${ACCOUNTS_URL}/:id`, () =>
  HttpResponse.json({ message: 'Account name already exists' }, { status: 409 }),
)

/**
 * Success-path variant of `GET /api/accounts` that also includes the seeded INVESTMENT account
 * (kept out of `seedAccounts` itself - see its own doc comment) - applied via `server.use(...)` in
 * tests asserting that an investment account must never appear in a transaction/transfer/recurring
 * account picker (duplicated verbatim across those pages' tests before this extraction, F7 finding).
 */
export const accountsWithInvestmentHandler = http.get(ACCOUNTS_URL, () =>
  HttpResponse.json([...seedAccounts, seedInvestmentAccount]),
)
