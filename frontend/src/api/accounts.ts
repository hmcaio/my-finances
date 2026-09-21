import { apiClient } from './client'
import { unwrap } from './apiError'
import type { components } from './generated/schema'

export type AccountType = 'CHECKING' | 'SAVINGS' | 'CASH_WALLET' | 'CREDIT_CARD' | 'INVESTMENT'

/** An Account as returned by the API (PRD S5.4), including its computed running `balance`. */
export interface Account {
  id: string
  name: string
  institutionId: string
  type: AccountType
  /** `null` for an INVESTMENT account, whose value comes from snapshots (F008, ADR 0012). */
  openingBalance: number | null
  openingBalanceDate: string | null
  closedDate: string | null
  closed: boolean
  balance: number
}

export type CreateAccountRequest = components['schemas']['CreateAccountRequest']
export type UpdateAccountRequest = components['schemas']['UpdateAccountRequest']

const DUPLICATE_NAME_MESSAGE = 'An account with this name already exists.'

/**
 * Fetches accounts. Closed accounts are excluded by default (`includeClosed` mirrors the
 * backend's default-exclude query param, F003 spec) - they drop out of "create new" pickers and
 * the live balances widget but stay browsable via {@link getAccount}.
 */
export async function getAccounts(includeClosed = false): Promise<Account[]> {
  return unwrap(apiClient.get<Account[]>('/accounts', { params: { includeClosed } }))
}

/** Fetches one account's detail, including its running balance as of `asOf` (default: today). */
export async function getAccount(id: string, asOf?: string): Promise<Account> {
  return unwrap(apiClient.get<Account>(`/accounts/${id}`, { params: asOf ? { asOf } : undefined }))
}

export async function createAccount(request: CreateAccountRequest): Promise<Account> {
  return unwrap(apiClient.post<Account>('/accounts', request), DUPLICATE_NAME_MESSAGE)
}

/** Edits name/institution only (`institutionId` is required, F017). Type and opening balance/date are immutable (F003 spec). */
export async function editAccount(id: string, request: UpdateAccountRequest): Promise<Account> {
  return unwrap(apiClient.patch<Account>(`/accounts/${id}`, request), DUPLICATE_NAME_MESSAGE)
}

// The backend sends no message text, so every expected 409 needs its own wording here: the account
// is already closed, or (F008) it is an investment account that still has an open product.
const CLOSE_CONFLICT_MESSAGE =
  'This account could not be closed: it is already closed, or it is an investment account that still has open products - close those first.'

/** Closes an account. Not reversible through the UI - no "reopen" flow (F003 spec). */
export async function closeAccount(id: string): Promise<Account> {
  return unwrap(apiClient.post<Account>(`/accounts/${id}/close`), CLOSE_CONFLICT_MESSAGE)
}
