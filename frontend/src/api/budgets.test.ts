import { describe, expect, it } from 'vitest'
import { server } from '../mocks/server'
import {
  budgetCreateConflictHandler,
  seedBudgetReport,
  seedBudgets,
} from '../mocks/handlers/budgets'
import { ApiError } from './apiError'
import { createBudget, getBudgetReport, getBudgets, setBudgetCap } from './budgets'

describe('budgets API client', () => {
  it('getBudgets returns the seeded list', async () => {
    await expect(getBudgets()).resolves.toEqual(seedBudgets)
  })

  it('createBudget posts the new budget and returns the created one', async () => {
    const created = await createBudget({
      categoryId: 'cat-1',
      monthlyCap: 300,
      effectiveFrom: '2026-03',
    })

    expect(created).toMatchObject({
      categoryId: 'cat-1',
      currentCap: 300,
      currentCapEffectiveFrom: '2026-03',
    })
    expect(created.id).toBeTruthy()
  })

  it('createBudget maps a 409 to the create-conflict message', async () => {
    server.use(budgetCreateConflictHandler)

    const error: unknown = await createBudget({
      categoryId: 'cat-2',
      monthlyCap: 100,
      effectiveFrom: '2026-03',
    }).catch((err: unknown) => err)

    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).status).toBe(409)
    expect((error as ApiError).message).toContain('cannot be budgeted')
  })

  it('setBudgetCap patches the cap and returns the updated budget', async () => {
    const updated = await setBudgetCap('budget-1', { monthlyCap: 750, effectiveFrom: '2026-04' })

    expect(updated).toMatchObject({
      id: 'budget-1',
      currentCap: 750,
      currentCapEffectiveFrom: '2026-04',
    })
  })

  it('getBudgetReport returns the seeded report for the requested month', async () => {
    await expect(getBudgetReport('2026-03')).resolves.toEqual(seedBudgetReport)
  })
})
