import { describe, expect, it } from 'vitest'
import { server } from '../mocks/server'
import {
  recurringTemplateCreateConflictHandler,
  seedPendingRecurringOccurrences,
  seedRecurringTemplates,
} from '../mocks/handlers/recurringTemplates'
import { ApiError } from './apiError'
import {
  confirmPendingRecurringOccurrence,
  createRecurringTemplate,
  dismissPendingRecurringOccurrence,
  getPendingRecurringOccurrences,
  getRecurringTemplates,
  reactivateRecurringTemplate,
  setRecurringTemplateCap,
  stopRecurringTemplate,
} from './recurringTemplates'

describe('recurringTemplates API client', () => {
  it('getRecurringTemplates returns the seeded list', async () => {
    await expect(getRecurringTemplates()).resolves.toEqual(seedRecurringTemplates)
  })

  it('createRecurringTemplate posts the new template and returns the created one', async () => {
    const created = await createRecurringTemplate({
      categoryId: 'cat-1',
      accountId: 'acct-1',
      description: 'Internet',
      amount: 120,
      dayOfMonth: 15,
      effectiveFrom: '2026-03',
    })

    expect(created).toMatchObject({
      description: 'Internet',
      active: true,
      currentAmount: 120,
      currentDayOfMonth: 15,
    })
    expect(created.id).toBeTruthy()
  })

  it('createRecurringTemplate maps a 409 to the create-conflict message', async () => {
    server.use(recurringTemplateCreateConflictHandler)

    const error: unknown = await createRecurringTemplate({
      categoryId: 'cat-1',
      accountId: 'acct-2',
      description: 'Internet',
      amount: 120,
      dayOfMonth: 15,
      effectiveFrom: '2026-03',
    }).catch((err: unknown) => err)

    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).status).toBe(409)
    expect((error as ApiError).message).toContain('closed')
  })

  it('setRecurringTemplateCap patches the amount/day and returns the updated template', async () => {
    const updated = await setRecurringTemplateCap('rt-1', {
      amount: 1600,
      dayOfMonth: 10,
      effectiveFrom: '2026-04',
    })

    expect(updated).toMatchObject({
      id: 'rt-1',
      currentAmount: 1600,
      currentDayOfMonth: 10,
      currentEffectiveFrom: '2026-04',
    })
  })

  it('stopRecurringTemplate deactivates the template', async () => {
    const stopped = await stopRecurringTemplate('rt-1')

    expect(stopped.active).toBe(false)
  })

  it('reactivateRecurringTemplate reactivates the template', async () => {
    const reactivated = await reactivateRecurringTemplate('rt-1')

    expect(reactivated.active).toBe(true)
  })

  it('getPendingRecurringOccurrences returns the seeded list', async () => {
    await expect(getPendingRecurringOccurrences()).resolves.toEqual(
      seedPendingRecurringOccurrences,
    )
  })

  it('confirmPendingRecurringOccurrence posts overrides and returns the created transaction', async () => {
    const transaction = await confirmPendingRecurringOccurrence('pending-1', {
      paymentMethodId: 'pm-1',
      amount: 1650,
    })

    expect(transaction).toMatchObject({ amount: 1650, recurringTemplateVersionId: 'rtv-1' })
  })

  it('dismissPendingRecurringOccurrence resolves without a body', async () => {
    await expect(dismissPendingRecurringOccurrence('pending-1')).resolves.toBeUndefined()
  })
})
