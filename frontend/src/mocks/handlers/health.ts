import { http, HttpResponse } from 'msw'
import type { HealthResponse } from '../../api/core/health'

/**
 * Seed data returned by the default `GET /api/health` handler below (F001's connectivity check).
 * Not a real aggregate (no create/update/delete) - this file exists only to stop `DashboardPage`'s
 * and `App`'s tests from each re-declaring the same inline success handler (frontend test audit's
 * F7 finding).
 */
export const seedHealth: HealthResponse = { status: 'UP', timestamp: '2026-01-01T00:00:00Z' }

const HEALTH_URL = '/api/health'

export const healthHandlers = [http.get(HEALTH_URL, () => HttpResponse.json(seedHealth))]

/** `500` variant (backend unreachable) - applied via `server.use(...)`. */
export const healthDownHandler = http.get(HEALTH_URL, () => new HttpResponse(null, { status: 500 }))
