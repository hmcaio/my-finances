import { http, HttpResponse } from 'msw'
import type { PaymentMethod } from '../../api/paymentMethods/paymentMethods'
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

const paymentMethods = createStore(seedPaymentMethods)

/**
 * Default success-path handler for the payment-methods endpoint (F002's REST API), backed by an
 * in-memory store restored after each test (see `categories.ts`).
 *
 * F034/ADR 0028: read-only - the POST/PATCH/DELETE handlers and their 409 conflict variants were
 * removed along with the backend's write endpoints; every row is fixed, migration-seeded content.
 */
export const paymentMethodsHandlers = [
  http.get(PAYMENT_METHODS_URL, () => HttpResponse.json(paymentMethods.list())),
]
