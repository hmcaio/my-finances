import { http, HttpResponse } from 'msw'
import type { Category, CategoryType } from '../../api/categories'
import { createStore } from '../store'

/**
 * Seed data returned by the default `GET /api/categories` handler below. Exported so tests can
 * assert against it directly instead of duplicating the fixture (F015 spec's F002 backfill).
 */
export const seedCategories: Category[] = [
  { id: 'cat-1', name: 'Groceries', type: 'EXPENSE', builtIn: false },
  { id: 'cat-2', name: 'Salary', type: 'INCOME', builtIn: false },
  { id: 'cat-3', name: 'Other Expense', type: 'EXPENSE', builtIn: true },
  { id: 'cat-4', name: 'Other Income', type: 'INCOME', builtIn: true },
]

/**
 * Named lookups for the two non-built-in seed rows (`cat-1`/`cat-2`), so call sites identify them
 * by name instead of indexing into `seedCategories` by position (F015 spec's F002 backfill,
 * position-independent per the frontend test audit's F6 finding).
 */
export const seedGroceriesCategory = seedCategories.find((c) => c.name === 'Groceries')!
export const seedSalaryCategory = seedCategories.find((c) => c.name === 'Salary')!

const CATEGORIES_URL = '/api/categories'

interface CategoryRequestBody {
  name: string
  type?: CategoryType
}

const categories = createStore(seedCategories)

/**
 * Default success-path handlers for every categories endpoint (F002's REST API), backed by an
 * in-memory store that `resetStores()` restores after each test - a mutation must show up in the
 * next `GET`, because a successful write refetches every active query (F019).
 */
export const categoriesHandlers = [
  http.get(CATEGORIES_URL, () => HttpResponse.json(categories.list())),

  http.post(CATEGORIES_URL, async ({ request }) => {
    const body = (await request.json()) as CategoryRequestBody
    const created = categories.add({
      id: categories.nextId('cat'),
      name: body.name,
      type: body.type ?? 'EXPENSE',
      builtIn: false,
    })
    return HttpResponse.json(created, { status: 201 })
  }),

  http.patch(`${CATEGORIES_URL}/:id`, async ({ request, params }) => {
    const body = (await request.json()) as CategoryRequestBody
    const updated = categories.replace(params.id as string, (row) => ({ ...row, name: body.name }))
    return updated ? HttpResponse.json(updated) : new HttpResponse(null, { status: 404 })
  }),

  http.delete(`${CATEGORIES_URL}/:id`, ({ params }) => {
    categories.remove(params.id as string)
    return new HttpResponse(null, { status: 204 })
  }),
]

/**
 * `409` variant for the delete-conflict case (F002 spec's `conflictMessage`, now backed for real by
 * F004's `CategoryInUseException`) - applied via `server.use(...)` in tests that exercise that
 * path, same pattern as `accountAlreadyClosedConflictHandler`.
 */
export const categoryDeleteConflictHandler = http.delete(`${CATEGORIES_URL}/:id`, () =>
  HttpResponse.json({ message: 'Category is in use' }, { status: 409 }),
)

/**
 * `409` variant for the duplicate-name case on create/rename (post-F007 schema audit's
 * `CategoryNameAlreadyExistsException`) - applied via `server.use(...)` in tests that exercise that
 * path, same pattern as {@link categoryDeleteConflictHandler}. The backend never sends the real
 * exception message on the wire (`include-message: never`), so the body here is a placeholder -
 * what the frontend actually shows comes from `categories.ts`'s own hardcoded `conflictMessage`.
 */
export const categoryCreateConflictHandler = http.post(CATEGORIES_URL, () =>
  HttpResponse.json({ message: 'Category name already exists' }, { status: 409 }),
)

export const categoryRenameConflictHandler = http.patch(`${CATEGORIES_URL}/:id`, () =>
  HttpResponse.json({ message: 'Category name already exists' }, { status: 409 }),
)
