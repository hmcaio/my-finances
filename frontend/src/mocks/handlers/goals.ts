import { http, HttpResponse } from 'msw'
import type {
  EmergencyFundSuggestion,
  Goal,
  GoalPoint,
  GoalProjection,
  GoalSummary,
  ProductGoalAllocation,
  ReplaceAllocationsRequest,
  ScenarioProjection,
  UpdateGoalRequest,
  CreateGoalRequest,
} from '../../api/goals/goals'
import { investmentProductsStore } from './investmentProducts'
import { createStore } from '../store'

/**
 * Seed goals (F039): "Car" is dated, flagged for the dashboard and holds 60% of Tesouro Selic;
 * "Emergency fund" is dateless and flagged; "Old project" is archived (so it appears only with
 * `includeArchived`). The figures are fixed - the handlers do not recompute them from the
 * allocations - so a test that cares about numbers overrides the handler.
 */
export const seedGoals: Goal[] = [
  {
    id: 'goal-car',
    name: 'Car',
    notes: null,
    targetAmount: 80000,
    targetDate: '2028-11-15',
    plannedMonthlyContribution: 2000,
    returnPessimistic: 2,
    returnBase: 4,
    returnOptimistic: 6,
    status: 'ACTIVE',
    showOnDashboard: true,
    allocatedValue: 30000,
    progressPercent: 37.5,
    targetReached: false,
    projectedShortfall: 5000,
    monthsToTarget: null,
    allocations: [{ productId: 'iprod-selic', productName: 'Tesouro Selic 2029', percentage: 60 }],
  },
  {
    id: 'goal-fund',
    name: 'Emergency fund',
    notes: 'Six months of expenses',
    targetAmount: 30000,
    targetDate: null,
    plannedMonthlyContribution: 500,
    returnPessimistic: 1,
    returnBase: 3,
    returnOptimistic: 5,
    status: 'ACTIVE',
    showOnDashboard: true,
    allocatedValue: 12000,
    progressPercent: 40,
    targetReached: false,
    projectedShortfall: null,
    monthsToTarget: 30,
    allocations: [],
  },
  {
    id: 'goal-old',
    name: 'Old project',
    notes: null,
    targetAmount: 1000,
    targetDate: null,
    plannedMonthlyContribution: 0,
    returnPessimistic: 0,
    returnBase: 0,
    returnOptimistic: 0,
    status: 'ARCHIVED',
    showOnDashboard: false,
    allocatedValue: 100,
    progressPercent: 10,
    targetReached: false,
    projectedShortfall: null,
    monthsToTarget: null,
    allocations: [{ productId: 'iprod-selic', productName: 'Tesouro Selic 2029', percentage: 70 }],
  },
]

export const seedGoalCar = seedGoals[0]
export const seedGoalFund = seedGoals[1]
export const seedGoalOld = seedGoals[2]

/** Three monthly points of a base-like scenario, enough for the chart. */
function scenario(
  name: ScenarioProjection['scenario'],
  annualReturn: number,
  start: number,
  dated: boolean,
): ScenarioProjection {
  const points: GoalPoint[] = [0, 1, 2].map((offset) => ({
    month: `${2026 + Math.floor((10 + offset) / 12)}-${String(((10 + offset) % 12) + 1).padStart(2, '0')}`,
    value: start + offset * 1000 + annualReturn * 10,
  }))
  return {
    scenario: name,
    annualReturn,
    points,
    projectedValueAtTargetDate: dated ? points[2].value : null,
    shortfallAtTargetDate: dated ? 80000 - points[2].value : null,
    requiredMonthlyContribution: dated ? 1500 + annualReturn : null,
    monthsToTarget: dated ? null : 30 - annualReturn,
  }
}

export function projectionFor(goal: Goal): GoalProjection {
  const dated = goal.targetDate !== null
  return {
    allocatedValue: goal.allocatedValue,
    latestSnapshotDate: '2026-11-01',
    scenarios: [
      scenario('PESSIMISTIC', goal.returnPessimistic, goal.allocatedValue, dated),
      scenario('BASE', goal.returnBase, goal.allocatedValue, dated),
      scenario('OPTIMISTIC', goal.returnOptimistic, goal.allocatedValue, dated),
    ],
  }
}

export const seedGoalHistory: GoalPoint[] = [
  { month: '2026-09', value: 9000 },
  { month: '2026-10', value: 10000 },
  { month: '2026-11', value: 12000 },
]

