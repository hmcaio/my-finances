import { apiClient } from './client'
import { unwrap } from './apiError'
import type { components } from './generated/schema'

export type TransactionType = 'INCOME' | 'EXPENSE'

/** A Transaction as returned by the API (PRD S5.3). `type` is derived server-side from the
 * category, never client input. */
export interface Transaction {
  id: string
  date: string
  amount: number
  categoryId: string
  type: TransactionType
  accountId: string
  paymentMethodId: string
  recurringTemplateVersionId: string | null
  description: string
  additionalNotes: string | null
}

export type CreateTransactionRequest = components['schemas']['CreateTransactionRequest']
export type UpdateTransactionRequest = components['schemas']['UpdateTransactionRequest']

/** Optional filter dimensions for {@link getTransactions} (F004 spec's list query params). */
export interface TransactionFilter {
  dateFrom?: string
  dateTo?: string
  categoryId?: string
  accountId?: string
  paymentMethodId?: string
}

/** One page of transactions - mirrors the backend's `PagedModel` envelope (F004 spec). */
export interface TransactionPage {
  content: Transaction[]
  page: {
    size: number
    number: number
    totalElements: number
    totalPages: number
  }
}

export const CLOSED_ACCOUNT_MESSAGE = 'This account is closed and cannot accept new transactions.'

/**
 * Fetches a filtered, paginated page of transactions. `page` is 0-indexed; both `page`/`size`
 * default to the backend's own defaults (0, 20) when omitted, most recent first.
 */
export async function getTransactions(
  filter: TransactionFilter = {},
  page?: number,
  size?: number,
): Promise<TransactionPage> {
  return unwrap(
    apiClient.get<TransactionPage>('/transactions', { params: { ...filter, page, size } }),
  )
}

export async function getTransaction(id: string): Promise<Transaction> {
  return unwrap(apiClient.get<Transaction>(`/transactions/${id}`))
}

export async function createTransaction(request: CreateTransactionRequest): Promise<Transaction> {
  return unwrap(apiClient.post<Transaction>('/transactions', request), CLOSED_ACCOUNT_MESSAGE)
}

/** Full-replace edit - every editable field (amount/date/category/account/payment
 * method/description/additional notes), matching F002/F003's PATCH convention (F004 spec). */
export async function editTransaction(
  id: string,
  request: UpdateTransactionRequest,
): Promise<Transaction> {
  return unwrap(
    apiClient.patch<Transaction>(`/transactions/${id}`, request),
    CLOSED_ACCOUNT_MESSAGE,
  )
}

export async function deleteTransaction(id: string): Promise<void> {
  await unwrap(apiClient.delete<void>(`/transactions/${id}`))
}

/** One row of the monthly spend by category (F012's `GET /api/transactions/spend-by-category`). */
export interface CategorySpend {
  categoryId: string
  total: number
}

/** Expense total per category for `month` (`YYYY-MM`), largest first. */
export async function getSpendByCategory(month: string): Promise<CategorySpend[]> {
  return unwrap(
    apiClient.get<CategorySpend[]>('/transactions/spend-by-category', { params: { month } }),
  )
}
