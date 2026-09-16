import { http, HttpResponse } from 'msw'
import type { Category, CategoryType } from '../../api/categories'

/**
 * Seed data returned by the default `GET /api/categories` handler below. Exported so tests can
 * assert against it directly instead of duplicating the fixture (F015 spec's F002 backfill).
 */
export const seedCategories: Category[] = [
  { id: 'cat-1', name: 'Groceries', type: 'EXPENSE' },
  { id: 'cat-2', name: 'Salary', type: 'INCOME' },
]

const CATEGORIES_URL = '/api/categories'

interface CategoryRequestBody {
  name: string
  type?: CategoryType
}

/**
 * Default success-path handlers for every categories endpoint (F002's REST API). Requests are
 * answered purely from the request itself (echoing the body back with a generated/known id)
 * rather than mutating `seedCategories`, so every test starts from the same fixture regardless of
 * execution order - `src/test/setup.ts`'s `server.resetHandlers()` only needs to undo per-test
 * `server.use(...)` overrides (like the 409 variant below), not any stored mutation.
 */
export const categoriesHandlers = [
  http.get(CATEGORIES_URL, () => HttpResponse.json(seedCategories)),

  http.post(CATEGORIES_URL, async ({ request }) => {
    const body = (await request.json()) as CategoryRequestBody
    const created: Category = { id: 'cat-new', name: body.name, type: body.type ?? 'EXPENSE' }
    return HttpResponse.json(created, { status: 201 })
  }),

  http.patch(`${CATEGORIES_URL}/:id`, async ({ request, params }) => {
    const body = (await request.json()) as CategoryRequestBody
    const existing = seedCategories.find((category) => category.id === params.id)
    const updated: Category = {
      id: params.id as string,
      name: body.name,
      type: existing?.type ?? 'EXPENSE',
    }
    return HttpResponse.json(updated)
  }),

  http.delete(`${CATEGORIES_URL}/:id`, () => new HttpResponse(null, { status: 204 })),
]

/**
 * `409` variant for the delete-conflict case (F002 spec's `conflictMessage`, now backed for real by
 * F004's `CategoryInUseException`) - applied via `server.use(...)` in tests that exercise that
 * path, same pattern as `accountAlreadyClosedConflictHandler`.
 */
export const categoryDeleteConflictHandler = http.delete(`${CATEGORIES_URL}/:id`, () =>
  HttpResponse.json({ message: 'Category is in use' }, { status: 409 }),
)
