import { describe, expect, it } from 'vitest'
import { http, HttpResponse } from 'msw'
import { server } from '../../mocks/server'
import {
  goalsStore,
  projectionFor,
  seedEmergencyFundSuggestion,
  seedGoalCar,
  seedGoalFund,
  seedGoalHistory,
  seedGoalOld,
} from '../../mocks/handlers/goals'
import { ApiError } from '../core/apiError'
import {
  createGoal,
  deleteGoal,
  editGoal,
  getEmergencyFundSuggestion,
  getGoal,
  getGoalDashboard,
  getGoalHistory,
  getGoalProjection,
  getGoals,
  getProductGoalAllocations,
  replaceGoalAllocations,
  setGoalShowOnDashboard,
} from './goals'

const updateBody = {
  name: 'Car',
  targetAmount: 90000,
  targetDate: '2029-01-10',
  plannedMonthlyContribution: 2500,
  returnPessimistic: 2,
  returnBase: 4,
  returnOptimistic: 6,
  status: 'ACTIVE' as const,
  showOnDashboard: true,
}

describe('goals API client', () => {
  it('getGoals lists the non-archived goals in the standard order and can include archived ones', async () => {
    await expect(getGoals()).resolves.toEqual([seedGoalCar, seedGoalFund])
    await expect(getGoals(true)).resolves.toHaveLength(3)
  })

  it('getGoal returns one goal and an unknown id throws the not-found problem', async () => {
    await expect(getGoal('goal-car')).resolves.toEqual(seedGoalCar)

    const error = await getGoal('goal-nope').catch((e: unknown) => e)
    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).type).toBe('urn:my-finances:error:goal-not-found')
  })

  it('createGoal posts the body and returns the created goal', async () => {
    let sentBody: unknown = null
    server.use(
      http.post('/api/goals', async ({ request }) => {
        sentBody = await request.json()
        return HttpResponse.json(seedGoalCar, { status: 201 })
      }),
    )
    const request = {
      name: 'Car',
      targetAmount: 80000,
      targetDate: '2028-11-15',
      plannedMonthlyContribution: 2000,
      returnPessimistic: 2,
      returnBase: 4,
      returnOptimistic: 6,
    }

    await expect(createGoal(request)).resolves.toEqual(seedGoalCar)
    expect(sentBody).toEqual(request)
  })

  it('editGoal patches the goal with the full body', async () => {
    const edited = await editGoal('goal-car', updateBody)

    expect(edited).toMatchObject({ id: 'goal-car', targetAmount: 90000, targetDate: '2029-01-10' })
    expect(goalsStore.find('goal-car')?.targetAmount).toBe(90000)
  })

  it('setGoalShowOnDashboard patches only the flag', async () => {
    const off = await setGoalShowOnDashboard('goal-car', false)

    expect(off.showOnDashboard).toBe(false)
  })

  it('setGoalShowOnDashboard on an archived goal is the goal-archived conflict', async () => {
    const error = await setGoalShowOnDashboard('goal-old', true).catch((e: unknown) => e)

    expect((error as ApiError).status).toBe(409)
    expect((error as ApiError).type).toBe('urn:my-finances:error:goal-archived')
  })

  it('deleteGoal removes the goal', async () => {
    await deleteGoal('goal-fund')

    expect(goalsStore.find('goal-fund')).toBeUndefined()
  })

  it('replaceGoalAllocations puts the whole set to the goal', async () => {
    let sentBody: unknown = null
    let sentUrl = ''
    server.use(
      http.put('/api/goals/:id/allocations', async ({ request }) => {
        sentBody = await request.json()
        sentUrl = new URL(request.url).pathname
        return HttpResponse.json([])
      }),
    )
    const request = { allocations: [{ productId: 'iprod-btc', percentage: 25 }] }

    await replaceGoalAllocations('goal-fund', request)

    expect(sentUrl).toBe('/api/goals/goal-fund/allocations')
    expect(sentBody).toEqual(request)
  })

  it('replaceGoalAllocations over 100% for a product is the exceeds-product conflict', async () => {
    const error = await replaceGoalAllocations('goal-fund', {
      allocations: [{ productId: 'iprod-selic', percentage: 50 }],
    }).catch((e: unknown) => e)

    expect((error as ApiError).status).toBe(409)
    expect((error as ApiError).type).toBe('urn:my-finances:error:goal-allocation-exceeds-product')
  })

  it('getGoalProjection returns the three scenarios', async () => {
    await expect(getGoalProjection('goal-car')).resolves.toEqual(projectionFor(seedGoalCar))
  })

  it('getGoalHistory sends the range as query parameters', async () => {
    let search = ''
    server.use(
      http.get('/api/goals/:id/history', ({ request }) => {
        search = new URL(request.url).search
        return HttpResponse.json(seedGoalHistory)
      }),
    )

    await expect(getGoalHistory('goal-car', { from: '2026-01', to: '2026-11' })).resolves.toEqual(
      seedGoalHistory,
    )
    expect(search).toBe('?from=2026-01&to=2026-11')
  })

  it('getGoalDashboard returns a summary per flagged goal', async () => {
    const summaries = await getGoalDashboard()

    expect(summaries.map((s) => s.id)).toEqual(['goal-car', 'goal-fund'])
    expect(summaries[0]).toMatchObject({ name: 'Car', projectedShortfall: 5000 })
    expect(summaries[0]).not.toHaveProperty('allocations')
  })

  it('getEmergencyFundSuggestion sends the months and returns the suggestion', async () => {
    await expect(getEmergencyFundSuggestion(3)).resolves.toEqual({
      ...seedEmergencyFundSuggestion,
      months: 3,
      suggestedTarget: 12000,
    })
  })

  it('getProductGoalAllocations lists the goals a product is allocated to', async () => {
    await expect(getProductGoalAllocations('iprod-selic')).resolves.toEqual([
      { goalId: 'goal-car', goalName: 'Car', percentage: 60 },
    ])
    expect(seedGoalOld.status).toBe('ARCHIVED')
  })
})
