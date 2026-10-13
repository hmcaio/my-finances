import { apiClient } from '../core/client'
import { unwrap } from '../core/apiError'
import type { components } from '../generated/schema'

/** One product line of a {@link TradeConfirmation} (F027 spec, ADR 0024). */
export interface TradeConfirmationLine {
  productId: string
  side: 'BUY' | 'SELL'
  quantity: number
  unitPrice: number
  resultingBalance: number | null
  closeHolding: boolean
}

/** A settlement's lines (F027, ADR 0024). `taxes` is the parent {@link Transfer}'s own field. */
export interface TradeConfirmation {
  lines: TradeConfirmationLine[]
}

/** A Transfer as returned by the API (PRD S5.5/S6.2). No category, no type - a transfer is never
 * "categorized" and has no direct budget/net-worth impact. F027 (ADR 0024, superseding F009's
 * single-product shape): with `tradeConfirmation` set it's a settlement covering one or more
 * product lines; `fromAccountId`/`toAccountId`/`amount` are then backend-derived from the
 * confirmation's net settlement, never user-typed. */
export interface Transfer {
  id: string
  date: string
  fromAccountId: string
  toAccountId: string
  amount: number
  description: string
  additionalNotes: string | null
  taxes: number | null
  tradeConfirmation: TradeConfirmation | null
}

export type CreateTransferRequest = components['schemas']['CreateTransferRequest']
export type UpdateTransferRequest = components['schemas']['UpdateTransferRequest']

/** Optional filter dimensions for {@link getTransfers} (F005 spec's list query params).
 * `accountId` matches either side of the transfer (PRD S6.9). */
export interface TransferFilter {
  dateFrom?: string
  dateTo?: string
  accountId?: string
  /** One product's buy/sell history across every confirmation that trades it (F009, F027). */
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
 * Creates a transfer - a trade confirmation when the request carries `cashAccountId`/
 * `investmentAccountId`/`tradeConfirmation` (F027, ADR 0024) instead of the plain `fromAccountId`/
 * `toAccountId`/`amount` shape; the backend derives `amount`/direction from the confirmation's net
 * settlement and never trusts a client-supplied figure for one. A `409` is `AccountClosedException`,
 * `InvestmentTransferInvalidException` or `InvestmentHoldingClosedException`, each with its own
 * message in `apiError.ts`'s `PROBLEM_TYPE_MESSAGES` (F031/ADR 0026). The same-account case (400,
 * F005's `SameAccountTransferException`) is prevented client-side by the create form (F005 spec:
 * "can't pick the same account twice"), so it isn't given its own message here.
 */
export async function createTransfer(request: CreateTransferRequest): Promise<Transfer> {
  return unwrap(apiClient.post<Transfer>('/transfers', request))
}

/** Full-replace edit - every editable field, matching F004's PATCH convention (F005 spec). Omitting
 * `tradeConfirmation` clears it back to a plain transfer, same as the old "omitting
 * investmentProductId clears the tag" (F027). */
export async function editTransfer(id: string, request: UpdateTransferRequest): Promise<Transfer> {
  return unwrap(apiClient.patch<Transfer>(`/transfers/${id}`, request))
}

export async function deleteTransfer(id: string): Promise<void> {
  await unwrap(apiClient.delete<void>(`/transfers/${id}`))
}
