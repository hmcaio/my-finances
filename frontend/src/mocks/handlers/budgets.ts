import { http, HttpResponse } from 'msw'
import type { Budget, BudgetReportLine } from '../../api/budgets'

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

/**
 * Default success-path handlers for every budgets endpoint (F006's REST API). Create/set-cap echo
 * the request body back rather than mutating `seedBudgets`, so every test starts from the same
 * fixture regardless of execution order (same convention as `transactionsHandlers`/
 * `transfersHandlers`).
 */
export const budgetsHandlers = [
  http.get(BUDGETS_URL, () => HttpResponse.json(seedBudgets)),

  http.get(`${BUDGETS_URL}/report`, () => HttpResponse.json(seedBudgetReport)),

  http.post(BUDGETS_URL, async ({ request }) => {
    const body = (await request.json()) as CreateBudgetRequestBody
    const created: Budget = {
      id: 'budget-new',
      categoryId: body.categoryId,
      currentCap: body.monthlyCap,
      currentCapEffectiveFrom: body.effectiveFrom,
    }
    return HttpResponse.json(created, { status: 201 })
  }),

  http.patch(`${BUDGETS_URL}/:id/cap`, async ({ request, params }) => {
    const body = (await request.json()) as UpdateBudgetCapRequestBody
    const existing = seedBudgets.find((budget) => budget.id === params.id)
    const updated: Budget = {
      id: params.id as string,
      categoryId: existing?.categoryId ?? 'cat-1',
      currentCap: body.monthlyCap,
      currentCapEffectiveFrom: body.effectiveFrom,
    }
    return HttpResponse.json(updated)
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
