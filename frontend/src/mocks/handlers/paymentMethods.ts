import { http, HttpResponse } from 'msw'
import type { PaymentMethod } from '../../api/paymentMethods'
import { createStore } from '../store'

/**
 * Seed data returned by the default `GET /api/payment-methods` handler below. Exported so tests
 * can assert against it directly instead of duplicating the fixture (F015 spec's F002 backfill).
 */
export const seedPaymentMethods: PaymentMethod[] = [
  { id: 'pm-1', name: 'Debit Card' },
  { id: 'pm-2', name: 'Cash' },
]

/**
 * Named lookups for the two seed rows, so call sites identify them by name instead of indexing
 * into `seedPaymentMethods` by position (frontend test audit's F6 finding).
 */
export const seedDebitCardPaymentMethod = seedPaymentMethods.find((p) => p.name === 'Debit Card')!
export const seedCashPaymentMethod = seedPaymentMethods.find((p) => p.name === 'Cash')!

const PAYMENT_METHODS_URL = '/api/payment-methods'

interface PaymentMethodRequestBody {
  name: string
}

const paymentMethods = createStore(seedPaymentMethods)

/**
 * Default success-path handlers for every payment-methods endpoint (F002's REST API), backed by an
 * in-memory store restored after each test (see `categories.ts`).
 */
export const paymentMethodsHandlers = [
  http.get(PAYMENT_METHODS_URL, () => HttpResponse.json(paymentMethods.list())),

  http.post(PAYMENT_METHODS_URL, async ({ request }) => {
    const body = (await request.json()) as PaymentMethodRequestBody
    const created = paymentMethods.add({ id: paymentMethods.nextId('pm'), name: body.name })
    return HttpResponse.json(created, { status: 201 })
  }),

  http.patch(`${PAYMENT_METHODS_URL}/:id`, async ({ request, params }) => {
    const body = (await request.json()) as PaymentMethodRequestBody
    const updated = paymentMethods.replace(params.id as string, (row) => ({
      ...row,
      name: body.name,
    }))
    return updated ? HttpResponse.json(updated) : new HttpResponse(null, { status: 404 })
  }),

  http.delete(`${PAYMENT_METHODS_URL}/:id`, ({ params }) => {
    paymentMethods.remove(params.id as string)
    return new HttpResponse(null, { status: 204 })
  }),
]

/**
 * `409` variant for the delete-conflict case (F002 spec's `conflictMessage`, now backed for real by
 * F004's `PaymentMethodInUseException`) - see `categoryDeleteConflictHandler` in `categories.ts`.
 */
export const paymentMethodDeleteConflictHandler = http.delete(`${PAYMENT_METHODS_URL}/:id`, () =>
  HttpResponse.json({ message: 'Payment method is in use' }, { status: 409 }),
)

/**
 * `409` variant for the duplicate-name case on create/rename (post-F007 schema audit's
 * `PaymentMethodNameAlreadyExistsException`) - see `categoryCreateConflictHandler` in
 * `categories.ts`.
 */
export const paymentMethodCreateConflictHandler = http.post(PAYMENT_METHODS_URL, () =>
  HttpResponse.json({ message: 'Payment method name already exists' }, { status: 409 }),
)

export const paymentMethodRenameConflictHandler = http.patch(`${PAYMENT_METHODS_URL}/:id`, () =>
  HttpResponse.json({ message: 'Payment method name already exists' }, { status: 409 }),
)
