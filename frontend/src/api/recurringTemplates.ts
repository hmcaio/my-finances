import { apiClient } from './client'
import { unwrap } from './apiError'
import type { components } from './generated/schema'
import type { Transaction } from './transactions'

/**
 * A RecurringTemplate as returned by the API (PRD S5.7, F007 spec): a versioned recurring bill/
 * income template. `currentAmount`/`currentDayOfMonth`/`currentEffectiveFrom` reflect whichever
 * version is effective as of the current real-world month - all three are `null` on the rare case
 * where a template exists but no version is effective yet (F006's `Budget.currentCap` precedent).
 */
export interface RecurringTemplate {
  id: string
  categoryId: string
  accountId: string
  description: string
  active: boolean
  currentAmount: number | null
  currentDayOfMonth: number | null
  currentEffectiveFrom: string | null
}

/** A not-yet-confirmed cycle of a RecurringTemplate (F007 spec), dashboard-ready. `amount` is
 * denormalized from the resolved template version so the pending-occurrences widget can render
 * without a second lookup per row. */
export interface PendingRecurringOccurrence {
  id: string
  templateId: string
  templateVersionId: string
  dueDate: string
  amount: number
}

export type CreateRecurringTemplateRequest = components['schemas']['CreateRecurringTemplateRequest']
export type UpdateRecurringTemplateCapRequest =
  components['schemas']['UpdateRecurringTemplateCapRequest']
export type ConfirmPendingOccurrenceRequest =
  components['schemas']['ConfirmPendingOccurrenceRequest']

export const CREATE_CONFLICT_MESSAGE =
  'This account is closed and cannot accept new recurring activity.'
export const CONFIRM_CONFLICT_MESSAGE = 'This account is closed and cannot accept new transactions.'

/** Fetches every RecurringTemplate, each with its amount/day-of-month as of the current month
 * (F007 spec). */
export async function getRecurringTemplates(): Promise<RecurringTemplate[]> {
  return unwrap(apiClient.get<RecurringTemplate[]>('/recurring-templates'))
}

/** Creates a RecurringTemplate plus its first version (F007 spec). A `409` covers a closed target
 * account. */
export async function createRecurringTemplate(
  request: CreateRecurringTemplateRequest,
): Promise<RecurringTemplate> {
  return unwrap(
    apiClient.post<RecurringTemplate>('/recurring-templates', request),
    CREATE_CONFLICT_MESSAGE,
  )
}

/** Sets a template's amount/day-of-month, effective from the given month (F007 spec's `PATCH
 * .../cap`) - creates a new version, or replaces the version for that exact month if one already
 * exists (F006 `Budget` precedent). */
export async function setRecurringTemplateCap(
  id: string,
  request: UpdateRecurringTemplateCapRequest,
): Promise<RecurringTemplate> {
  return unwrap(apiClient.patch<RecurringTemplate>(`/recurring-templates/${id}/cap`, request))
}

/** Stops a template - no further pending occurrences generate until reactivated (F007 spec). */
export async function stopRecurringTemplate(id: string): Promise<RecurringTemplate> {
  return unwrap(apiClient.post<RecurringTemplate>(`/recurring-templates/${id}/stop`))
}

/** Reactivates a stopped template, resuming generation from the current month (F007 spec/PRD
 * S5.7) - never backfills the stopped period. */
export async function reactivateRecurringTemplate(id: string): Promise<RecurringTemplate> {
  return unwrap(apiClient.post<RecurringTemplate>(`/recurring-templates/${id}/reactivate`))
}

/** Fetches every pending occurrence awaiting confirmation, dashboard-ready (F007 spec). Triggers
 * catch-up generation server-side first, so this is never stale. */
export async function getPendingRecurringOccurrences(): Promise<PendingRecurringOccurrence[]> {
  return unwrap(apiClient.get<PendingRecurringOccurrence[]>('/recurring-templates/pending'))
}

/**
 * Confirms a pending occurrence into a real transaction (F007 spec, PRD S5.7/S6.5).
 * `amount`/`date`/`accountId` are optional overrides falling back to the occurrence's own
 * defaults; `paymentMethodId` is always required - the template has no payment method of its own
 * to default to.
 */
export async function confirmPendingRecurringOccurrence(
  id: string,
  request: ConfirmPendingOccurrenceRequest,
): Promise<Transaction> {
  return unwrap(
    apiClient.post<Transaction>(`/recurring-templates/pending/${id}/confirm`, request),
    CONFIRM_CONFLICT_MESSAGE,
  )
}

/** Dismisses a pending occurrence without confirming it - no transaction is created (F007
 * spec). */
export async function dismissPendingRecurringOccurrence(id: string): Promise<void> {
  await unwrap(apiClient.delete<void>(`/recurring-templates/pending/${id}`))
}
