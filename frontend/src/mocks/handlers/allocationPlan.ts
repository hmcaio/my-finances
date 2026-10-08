import { http, HttpResponse } from 'msw'
import type { AllocationPlanVersion } from '../../api/investments/allocationPlan'
import { createStore } from '../store'

const PLAN_URL = '/api/fii/allocation-plan'

interface SetAllocationPlanBody {
  entries: { investmentProductId: string; targetPercentage: number }[]
  effectiveFrom: string
}

/**
 * In-memory version history for the mocked allocation plan (F026, ADR 0023): no seed rows (no
 * allocation has ever been set by default), restored after each test like every other
 * `createStore`-backed handler file.
 */
export const allocationPlanVersionsStore = createStore<AllocationPlanVersion>([])

let nextVersionNumber = 1

function currentMonth(): string {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
}

function resolveEffective(month: string): AllocationPlanVersion | undefined {
  return allocationPlanVersionsStore
    .list()
    .filter((v) => v.effectiveFrom <= month)
    .sort((a, b) => (a.effectiveFrom < b.effectiveFrom ? 1 : -1))[0]
}

export const allocationPlanHandlers = [
  http.get(PLAN_URL, () => {
    const effective = resolveEffective(currentMonth())
    return effective ? HttpResponse.json(effective) : new HttpResponse(null, { status: 204 })
  }),

  http.get(`${PLAN_URL}/versions`, () =>
    HttpResponse.json(
      [...allocationPlanVersionsStore.list()].sort((a, b) =>
        a.effectiveFrom < b.effectiveFrom ? -1 : 1,
      ),
    ),
  ),

  http.put(PLAN_URL, async ({ request }) => {
    const body = (await request.json()) as SetAllocationPlanBody
    const existing = allocationPlanVersionsStore
      .list()
      .find((v) => v.effectiveFrom === body.effectiveFrom)
    if (existing) {
      const updated = allocationPlanVersionsStore.replace(existing.id, (row) => ({
        ...row,
        entries: body.entries,
      }))
      return HttpResponse.json(updated)
    }
    const created = allocationPlanVersionsStore.add({
      id: `aplanv-new-${nextVersionNumber++}`,
      planId: 'aplan-1',
      entries: body.entries,
      effectiveFrom: body.effectiveFrom,
    })
    return HttpResponse.json(created)
  }),
]

/** `409` variant: a product isn't classified under the FII sub-category. */
export const allocationPlanNotFiiConflictHandler = http.put(PLAN_URL, () =>
  HttpResponse.json({ message: 'Not an FII product' }, { status: 409 }),
)

/** `400` variant: the entries don't sum to exactly 100%, or repeat a product. */
export const allocationPlanInvalidEntriesHandler = http.put(PLAN_URL, () =>
  HttpResponse.json({ message: 'Invalid entries' }, { status: 400 }),
)

/** `404` variant: an entry references an unknown product. */
export const allocationPlanUnknownProductHandler = http.put(PLAN_URL, () =>
  HttpResponse.json({ message: 'Unknown product' }, { status: 404 }),
)
