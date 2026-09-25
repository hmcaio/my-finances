import { apiClient } from '../core/client'
import { unwrap } from '../core/apiError'
import type { components } from '../generated/schema'

/** A Transfer as returned by the API (PRD S5.5/S6.2). No category, no type - a transfer is never
 * "categorized" and has no direct budget/net-worth impact. With `investmentProductId` set (F009,
 * ADR 0012) it is a buy (into the product's INVESTMENT account) or a sell (out of it); quantity,
 * unit price and taxes are record-only and null when not given. */
export interface Transfer {
  id: string
  date: string
  fromAccountId: string
  toAccountId: string
  amount: number
  description: string
  additionalNotes: string | null
  investmentProductId: string | null
  quantity: number | null
  unitPrice: number | null
  taxes: number | null
}

export type CreateTransferRequest = components['schemas']['CreateTransferRequest']
export type UpdateTransferRequest = components['schemas']['UpdateTransferRequest']

/** Optional filter dimensions for {@link getTransfers} (F005 spec's list query params).
 * `accountId` matches either side of the transfer (PRD S6.9). */
export interface TransferFilter {
  dateFrom?: string
  dateTo?: string
  accountId?: string
  /** One product's buy/sell history (F009). */
  investmentProductId?: string
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

// The backend sends no message text, so every expected 409 needs its own wording here. A transfer's
// 409 has several causes: a closed account, and (F009) the investment rules.
export const TRANSFER_CONFLICT_MESSAGE =
  'The transfer could not be saved: both accounts must be open, an investment account needs one of its own open products, and a product needs exactly one investment account on one side.'

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
 * Creates a transfer - a buy/sell when it carries `investmentProductId` (F009), optionally with a
 * `resultingBalance` that also records a snapshot of the product on the transfer date. A `409`
 * (an account closed, or an investment rule) is mapped to a friendly message, same pattern as
 * F004's `createTransaction`. The same-account case (400, F005's
 * `SameAccountTransferException`) is prevented client-side by the create form (F005 spec: "can't
 * pick the same account twice"), so it isn't given its own friendly message here.
 */
export async function createTransfer(request: CreateTransferRequest): Promise<Transfer> {
  return unwrap(apiClient.post<Transfer>('/transfers', request), TRANSFER_CONFLICT_MESSAGE)
}

/** Full-replace edit - every editable field (date/from/to account/amount/description/additional
 * notes, plus the investment product and trade details), matching F004's PATCH convention (F005
 * spec). Editing never touches snapshots. */
export async function editTransfer(id: string, request: UpdateTransferRequest): Promise<Transfer> {
  return unwrap(apiClient.patch<Transfer>(`/transfers/${id}`, request), TRANSFER_CONFLICT_MESSAGE)
}

export async function deleteTransfer(id: string): Promise<void> {
  await unwrap(apiClient.delete<void>(`/transfers/${id}`))
}
