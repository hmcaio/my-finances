import { apiClient } from './client'
import { unwrap } from './apiError'
import type { components } from './generated/schema'

/** A PaymentMethod as returned by the API (PRD S5.2). */
export interface PaymentMethod {
  id: string
  name: string
}

export type CreatePaymentMethodRequest = components['schemas']['CreatePaymentMethodRequest']
export type UpdatePaymentMethodRequest = components['schemas']['UpdatePaymentMethodRequest']

const CONFLICT_MESSAGE =
  'This payment method is used by existing transactions — reassign them before deleting it.'

/**
 * Fetches every payment method. Reused as a dropdown-options source by F004 (Transactions), per
 * F002 spec.
 */
export async function getPaymentMethods(): Promise<PaymentMethod[]> {
  return unwrap(apiClient.get<PaymentMethod[]>('/payment-methods'))
}

export async function createPaymentMethod(
  request: CreatePaymentMethodRequest,
): Promise<PaymentMethod> {
  return unwrap(apiClient.post<PaymentMethod>('/payment-methods', request))
}

export async function renamePaymentMethod(
  id: string,
  request: UpdatePaymentMethodRequest,
): Promise<PaymentMethod> {
  return unwrap(apiClient.patch<PaymentMethod>(`/payment-methods/${id}`, request))
}

export async function deletePaymentMethod(id: string): Promise<void> {
  await unwrap(apiClient.delete<void>(`/payment-methods/${id}`), CONFLICT_MESSAGE)
}
