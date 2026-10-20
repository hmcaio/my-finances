import { http, HttpResponse } from 'msw'
import type { InvestmentSegment } from '../../api/investments/investmentSegments'
import { createStore } from '../store'

/**
 * Seed data returned by the default `GET /api/investment-segments` handler below (F026, ADR
 * 0023) - the nine B3 FII sectors seeded by `V24` (F034/ADR 0028). Exported so tests can assert
 * against it instead of duplicating the fixture.
 */
export const seedInvestmentSegments: InvestmentSegment[] = [
  { id: 'iseg-shoppings', name: 'Shoppings' },
  { id: 'iseg-logistica', name: 'Logística' },
]

const SEGMENTS_URL = '/api/investment-segments'

export const investmentSegmentsStore = createStore(seedInvestmentSegments)

/**
 * Default success-path handler for the investment segments endpoint (F026's REST API), backed by
 * an in-memory store restored after each test (see `categories.ts`).
 *
 * F034/ADR 0028: read-only - the POST/PATCH/DELETE handlers and their 409 conflict variants were
 * removed along with the backend's write endpoints; every row is fixed, migration-seeded content.
 */
export const investmentSegmentsHandlers = [
  http.get(SEGMENTS_URL, () => HttpResponse.json(investmentSegmentsStore.list())),
]
