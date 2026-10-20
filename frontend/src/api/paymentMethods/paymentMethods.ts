import { apiClient } from '../core/client'
import { unwrap } from '../core/apiError'

/** A PaymentMethod as returned by the API (PRD S5.2). */
export interface PaymentMethod {
  id: string
  name: string
}

/**
 * Fetches every payment method. Reused as a dropdown-options source by F004 (Transactions), per
 * F002 spec.
 *
 * F034/ADR 0028: read-only — every row is fixed, migration-seeded content (Debit Card, Credit
 * Card, PIX, Cash). create/rename/delete were removed from the backend and this client alike.
 */
export async function getPaymentMethods(): Promise<PaymentMethod[]> {
  return unwrap(apiClient.get<PaymentMethod[]>('/payment-methods'))
}