export const seedEmergencyFundSuggestion: EmergencyFundSuggestion = {
  months: 6,
  averageMonthlyExpense: 4000,
  suggestedTarget: 24000,
  monthsAveraged: 12,
}

export const goalsStore = createStore(seedGoals)

const GOALS_URL = '/api/goals'
const PROBLEM_PREFIX = 'urn:my-finances:error:'

function problem(type: string, status: number) {
  return HttpResponse.json({ type: `${PROBLEM_PREFIX}${type}` }, { status })
}

function summaryOf(goal: Goal): GoalSummary {
  return {
    id: goal.id,
    name: goal.name,
    targetAmount: goal.targetAmount,
    targetDate: goal.targetDate,
    allocatedValue: goal.allocatedValue,
    progressPercent: goal.progressPercent,
    targetReached: goal.targetReached,
    projectedShortfall: goal.projectedShortfall,
    monthsToTarget: goal.monthsToTarget,
  }
}

/** Dated goals first by date, then dateless by name ignoring case (the backend's order). */
function standardOrder(a: Goal, b: Goal): number {
  if ((a.targetDate === null) !== (b.targetDate === null)) return a.targetDate === null ? 1 : -1
  if (a.targetDate !== null && b.targetDate !== null && a.targetDate !== b.targetDate) {
    return a.targetDate < b.targetDate ? -1 : 1
  }
  return a.name.toLowerCase().localeCompare(b.name.toLowerCase())
}

/** Whether `percentages` fit under 100 for every product, counting only non-archived other goals. */
function exceedsAProduct(goalId: string, request: ReplaceAllocationsRequest): boolean {
  return request.allocations.some((share) => {
    const others = goalsStore
      .list()
      .filter((g) => g.id !== goalId && g.status !== 'ARCHIVED')
      .flatMap((g) => g.allocations)
      .filter((a) => a.productId === share.productId)
      .reduce((sum, a) => sum + a.percentage, 0)
    return others + share.percentage > 100
  })
}

function productGoalAllocations(productId: string): ProductGoalAllocation[] {
  return goalsStore
    .list()
    .filter((g) => g.status !== 'ARCHIVED')
    .flatMap((g) =>
      g.allocations
        .filter((a) => a.productId === productId)
        .map((a) => ({ goalId: g.id, goalName: g.name, percentage: a.percentage })),
    )
    .sort((a, b) => b.percentage - a.percentage)
}

/**
 * Default success-path handlers for every goals endpoint (F039), backed by an in-memory store
 * restored after each test. Writes change what the next GET returns; the computed figures
 * (`allocatedValue`, `progressPercent`, the projection) are fixed seed values, not recomputed. A
 * `PATCH` that turns `showOnDashboard` on for an archived goal answers `409` `goal-archived`; a
 * `PUT .../allocations` over 100% for a product answers `409` `goal-allocation-exceeds-product`
 * and restoring an archived goal that no longer fits answers the same, as the backend does.
 */
