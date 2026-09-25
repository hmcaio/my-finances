import { http, HttpResponse } from 'msw'
import type { Budget, BudgetReportLine } from '../../api/budgets/budgets'
import { createStore } from '../store'

/**
 * Seed data returned by the default `GET /api/budgets` handler below. `cat-1` (Groceries, see
 * `categories.ts`) is budgeted; `cat-2` (Salary, an income category) deliberately isn't, so tests
 * can exercise the "unbudgeted expense categories only" picker (F006 spec).
 */
export const seedBudgets: Budget[] = [
  {
    id: 'budget-1',
    categoryId: 'cat-1',
    currentCap: 500,
    currentCapEffectiveFrom: '2026-01',
    stopped: false,
  },
]

/** Seed data returned by the default `GET /api/budgets/report` handler below. */
export const seedBudgetReport: BudgetReportLine[] = [{ categoryId: 'cat-1', cap: 500, actual: 620 }]

/**
 * Named lookups for the sole seed row of each array, so call sites identify them by their category
 * instead of indexing by position (frontend test audit's F6 finding).
 */
export const seedGroceriesBudget = seedBudgets.find((b) => b.categoryId === 'cat-1')!
export const seedGroceriesBudgetReportLine = seedBudgetReport.find((l) => l.categoryId === 'cat-1')!

const BUDGETS_URL = '/api/budgets'

interface CreateBudgetRequestBody {
  categoryId: string
  monthlyCap: number
  effectiveFrom: string
}

interface UpdateBudgetCapRequestBody {
  monthlyCap: number
  effectiveFrom: string
}

interface StopBudgetRequestBody {
  effectiveFrom: string
}

const budgets = createStore(seedBudgets)

/**
 * Default success-path handlers for every budgets endpoint (F006's REST API), backed by an
 * in-memory store restored after each test (see `categories.ts`). The report is a fixed seed:
 * tests that care about its numbers override it.
 */
export const budgetsHandlers = [
  http.get(BUDGETS_URL, () => HttpResponse.json(budgets.list())),

  http.get(`${BUDGETS_URL}/report`, () => HttpResponse.json(seedBudgetReport)),

  http.post(BUDGETS_URL, async ({ request }) => {
    const body = (await request.json()) as CreateBudgetRequestBody
    const created = budgets.add({
      id: budgets.nextId('budget'),
      categoryId: body.categoryId,
      currentCap: body.monthlyCap,
      currentCapEffectiveFrom: body.effectiveFrom,
      stopped: false,
    })
    return HttpResponse.json(created, { status: 201 })
  }),

  http.patch(`${BUDGETS_URL}/:id/cap`, async ({ request, params }) => {
    const body = (await request.json()) as UpdateBudgetCapRequestBody
    const updated = budgets.replace(params.id as string, (row) => ({
      ...row,
      currentCap: body.monthlyCap,
      currentCapEffectiveFrom: body.effectiveFrom,
      stopped: false,
    }))
    return updated ? HttpResponse.json(updated) : new HttpResponse(null, { status: 404 })
  }),

  // Simplification: like the real API for a stop from the current month or earlier, the row turns
  // into `stopped` at once (a future-month stop is not modelled). The report is a fixed seed.
  http.post(`${BUDGETS_URL}/:id/stop`, async ({ request, params }) => {
    const body = (await request.json()) as StopBudgetRequestBody
    const updated = budgets.replace(params.id as string, (row) => ({
      ...row,
      currentCap: null,
      currentCapEffectiveFrom: body.effectiveFrom,
      stopped: true,
    }))
    return updated ? HttpResponse.json(updated) : new HttpResponse(null, { status: 404 })
  }),
]

/**
 * `409` variant for the create-conflict case (F006 spec: a non-expense category, or a category
 * that's already budgeted) - applied via `server.use(...)` in tests that exercise that path, same
 * pattern as `categoryDeleteConflictHandler`.
 */
export const budgetCreateConflictHandler = http.post(BUDGETS_URL, () =>
  HttpResponse.json({ message: 'Category cannot be budgeted' }, { status: 409 }),
)
