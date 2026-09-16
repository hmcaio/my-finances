import { http, HttpResponse } from 'msw'
import type { PaymentMethod } from '../../api/paymentMethods'

/**
 * Seed data returned by the default `GET /api/payment-methods` handler below. Exported so tests
 * can assert against it directly instead of duplicating the fixture (F015 spec's F002 backfill).
 */
export const seedPaymentMethods: PaymentMethod[] = [
  { id: 'pm-1', name: 'Debit Card' },
  { id: 'pm-2', name: 'Cash' },
]

const PAYMENT_METHODS_URL = '/api/payment-methods'

interface PaymentMethodRequestBody {
  name: string
}

/**
 * Default success-path handlers for every payment-methods endpoint (F002's REST API). Same
 * request-echoing approach as `categories.ts` - no mutation of `seedPaymentMethods`, so every test
 * starts from the same fixture regardless of execution order.
 */
export const paymentMethodsHandlers = [
  http.get(PAYMENT_METHODS_URL, () => HttpResponse.json(seedPaymentMethods)),

  http.post(PAYMENT_METHODS_URL, async ({ request }) => {
    const body = (await request.json()) as PaymentMethodRequestBody
    const created: PaymentMethod = { id: 'pm-new', name: body.name }
    return HttpResponse.json(created, { status: 201 })
  }),

  http.patch(`${PAYMENT_METHODS_URL}/:id`, async ({ request, params }) => {
    const body = (await request.json()) as PaymentMethodRequestBody
    const updated: PaymentMethod = { id: params.id as string, name: body.name }
    return HttpResponse.json(updated)
  }),

  http.delete(`${PAYMENT_METHODS_URL}/:id`, () => new HttpResponse(null, { status: 204 })),
]

/**
 * `409` variant for the delete-conflict case (F002 spec's `conflictMessage`, now backed for real by
 * F004's `PaymentMethodInUseException`) - see `categoryDeleteConflictHandler` in `categories.ts`.
 */
export const paymentMethodDeleteConflictHandler = http.delete(`${PAYMENT_METHODS_URL}/:id`, () =>
  HttpResponse.json({ message: 'Payment method is in use' }, { status: 409 }),
)
