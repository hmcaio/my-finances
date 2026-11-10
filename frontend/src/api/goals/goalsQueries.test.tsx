import { describe, expect, it } from 'vitest'
import { waitFor } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { server } from '../../mocks/server'
import { getSnapshot } from '../../providers/notifications/notificationStore'
import {
  projectionFor,
  seedContributionSuggestion,
  seedEmergencyFundSuggestion,
  seedGoalCar,
  seedGoalFund,
  seedGoalHistory,
} from '../../mocks/handlers/goals'
import { renderHookWithQueryClient } from '../../test/utils/renderWithQueryClient'
import { createQueryClient } from '../core/queryClient'
import {
  useCreateGoal,
  useDeleteGoal,
  useEditGoal,
  useEmergencyFundSuggestion,
  useGoal,
  useGoalContributionSuggestion,
  useGoalDashboard,
  useGoalHistory,
  useGoalProjection,
  useGoals,
  useProductGoalAllocations,
  useReplaceGoalAllocations,
  useSetGoalShowOnDashboard,
} from './goalsQueries'

describe('goals reads', () => {
  it('loads the list, one goal, the projection, the history and the dashboard', async () => {
    const { result } = renderHookWithQueryClient(() => ({
      list: useGoals(false),
      one: useGoal('goal-car'),
      projection: useGoalProjection('goal-car'),
      history: useGoalHistory('goal-car'),
      dashboard: useGoalDashboard(),
    }))

    await waitFor(() => expect(result.current.list.data).toEqual([seedGoalCar, seedGoalFund]))
    await waitFor(() => expect(result.current.one.data).toEqual(seedGoalCar))
    await waitFor(() => expect(result.current.projection.data).toEqual(projectionFor(seedGoalCar)))
    await waitFor(() => expect(result.current.history.data).toEqual(seedGoalHistory))
    await waitFor(() =>
      expect(result.current.dashboard.data?.map((s) => s.id)).toEqual(['goal-car', 'goal-fund']),
    )
  })

  it('loads the emergency-fund suggestion and the per-product allocations', async () => {
    const { result } = renderHookWithQueryClient(() => ({
      suggestion: useEmergencyFundSuggestion(6, true),
      product: useProductGoalAllocations('iprod-selic'),
    }))

    await waitFor(() => expect(result.current.suggestion.data).toEqual(seedEmergencyFundSuggestion))
    await waitFor(() =>
      expect(result.current.product.data).toEqual([
        { goalId: 'goal-car', goalName: 'Car', percentage: 60 },
      ]),
    )
  })

  it('loads the contribution suggestion of a goal and the pace on its projection', async () => {
    const { result } = renderHookWithQueryClient(() => ({
      suggestion: useGoalContributionSuggestion('goal-car', true),
      projection: useGoalProjection('goal-car'),
    }))

    await waitFor(() => expect(result.current.suggestion.data).toEqual(seedContributionSuggestion))
    await waitFor(() => expect(result.current.projection.data?.pace?.monthlyPace).toBe(500))
  })

  it('does not fetch a contribution suggestion until it is enabled or without a goal id', async () => {
    let calls = 0
    server.use(
      http.get('/api/goals/:id/contribution-suggestion', () => {
        calls += 1
        return HttpResponse.json(seedContributionSuggestion)
      }),
    )

    const { result } = renderHookWithQueryClient(() => ({
      disabled: useGoalContributionSuggestion('goal-car', false),
      noId: useGoalContributionSuggestion(undefined, true),
    }))

    expect(result.current.disabled.fetchStatus).toBe('idle')
    expect(result.current.noId.fetchStatus).toBe('idle')
    expect(calls).toBe(0)
  })

  it('does not fetch a disabled suggestion or a goal without an id', async () => {
    let suggestionCalls = 0
    server.use(
      http.get('/api/goals/emergency-fund-suggestion', () => {
        suggestionCalls += 1
        return HttpResponse.json(seedEmergencyFundSuggestion)
      }),
    )

    const { result } = renderHookWithQueryClient(() => ({
      suggestion: useEmergencyFundSuggestion(6, false),
      one: useGoal(undefined),
    }))

    expect(result.current.suggestion.fetchStatus).toBe('idle')
    expect(result.current.one.fetchStatus).toBe('idle')
    expect(suggestionCalls).toBe(0)
  })
})

