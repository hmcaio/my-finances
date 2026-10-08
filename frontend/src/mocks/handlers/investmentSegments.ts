import { http, HttpResponse } from 'msw'
import type { InvestmentSegment } from '../../api/investments/investmentSegments'
import { createStore } from '../store'

/**
 * Seed data returned by the default `GET /api/investment-segments` handler below (F026, ADR
 * 0023). Exported so tests can assert against it instead of duplicating the fixture.
 */
export const seedInvestmentSegments: InvestmentSegment[] = [
  { id: 'iseg-shoppings', name: 'Shoppings' },
  { id: 'iseg-logistica', name: 'Logistica' },
]

const SEGMENTS_URL = '/api/investment-segments'

interface SegmentRequestBody {
  name: string
}

export const investmentSegmentsStore = createStore(seedInvestmentSegments)

/**
 * Default success-path handlers for the investment segments endpoints (F026's REST API), backed
 * by an in-memory store restored after each test (see `categories.ts`).
 */
export const investmentSegmentsHandlers = [
  http.get(SEGMENTS_URL, () => HttpResponse.json(investmentSegmentsStore.list())),

  http.post(SEGMENTS_URL, async ({ request }) => {
    const body = (await request.json()) as SegmentRequestBody
    const created = investmentSegmentsStore.add({
      id: investmentSegmentsStore.nextId('iseg'),
      name: body.name,
    })
    return HttpResponse.json(created, { status: 201 })
  }),

  http.patch(`${SEGMENTS_URL}/:id`, async ({ request, params }) => {
    const body = (await request.json()) as SegmentRequestBody
    const updated = investmentSegmentsStore.replace(params.id as string, (row) => ({
      ...row,
      name: body.name,
    }))
    return updated ? HttpResponse.json(updated) : new HttpResponse(null, { status: 404 })
  }),

  http.delete(`${SEGMENTS_URL}/:id`, ({ params }) => {
    investmentSegmentsStore.remove(params.id as string)
    return new HttpResponse(null, { status: 204 })
  }),
]

/** `409` variant for deleting a segment still referenced by a product. */
export const investmentSegmentDeleteConflictHandler = http.delete(`${SEGMENTS_URL}/:id`, () =>
  HttpResponse.json({ message: 'Investment segment is in use' }, { status: 409 }),
)

/** `409` variants for the duplicate-name case on create/rename. */
export const investmentSegmentCreateConflictHandler = http.post(SEGMENTS_URL, () =>
  HttpResponse.json({ message: 'Name already exists' }, { status: 409 }),
)

export const investmentSegmentRenameConflictHandler = http.patch(`${SEGMENTS_URL}/:id`, () =>
  HttpResponse.json({ message: 'Name already exists' }, { status: 409 }),
)