export const goalsHandlers = [
  http.get(GOALS_URL, ({ request }) => {
    const includeArchived = new URL(request.url).searchParams.get('includeArchived') === 'true'
    return HttpResponse.json(
      goalsStore
        .list()
        .filter((g) => includeArchived || g.status !== 'ARCHIVED')
        .sort(standardOrder),
    )
  }),

  http.get(`${GOALS_URL}/dashboard`, () =>
    HttpResponse.json(
      goalsStore
        .list()
        .filter((g) => g.showOnDashboard && g.status !== 'ARCHIVED')
        .sort(standardOrder)
        .map(summaryOf),
    ),
  ),

  http.get(`${GOALS_URL}/emergency-fund-suggestion`, ({ request }) => {
    const months = Number(new URL(request.url).searchParams.get('months') ?? '6')
    if (!Number.isInteger(months) || months < 1 || months > 120) {
      return problem('handler-method-validation', 400)
    }
    return HttpResponse.json({
      ...seedEmergencyFundSuggestion,
      months,
      suggestedTarget: months * seedEmergencyFundSuggestion.averageMonthlyExpense,
    })
  }),

  http.get(`${GOALS_URL}/:id`, ({ params }) => {
    const goal = goalsStore.find(params.id as string)
    return goal ? HttpResponse.json(goal) : problem('goal-not-found', 404)
  }),

  http.get(`${GOALS_URL}/:id/projection`, ({ params }) => {
    const goal = goalsStore.find(params.id as string)
    return goal ? HttpResponse.json(projectionFor(goal)) : problem('goal-not-found', 404)
  }),

  http.get(`${GOALS_URL}/:id/history`, ({ params }) => {
    const goal = goalsStore.find(params.id as string)
    return goal ? HttpResponse.json(seedGoalHistory) : problem('goal-not-found', 404)
  }),

  http.post(GOALS_URL, async ({ request }) => {
    const body = (await request.json()) as CreateGoalRequest
    const created = goalsStore.add({
      id: goalsStore.nextId('goal'),
      name: body.name,
      notes: body.notes ?? null,
      targetAmount: body.targetAmount,
      targetDate: body.targetDate ?? null,
      plannedMonthlyContribution: body.plannedMonthlyContribution,
      returnPessimistic: body.returnPessimistic,
      returnBase: body.returnBase,
      returnOptimistic: body.returnOptimistic,
      status: 'ACTIVE',
      showOnDashboard: body.showOnDashboard ?? true,
      allocatedValue: 0,
      progressPercent: 0,
      targetReached: false,
      projectedShortfall: body.targetDate ? body.targetAmount : null,
      monthsToTarget: null,
      allocations: [],
    })
    return HttpResponse.json(created, { status: 201 })
  }),

  http.patch(`${GOALS_URL}/:id/show-on-dashboard`, async ({ request, params }) => {
    const goal = goalsStore.find(params.id as string)
    if (!goal) return problem('goal-not-found', 404)
    const { showOnDashboard } = (await request.json()) as { showOnDashboard: boolean }
    if (showOnDashboard && goal.status === 'ARCHIVED') return problem('goal-archived', 409)
    return HttpResponse.json(goalsStore.replace(goal.id, (g) => ({ ...g, showOnDashboard })))
  }),

  http.patch(`${GOALS_URL}/:id`, async ({ request, params }) => {
    const goal = goalsStore.find(params.id as string)
    if (!goal) return problem('goal-not-found', 404)
    const body = (await request.json()) as UpdateGoalRequest
    if (goal.status === 'ARCHIVED' && body.status !== 'ARCHIVED') {
      if (exceedsAProduct(goal.id, { allocations: goal.allocations })) {
        return problem('goal-allocation-exceeds-product', 409)
      }
    }
    const updated = goalsStore.replace(goal.id, (g) => ({
      ...g,
      name: body.name,
      notes: body.notes ?? null,
      targetAmount: body.targetAmount,
      targetDate: body.targetDate ?? null,
      plannedMonthlyContribution: body.plannedMonthlyContribution,
      returnPessimistic: body.returnPessimistic,
      returnBase: body.returnBase,
      returnOptimistic: body.returnOptimistic,
      status: body.status,
      showOnDashboard: body.status === 'ARCHIVED' ? false : body.showOnDashboard,
    }))
    return HttpResponse.json(updated)
  }),

  http.delete(`${GOALS_URL}/:id`, ({ params }) => {
    if (!goalsStore.find(params.id as string)) return problem('goal-not-found', 404)
    goalsStore.remove(params.id as string)
    return new HttpResponse(null, { status: 204 })
  }),

  http.put(`${GOALS_URL}/:id/allocations`, async ({ request, params }) => {
    const goal = goalsStore.find(params.id as string)
    if (!goal) return problem('goal-not-found', 404)
    if (goal.status === 'ARCHIVED') return problem('goal-archived', 409)
    const body = (await request.json()) as ReplaceAllocationsRequest
    for (const share of body.allocations) {
      if (!investmentProductsStore.find(share.productId)) {
        return problem('investment-product-not-found', 404)
      }
    }
    if (exceedsAProduct(goal.id, body)) return problem('goal-allocation-exceeds-product', 409)
    goalsStore.replace(goal.id, (g) => ({
      ...g,
      allocations: body.allocations.map((share) => ({
        productId: share.productId,
        productName: investmentProductsStore.find(share.productId)?.name ?? '',
        percentage: share.percentage,
      })),
    }))
    return HttpResponse.json(
      body.allocations.map((share) => ({
        productId: share.productId,
        percentage: share.percentage,
      })),
    )
  }),

  http.get('/api/investment-products/:id/goal-allocations', ({ params }) => {
    if (!investmentProductsStore.find(params.id as string)) {
      return problem('investment-product-not-found', 404)
    }
    return HttpResponse.json(productGoalAllocations(params.id as string))
  }),
]

/** `409` variant of `PUT .../allocations`: a product over 100% across the other goals. */
export const allocationExceedsProductHandler = http.put(`${GOALS_URL}/:id/allocations`, () =>
  problem('goal-allocation-exceeds-product', 409),
)