describe('goals writes refresh every goal read', () => {
  it('a created goal shows up in the list and the dashboard', async () => {
    const { result } = renderHookWithQueryClient(() => ({
      list: useGoals(false),
      dashboard: useGoalDashboard(),
      create: useCreateGoal(),
    }))
    await waitFor(() => expect(result.current.list.data).toHaveLength(2))

    await result.current.create.mutateAsync({
      name: 'Trip',
      targetAmount: 5000,
      plannedMonthlyContribution: 100,
      returnPessimistic: 1,
      returnBase: 2,
      returnOptimistic: 3,
    })

    await waitFor(() => expect(result.current.list.data).toHaveLength(3))
    await waitFor(() => expect(result.current.dashboard.data).toHaveLength(3))
  })

  it('an edit refreshes the goal and the list', async () => {
    const { result } = renderHookWithQueryClient(() => ({
      one: useGoal('goal-car'),
      list: useGoals(false),
      edit: useEditGoal(),
    }))
    await waitFor(() => expect(result.current.one.data?.name).toBe('Car'))

    await result.current.edit.mutateAsync({
      id: 'goal-car',
      name: 'New car',
      targetAmount: 80000,
      targetDate: '2028-11-15',
      plannedMonthlyContribution: 2000,
      returnPessimistic: 2,
      returnBase: 4,
      returnOptimistic: 6,
      status: 'ACTIVE',
      showOnDashboard: true,
    })

    await waitFor(() => expect(result.current.one.data?.name).toBe('New car'))
    await waitFor(() => expect(result.current.list.data?.map((g) => g.name)).toContain('New car'))
  })

  it('turning the dashboard flag off removes the goal from the dashboard', async () => {
    const { result } = renderHookWithQueryClient(() => ({
      dashboard: useGoalDashboard(),
      toggle: useSetGoalShowOnDashboard(),
    }))
    await waitFor(() => expect(result.current.dashboard.data).toHaveLength(2))

    await result.current.toggle.mutateAsync({ id: 'goal-car', showOnDashboard: false })

    await waitFor(() =>
      expect(result.current.dashboard.data?.map((s) => s.id)).toEqual(['goal-fund']),
    )
  })

  it('replacing allocations refreshes the goal and the per-product list', async () => {
    const { result } = renderHookWithQueryClient(() => ({
      one: useGoal('goal-fund'),
      product: useProductGoalAllocations('iprod-btc'),
      replace: useReplaceGoalAllocations(),
    }))
    await waitFor(() => expect(result.current.product.data).toEqual([]))

    await result.current.replace.mutateAsync({
      id: 'goal-fund',
      allocations: [{ productId: 'iprod-btc', percentage: 40 }],
    })

    await waitFor(() => expect(result.current.one.data?.allocations).toHaveLength(1))
    await waitFor(() =>
      expect(result.current.product.data).toEqual([
        { goalId: 'goal-fund', goalName: 'Emergency fund', percentage: 40 },
      ]),
    )
  })

  it('a delete removes the goal from the list', async () => {
    const { result } = renderHookWithQueryClient(() => ({
      list: useGoals(false),
      remove: useDeleteGoal(),
    }))
    await waitFor(() => expect(result.current.list.data).toHaveLength(2))

    await result.current.remove.mutateAsync('goal-fund')

    await waitFor(() => expect(result.current.list.data?.map((g) => g.id)).toEqual(['goal-car']))
  })

  it('a goals write confirms itself with a short message (F040)', async () => {
    const { result } = renderHookWithQueryClient(() => ({
      toggle: useSetGoalShowOnDashboard(),
      remove: useDeleteGoal(),
    }))

    await result.current.toggle.mutateAsync({ id: 'goal-car', showOnDashboard: true })
    expect(getSnapshot()?.message).toBe('Goal updated')

    await result.current.remove.mutateAsync('goal-fund')
    expect(getSnapshot()?.message).toBe('Goal deleted')
  })

  it('a failed write invalidates nothing and surfaces the problem', async () => {
    const client = createQueryClient()
    const { result } = renderHookWithQueryClient(
      () => ({ list: useGoals(false), toggle: useSetGoalShowOnDashboard() }),
      client,
    )
    await waitFor(() => expect(result.current.list.data).toHaveLength(2))
    const before = result.current.list.dataUpdatedAt

    await expect(
      result.current.toggle.mutateAsync({ id: 'goal-old', showOnDashboard: true }),
    ).rejects.toMatchObject({ status: 409 })

    expect(result.current.list.dataUpdatedAt).toBe(before)
  })
})
