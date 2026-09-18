import { describe, expect, it } from 'vitest'
import { server } from '../mocks/server'
import {
  paymentMethodCreateConflictHandler,
  paymentMethodDeleteConflictHandler,
  paymentMethodRenameConflictHandler,
  seedPaymentMethods,
} from '../mocks/handlers/paymentMethods'
import { ApiError } from './apiError'
import {
  createPaymentMethod,
  deletePaymentMethod,
  getPaymentMethods,
  renamePaymentMethod,
} from './paymentMethods'

describe('paymentMethods API client', () => {
  it('getPaymentMethods returns the seeded list', async () => {
    await expect(getPaymentMethods()).resolves.toEqual(seedPaymentMethods)
  })

  it('createPaymentMethod posts the new payment method and returns the created one', async () => {
    const created = await createPaymentMethod({ name: 'Credit Card' })
    expect(created).toMatchObject({ name: 'Credit Card' })
    expect(created.id).toBeTruthy()
  })

  it('renamePaymentMethod patches the name and returns the updated payment method', async () => {
    const updated = await renamePaymentMethod('pm-1', { name: 'Debit Card (Checking)' })
    expect(updated).toMatchObject({ id: 'pm-1', name: 'Debit Card (Checking)' })
  })

  it('deletePaymentMethod resolves on success', async () => {
    await expect(deletePaymentMethod('pm-1')).resolves.toBeUndefined()
  })

  it('deletePaymentMethod maps a 409 to the delete-conflict message', async () => {
    server.use(paymentMethodDeleteConflictHandler)

    const error: unknown = await deletePaymentMethod('pm-1').catch((err: unknown) => err)

    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).status).toBe(409)
    expect((error as ApiError).message).toContain('reassign them')
  })

  it('createPaymentMethod maps a 409 to the duplicate-name message', async () => {
    server.use(paymentMethodCreateConflictHandler)

    const error: unknown = await createPaymentMethod({ name: 'Debit Card' }).catch(
      (err: unknown) => err,
    )

    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).status).toBe(409)
    expect((error as ApiError).message).toContain('already exists')
  })

  it('renamePaymentMethod maps a 409 to the duplicate-name message', async () => {
    server.use(paymentMethodRenameConflictHandler)

    const error: unknown = await renamePaymentMethod('pm-2', { name: 'Debit Card' }).catch(
      (err: unknown) => err,
    )

    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).status).toBe(409)
    expect((error as ApiError).message).toContain('already exists')
  })
})
