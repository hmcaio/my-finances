import { apiClient } from './client'
import { unwrap } from './apiError'
import type { components } from './generated/schema'

/** A Transfer as returned by the API (PRD S5.5/S6.2). No category, no type - a transfer is never
 * "categorized" and has no direct budget/net-worth impact. */
export interface Transfer {
  id: string
  date: string
  fromAccountId: string
  toAccountId: string
  amount: number
  description: string
  additionalNotes: string | null
}

export type CreateTransferRequest = components['schemas']['CreateTransferRequest']
export type UpdateTransferRequest = components['schemas']['UpdateTransferRequest']

/** Optional filter dimensions for {@link getTransfers} (F005 spec's list query params).
 * `accountId` matches either side of the transfer (PRD S6.9). */
export interface TransferFilter {
  dateFrom?: string
  dateTo?: string
  accountId?: string
}

/** One page of transfers - mirrors the backend's `PagedModel` envelope (F004's convention, reused
 * here). */
export interface TransferPage {
  content: Transfer[]
  page: {
    size: number
    number: number
    totalElements: number
    totalPages: number
  }
}

export const CLOSED_ACCOUNT_MESSAGE = 'This account is closed and cannot accept new transfers.'

/**
 * Fetches a filtered, paginated page of transfers. `page` is 0-indexed; both `page`/`size` default
 * to the backend's own defaults (0, 20) when omitted, most recent first.
 */
export async function getTransfers(
  filter: TransferFilter = {},
  page?: number,
  size?: number,
): Promise<TransferPage> {
  return unwrap(apiClient.get<TransferPage>('/transfers', { params: { ...filter, page, size } }))
}

export async function getTransfer(id: string): Promise<Transfer> {
  return unwrap(apiClient.get<Transfer>(`/transfers/${id}`))
}

/**
 * Creates a transfer. A `409` (either account closed) is mapped to a friendly message, same
 * pattern as F004's `createTransaction`. The same-account case (400, F005's
 * `SameAccountTransferException`) is prevented client-side by the create form (F005 spec: "can't
 * pick the same account twice"), so it isn't given its own friendly message here.
 */
export async function createTransfer(request: CreateTransferRequest): Promise<Transfer> {
  return unwrap(apiClient.post<Transfer>('/transfers', request), CLOSED_ACCOUNT_MESSAGE)
}

/** Full-replace edit - every editable field (date/from/to account/amount/description/additional
 * notes), matching F004's PATCH convention (F005 spec). */
export async function editTransfer(id: string, request: UpdateTransferRequest): Promise<Transfer> {
  return unwrap(apiClient.patch<Transfer>(`/transfers/${id}`, request), CLOSED_ACCOUNT_MESSAGE)
}

export async function deleteTransfer(id: string): Promise<void> {
  await unwrap(apiClient.delete<void>(`/transfers/${id}`))
}
