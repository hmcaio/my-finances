import { http, HttpResponse } from 'msw'

/**
 * Default `POST /api/reports` handler (F030, replacing F013's `GET /api/export`): a few bytes
 * standing in for the generated file, so a test can check the download happens without a real
 * report. Content-Type defaults to CSV (the common single-type case); per-test overrides via
 * `server.use(...)` return a different content type (`application/zip`/`application/pdf`) or
 * inspect the request body when they need to assert on it.
 */
export const seedReportBytes = new Uint8Array([0x61, 0x2c, 0x62, 0x0d, 0x0a])

export const reportsHandlers = [
  http.post('/api/reports', () =>
    HttpResponse.arrayBuffer(seedReportBytes.buffer, {
      headers: { 'Content-Type': 'text/csv' },
    }),
  ),
]

/** `400` variant (empty/unknown `types`) - applied via `server.use(...)`. */
export const reportsBadRequestHandler = http.post(
  '/api/reports',
  () => new HttpResponse(null, { status: 400 }),
)

/** A multi-file (zip) response - applied via `server.use(...)` for a multi-type selection. */
export const reportsZipHandler = http.post('/api/reports', () =>
  HttpResponse.arrayBuffer(seedReportBytes.buffer, {
    headers: { 'Content-Type': 'application/zip' },
  }),
)
