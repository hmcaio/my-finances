import { describe, expect, it } from 'vitest'
import { waitFor } from '@testing-library/react'
import {
  budgetCreateConflictHandler,
  seedBudgetReport,
  seedBudgets,
} from '../../mocks/handlers/budgets'
import { server } from '../../mocks/server'
import { renderHookWithQueryClient } from '../../test/renderWithQueryClient'
import { CREATE_CONFLICT_MESSAGE } from './budgets'
import { useBudgetReport, useBudgets, useCreateBudget, useSetBudgetCap } from './budgetsQueries'

describe('budgets hooks', () => {
  it('loads the budgets and the month report', async () => {
    const { result } = renderHookWithQueryClient(() => ({
      list: useBudgets(),
      report: useBudgetReport('2026-01'),
    }))

    await waitFor(() => expect(result.current.list.data).toEqual(seedBudgets))
    await waitFor(() => expect(result.current.report.data).toEqual(seedBudgetReport))
  })

  it('refetches the budgets after a cap change and a create', async () => {
    const { result } = renderHookWithQueryClient(() => ({
      list: useBudgets(),
      setCap: useSetBudgetCap(),
      create: useCreateBudget(),
    }))
    await waitFor(() => expect(result.current.list.data).toBeDefined())

    await result.current.setCap.mutateAsync({
      id: seedBudgets[0].id,
      monthlyCap: 800,
      effectiveFrom: '2026-09',
    })
    await waitFor(() => expect(result.current.list.data?.[0].currentCap).toBe(800))

    await result.current.create.mutateAsync({
      categoryId: 'cat-3',
      monthlyCap: 50,
      effectiveFrom: '2026-09',
    })
    await waitFor(() => expect(result.current.list.data).toHaveLength(2))
  })

  it('keeps the create conflict message on failure', async () => {
    server.use(budgetCreateConflictHandler)
    const { result } = renderHookWithQueryClient(() => useCreateBudget())

    await expect(
      result.current.mutateAsync({ categoryId: 'cat-1', monthlyCap: 1, effectiveFrom: '2026-09' }),
    ).rejects.toThrow(CREATE_CONFLICT_MESSAGE)
  })
})
