import { http, HttpResponse } from 'msw'
import type { Ledger, LedgerEntry, LedgerEntryType } from '../../api/ledger/ledger'
import type { Transaction } from '../../api/transactions/transactions'
import type { Transfer } from '../../api/transfers/transfers'
import { transactions } from './transactions'
import { transfers } from './transfers'

/** The backend's cap on a Ledger response (`LedgerQuery.MAX_ENTRIES`). */
export const LEDGER_MAX_ENTRIES = 1000

/** Longest `q` the backend accepts before answering 400 (`MAX_SEARCH_TEXT_LENGTH`). */
const MAX_SEARCH_LENGTH = 100

/** Lower-case and strip accents, like Postgres `lower(unaccent(x))`. */
function fold(value: string): string {
  return value.normalize('NFD').replace(/\p{M}/gu, '').toLowerCase()
}

function matchesSearch(search: string, description: string, notes: string | null): boolean {
  const needle = fold(search)
  return fold(description).includes(needle) || (notes !== null && fold(notes).includes(needle))
}

function fromTransaction(t: Transaction): LedgerEntry {
  return {
    id: t.id,
    type: t.type,
    date: t.date,
    amount: t.amount,
    description: t.description,
    additionalNotes: t.additionalNotes,
    categoryId: t.categoryId,
    accountId: t.accountId,
    paymentMethodId: t.paymentMethodId,
    fromAccountId: null,
    toAccountId: null,
    tradeLines: [],
  }
}

function fromTransfer(t: Transfer): LedgerEntry {
  return {
    id: t.id,
    type: 'TRANSFER',
    date: t.date,
    amount: t.amount,
    description: t.description,
    additionalNotes: t.additionalNotes,
    categoryId: null,
    accountId: null,
    paymentMethodId: null,
    fromAccountId: t.fromAccountId,
    toAccountId: t.toAccountId,
    tradeLines: (t.tradeConfirmation?.lines ?? []).map((l) => ({
      side: l.side,
      productId: l.productId,
    })),
  }
}

/** Date descending, then transactions before transfers, then id descending (`LedgerQuery`). */
function newestFirst(a: LedgerEntry, b: LedgerEntry): number {
  if (a.date !== b.date) return a.date < b.date ? 1 : -1
  const aTransfer = a.type === 'TRANSFER'
  const bTransfer = b.type === 'TRANSFER'
  if (aTransfer !== bTransfer) return aTransfer ? 1 : -1
  return a.id < b.id ? 1 : a.id > b.id ? -1 : 0
}

const round2 = (n: number) => Math.round(n * 100) / 100

/**
 * Default `GET /api/ledger` handler (F037), backed by the same in-memory stores as the transactions
 * and transfers handlers, so a create, edit or delete through those shows up here after the
 * refetch. Mirrors `LedgerQuery`: `type` picks the side(s) read, a category or payment method
 * excludes transfers, `accountId` matches a transaction's account or either side of a transfer,
 * `q` ignores case and accents over description and notes, the list is capped, and the summary
 * covers every match (transfers excluded) even when the list is truncated.
 */
export const ledgerHandlers = [
  http.get('/api/ledger', ({ request }) => {
    const url = new URL(request.url)
    const dateFrom = url.searchParams.get('dateFrom')
    const dateTo = url.searchParams.get('dateTo')
    const type = url.searchParams.get('type') as LedgerEntryType | null
    const accountId = url.searchParams.get('accountId')
    const categoryId = url.searchParams.get('categoryId')
    const paymentMethodId = url.searchParams.get('paymentMethodId')
    const search = url.searchParams.get('q')?.trim() ?? ''

    if (search.length > MAX_SEARCH_LENGTH) {
      return HttpResponse.json(
        { type: 'urn:my-finances:error:handler-method-validation' },
        { status: 400 },
      )
    }

    const inScope = (date: string) => (!dateFrom || date >= dateFrom) && (!dateTo || date <= dateTo)

    const readsTransactions = type !== 'TRANSFER'
    const readsTransfers =
      type !== 'INCOME' && type !== 'EXPENSE' && !categoryId && !paymentMethodId

    const matchingTransactions = readsTransactions
      ? transactions
          .list()
          .filter((t) => inScope(t.date))
          .filter((t) => !type || t.type === type)
          .filter((t) => !accountId || t.accountId === accountId)
          .filter((t) => !categoryId || t.categoryId === categoryId)
          .filter((t) => !paymentMethodId || t.paymentMethodId === paymentMethodId)
          .filter((t) => !search || matchesSearch(search, t.description, t.additionalNotes))
      : []
    const matchingTransfers = readsTransfers
      ? transfers
          .list()
          .filter((t) => inScope(t.date))
          .filter((t) => !accountId || t.fromAccountId === accountId || t.toAccountId === accountId)
          .filter((t) => !search || matchesSearch(search, t.description, t.additionalNotes))
      : []

    const merged = [
      ...matchingTransactions.map(fromTransaction),
      ...matchingTransfers.map(fromTransfer),
    ].sort(newestFirst)

    const income = matchingTransactions
      .filter((t) => t.type === 'INCOME')
      .reduce((sum, t) => sum + t.amount, 0)
    const expense = matchingTransactions
      .filter((t) => t.type === 'EXPENSE')
      .reduce((sum, t) => sum + t.amount, 0)

    const body: Ledger = {
      entries: merged.slice(0, LEDGER_MAX_ENTRIES),
      summary: { income: round2(income), expense: round2(expense), net: round2(income - expense) },
      truncated: merged.length > LEDGER_MAX_ENTRIES,
    }
    return HttpResponse.json(body)
  }),
]
