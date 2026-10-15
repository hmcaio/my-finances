import { describe, expect, it } from 'vitest'
import { seedPaymentMethods } from '../../mocks/handlers/paymentMethods'
import { getPaymentMethods } from './paymentMethods'

/**
 * F034/ADR 0028: PaymentMethod lost create/rename/delete - every row is fixed, migration-seeded
 * content. Only the read path is left to test.
 */
describe('paymentMethods API client', () => {
  it('getPaymentMethods returns every seeded payment method', async () => {
    const paymentMethods = await getPaymentMethods()

    expect(paymentMethods).toEqual(seedPaymentMethods)
  })
})
