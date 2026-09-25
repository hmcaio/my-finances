import { http, HttpResponse } from 'msw'
import type {
  PendingRecurringOccurrence,
  RecurringTemplate,
} from '../../api/recurringTemplates/recurringTemplates'
import { createStore } from '../store'

/**
 * Seed data returned by the default `GET /api/recurring-templates` handler below. `cat-1`
 * (Groceries) / `acct-1` (Checking, open) are the same seed category/account `budgets.ts`/
 * `transactions.ts` use.
 */
export const seedRecurringTemplates: RecurringTemplate[] = [
  {
    id: 'rt-1',
    categoryId: 'cat-1',
    accountId: 'acct-1',
    description: 'Rent',
    active: true,
    currentAmount: 1500,
    currentDayOfMonth: 5,
    currentEffectiveFrom: '2026-01',
  },
]

/** Seed data returned by the default `GET /api/recurring-templates/pending` handler below. */
export const seedPendingRecurringOccurrences: PendingRecurringOccurrence[] = [
  {
    id: 'pending-1',
    templateId: 'rt-1',
    templateVersionId: 'rtv-1',
    dueDate: '2026-03-05',
    amount: 1500,
  },
]

/**
 * Named lookups for the sole seed row of each array, so call sites identify them by description
 * instead of indexing by position (frontend test audit's F6 finding).
 */
export const seedRentRecurringTemplate = seedRecurringTemplates.find(
  (t) => t.description === 'Rent',
)!
export const seedRentPendingOccurrence = seedPendingRecurringOccurrences.find(
  (o) => o.templateId === seedRentRecurringTemplate.id,
)!

const RECURRING_TEMPLATES_URL = '/api/recurring-templates'

interface CreateRecurringTemplateRequestBody {
  categoryId: string
  accountId: string
  description: string
  amount: number
  dayOfMonth: number
  effectiveFrom: string
}

interface UpdateRecurringTemplateCapRequestBody {
  amount: number
  dayOfMonth: number
  effectiveFrom: string
}

const templates = createStore(seedRecurringTemplates)
const pendingOccurrences = createStore(seedPendingRecurringOccurrences)

/**
 * Default success-path handlers for every recurring-templates endpoint (F007's REST API), backed
 * by in-memory stores restored after each test (see `categories.ts`). Stop/reactivate flip the
 * `active` flag; confirming or dismissing removes the pending occurrence.
 */
export const recurringTemplatesHandlers = [
  http.get(RECURRING_TEMPLATES_URL, () => HttpResponse.json(templates.list())),

  http.get(`${RECURRING_TEMPLATES_URL}/pending`, () =>
    HttpResponse.json(pendingOccurrences.list()),
  ),

  http.post(RECURRING_TEMPLATES_URL, async ({ request }) => {
    const body = (await request.json()) as CreateRecurringTemplateRequestBody
    const created = templates.add({
      id: templates.nextId('rt'),
      categoryId: body.categoryId,
      accountId: body.accountId,
      description: body.description,
      active: true,
      currentAmount: body.amount,
      currentDayOfMonth: body.dayOfMonth,
      currentEffectiveFrom: body.effectiveFrom,
    })
    return HttpResponse.json(created, { status: 201 })
  }),

  http.patch(`${RECURRING_TEMPLATES_URL}/:id/cap`, async ({ request, params }) => {
    const body = (await request.json()) as UpdateRecurringTemplateCapRequestBody
    const updated = templates.replace(params.id as string, (row) => ({
      ...row,
      currentAmount: body.amount,
      currentDayOfMonth: body.dayOfMonth,
      currentEffectiveFrom: body.effectiveFrom,
    }))
    return updated ? HttpResponse.json(updated) : new HttpResponse(null, { status: 404 })
  }),

  http.post(`${RECURRING_TEMPLATES_URL}/:id/stop`, ({ params }) => {
    const updated = templates.replace(params.id as string, (row) => ({ ...row, active: false }))
    return updated ? HttpResponse.json(updated) : new HttpResponse(null, { status: 404 })
  }),

  http.post(`${RECURRING_TEMPLATES_URL}/:id/reactivate`, ({ params }) => {
    const updated = templates.replace(params.id as string, (row) => ({ ...row, active: true }))
    return updated ? HttpResponse.json(updated) : new HttpResponse(null, { status: 404 })
  }),

  http.post(`${RECURRING_TEMPLATES_URL}/pending/:id/confirm`, async ({ request, params }) => {
    const body = (await request.json()) as { amount?: number; date?: string; accountId?: string }
    const occurrence = pendingOccurrences.find(params.id as string)
    pendingOccurrences.remove(params.id as string)
    return HttpResponse.json({
      id: 'txn-new',
      date: body.date ?? occurrence?.dueDate ?? '2026-03-05',
      amount: body.amount ?? occurrence?.amount ?? 0,
      categoryId: 'cat-1',
      type: 'EXPENSE',
      accountId: body.accountId ?? 'acct-1',
      paymentMethodId: 'pm-1',
      recurringTemplateVersionId: occurrence?.templateVersionId ?? 'rtv-1',
      description: 'Rent',
      additionalNotes: null,
    })
  }),

  http.delete(`${RECURRING_TEMPLATES_URL}/pending/:id`, ({ params }) => {
    pendingOccurrences.remove(params.id as string)
    return new HttpResponse(null, { status: 204 })
  }),
]

/**
 * `409` variant for the create-conflict case (F007 spec: a closed target account) - applied via
 * `server.use(...)` in tests that exercise that path, same pattern as
 * `transferClosedAccountConflictHandler`.
 */
export const recurringTemplateCreateConflictHandler = http.post(RECURRING_TEMPLATES_URL, () =>
  HttpResponse.json({ message: 'Account is closed' }, { status: 409 }),
)
