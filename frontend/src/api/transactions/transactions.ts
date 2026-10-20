import { apiClient } from '../core/client'
import { unwrap } from '../core/apiError'
import type { components } from '../generated/schema'

export type TransactionType = 'INCOME' | 'EXPENSE'

export type FuelType = components['schemas']['CreateTransactionRequest']['fuelType']

/**
 * A Transaction as returned by the API (PRD S5.3). `type` is derived server-side from the
 * category, never client input.
 *
 * <p>The fuel fields (F024, ADR 0021) are `null` for an ordinary transaction and present exactly
 * when the transaction's category is the dedicated fuel category; `kmPerLiter`/`amountPerKm`/
 * `litersPerKm` are computed on read (`FuelRatiosQuery`), `null` when `kmSinceLastFill` is absent
 * (a vehicle's first recorded fill). `investmentHoldingId` (F026, ADR 0023) is `null` for an
 * ordinary transaction and present exactly when the transaction's category is the dedicated
 * dividend category.
 */
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
  vehicleId: string | null
  fuelType: FuelType | null
  liters: number | null
  pricePerLiter: number | null
  kmSinceLastFill: number | null
  odometer: number | null
  kmPerLiter: number | null
  amountPerKm: number | null
  litersPerKm: number | null
  investmentHoldingId: string | null
}

export type CreateTransactionRequest = components['schemas']['CreateTransactionRequest']
export type UpdateTransactionRequest = components['schemas']['UpdateTransactionRequest']

export async function getTransaction(id: string): Promise<Transaction> {
  return unwrap(apiClient.get<Transaction>(`/transactions/${id}`))
}

/** A `409` is `AccountClosedException` or `AccountTypeNotAllowedException` (F031/ADR 0026, see
 * `PROBLEM_TYPE_MESSAGES` in `apiError.ts`). */
export async function createTransaction(request: CreateTransactionRequest): Promise<Transaction> {
  return unwrap(apiClient.post<Transaction>('/transactions', request))
}

/** Full-replace edit - every editable field (amount/date/category/account/payment
 * method/description/additional notes), matching F002/F003's PATCH convention (F004 spec). */
export async function editTransaction(
  id: string,
  request: UpdateTransactionRequest,
): Promise<Transaction> {
  return unwrap(apiClient.patch<Transaction>(`/transactions/${id}`, request))
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
