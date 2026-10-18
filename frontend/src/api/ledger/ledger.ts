import { apiClient } from '../core/client'
import { unwrap } from '../core/apiError'

/** What a Ledger row is: a transaction's own type, or a transfer (F037). */
export type LedgerEntryType = 'INCOME' | 'EXPENSE' | 'TRANSFER'

/** The part of a trade-confirmation line a Ledger row needs (the Buy/Sell chips). */
export interface LedgerTradeLine {
  side: 'BUY' | 'SELL'
  productId: string
}

/**
 * One row of `GET /api/ledger` (F037): flat, `null` where a field does not apply. A transaction has
 * `categoryId`/`accountId`/`paymentMethodId`; a transfer has `fromAccountId`/`toAccountId` and
 * `tradeLines` (empty unless it is a trade confirmation). `id` is the transaction's or the
 * transfer's own id, so `type` picks the endpoint for edit and delete. `amount` is always positive.
 */
export interface LedgerEntry {
  id: string
  type: LedgerEntryType
  date: string
  amount: number
  description: string
  additionalNotes: string | null
  categoryId: string | null
  accountId: string | null
  paymentMethodId: string | null
  fromAccountId: string | null
  toAccountId: string | null
  tradeLines: LedgerTradeLine[]
}

/** Income, expense and net of the whole filtered scope; transfers are excluded (PRD S5.5). */
export interface LedgerSummary {
  income: number
  expense: number
  net: number
}

export interface Ledger {
  entries: LedgerEntry[]
  summary: LedgerSummary
  /** More rows matched than `entries` holds (the backend caps the list at 1,000). */
  truncated: boolean
}

/** Optional filter dimensions of `GET /api/ledger`; dates are inclusive `YYYY-MM-DD`. */
export interface LedgerFilter {
  dateFrom?: string
  dateTo?: string
  type?: LedgerEntryType
  accountId?: string
  categoryId?: string
  paymentMethodId?: string
  /** Text search over description and notes, ignoring case and accents (at most 100 characters). */
  q?: string
}

/** The Ledger for `filter`: transactions and transfers interleaved, newest first. */
export async function getLedger(filter: LedgerFilter = {}): Promise<Ledger> {
  return unwrap(apiClient.get<Ledger>('/ledger', { params: filter }))
}
