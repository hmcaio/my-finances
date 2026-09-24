import { http, HttpResponse } from 'msw'

/**
 * Default `GET /api/export` handler (F013): a few bytes standing in for the ZIP, so a test can
 * check the download happens without a real archive. Echoes nothing - per-test overrides via
 * `server.use(...)` capture the query string when they need to assert on it.
 */
export const seedExportBytes = new Uint8Array([0x50, 0x4b, 0x05, 0x06])

export const exportHandlers = [
  http.get('/api/export', () =>
    HttpResponse.arrayBuffer(seedExportBytes.buffer, {
      headers: { 'Content-Type': 'application/zip' },
    }),
  ),
]

/** `400` variant (reversed date range) - applied via `server.use(...)`. */
export const exportBadRangeHandler = http.get(
  '/api/export',
  () => new HttpResponse(null, { status: 400 }),
)
