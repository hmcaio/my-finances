import { assertOk } from './apiError'
import type { components } from './generated/schema'

// Local dev only: backend is always http://localhost:8080 (PRD S7.1). See src/api/health.ts.
const API_BASE_URL = 'http://localhost:8080'

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
  const response = await fetch(`${API_BASE_URL}/api/payment-methods`)
  await assertOk(response)
  return (await response.json()) as PaymentMethod[]
}

export async function createPaymentMethod(
  request: CreatePaymentMethodRequest,
): Promise<PaymentMethod> {
  const response = await fetch(`${API_BASE_URL}/api/payment-methods`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
  })
  await assertOk(response)
  return (await response.json()) as PaymentMethod
}

export async function renamePaymentMethod(
  id: string,
  request: UpdatePaymentMethodRequest,
): Promise<PaymentMethod> {
  const response = await fetch(`${API_BASE_URL}/api/payment-methods/${id}`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
  })
  await assertOk(response)
  return (await response.json()) as PaymentMethod
}

export async function deletePaymentMethod(id: string): Promise<void> {
  const response = await fetch(`${API_BASE_URL}/api/payment-methods/${id}`, { method: 'DELETE' })
  await assertOk(response, CONFLICT_MESSAGE)
}
