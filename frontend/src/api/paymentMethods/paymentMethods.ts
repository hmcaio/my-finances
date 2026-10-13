import { apiClient } from '../core/client'
import { unwrap } from '../core/apiError'
import type { components } from '../generated/schema'

/** A PaymentMethod as returned by the API (PRD S5.2). */
export interface PaymentMethod {
  id: string
  name: string
}

export type CreatePaymentMethodRequest = components['schemas']['CreatePaymentMethodRequest']
export type UpdatePaymentMethodRequest = components['schemas']['UpdatePaymentMethodRequest']

/** Matches the backend's `TextFieldConstraints.MAX_NAME_LENGTH`, so inputs can cap what is typed. */
export const PAYMENT_METHOD_NAME_MAX_LENGTH = 100

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

/** Deletes a payment method; a `409` means a transaction still uses it (F031/ADR 0026:
 * `PaymentMethodInUseException`, see `PROBLEM_TYPE_MESSAGES` in `apiError.ts`). */
export async function deletePaymentMethod(id: string): Promise<void> {
  await unwrap(apiClient.delete<void>(`/payment-methods/${id}`))
}
