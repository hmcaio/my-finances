import { http, HttpResponse } from 'msw'
import type { PendingRecurringOccurrence, RecurringTemplate } from '../../api/recurringTemplates'

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

/**
 * Default success-path handlers for every recurring-templates endpoint (F007's REST API). Create/
 * set-cap echo the request body back rather than mutating `seedRecurringTemplates`, so every test
 * starts from the same fixture regardless of execution order (same convention as `budgetsHandlers`).
 * Stop/reactivate look up the existing seed row so the `active` flag actually flips, matching the
 * accounts handler's `close` precedent.
 */
export const recurringTemplatesHandlers = [
  http.get(RECURRING_TEMPLATES_URL, () => HttpResponse.json(seedRecurringTemplates)),

  http.get(`${RECURRING_TEMPLATES_URL}/pending`, () =>
    HttpResponse.json(seedPendingRecurringOccurrences),
  ),

  http.post(RECURRING_TEMPLATES_URL, async ({ request }) => {
    const body = (await request.json()) as CreateRecurringTemplateRequestBody
    const created: RecurringTemplate = {
      id: 'rt-new',
      categoryId: body.categoryId,
      accountId: body.accountId,
      description: body.description,
      active: true,
      currentAmount: body.amount,
      currentDayOfMonth: body.dayOfMonth,
      currentEffectiveFrom: body.effectiveFrom,
    }
    return HttpResponse.json(created, { status: 201 })
  }),

  http.patch(`${RECURRING_TEMPLATES_URL}/:id/cap`, async ({ request, params }) => {
    const body = (await request.json()) as UpdateRecurringTemplateCapRequestBody
    const existing = seedRecurringTemplates.find((t) => t.id === params.id)
    const updated: RecurringTemplate = {
      id: params.id as string,
      categoryId: existing?.categoryId ?? 'cat-1',
      accountId: existing?.accountId ?? 'acct-1',
      description: existing?.description ?? 'Rent',
      active: existing?.active ?? true,
      currentAmount: body.amount,
      currentDayOfMonth: body.dayOfMonth,
      currentEffectiveFrom: body.effectiveFrom,
    }
    return HttpResponse.json(updated)
  }),

  http.post(`${RECURRING_TEMPLATES_URL}/:id/stop`, ({ params }) => {
    const existing = seedRecurringTemplates.find((t) => t.id === params.id)
    return HttpResponse.json({ ...(existing ?? seedRecurringTemplates[0]), active: false })
  }),

  http.post(`${RECURRING_TEMPLATES_URL}/:id/reactivate`, ({ params }) => {
    const existing = seedRecurringTemplates.find((t) => t.id === params.id)
    return HttpResponse.json({ ...(existing ?? seedRecurringTemplates[0]), active: true })
  }),

  http.post(`${RECURRING_TEMPLATES_URL}/pending/:id/confirm`, async ({ request, params }) => {
    const body = (await request.json()) as { amount?: number; date?: string; accountId?: string }
    const occurrence = seedPendingRecurringOccurrences.find((o) => o.id === params.id)
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

  http.delete(
    `${RECURRING_TEMPLATES_URL}/pending/:id`,
    () => new HttpResponse(null, { status: 204 }),
  ),
]

/**
 * `409` variant for the create-conflict case (F007 spec: a closed target account) - applied via
 * `server.use(...)` in tests that exercise that path, same pattern as
 * `transferClosedAccountConflictHandler`.
 */
export const recurringTemplateCreateConflictHandler = http.post(RECURRING_TEMPLATES_URL, () =>
  HttpResponse.json({ message: 'Account is closed' }, { status: 409 }),
)
