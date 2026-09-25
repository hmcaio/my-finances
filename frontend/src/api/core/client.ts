import axios, { isAxiosError, isCancel, type InternalAxiosRequestConfig } from 'axios'
import { logger } from '../../utils/logger'

/**
 * Single Axios instance shared by every typed client under `src/api` — the one place that owns
 * the backend's base URL (including the `/api` prefix, so call sites use bare relative paths like
 * `/categories`). Configurable via `VITE_API_BASE_URL` (see `.env.development`/`.env.production`,
 * F014 spec): dev points at the natively-run backend directly, prod is a relative `/api` that
 * nginx reverse-proxies same-origin (no CORS needed in prod).
 */
export const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  headers: { 'Content-Type': 'application/json' },
})

const REQUEST_ID_HEADER = 'X-Request-Id'

interface InFlightRequest {
  requestId: string
  startedAt: number
}

// Kept beside the request config rather than mutating it (the config is Axios's public type).
const inFlight = new WeakMap<object, InFlightRequest>()

/**
 * A per-request id the backend echoes into its access log (and nginx into its own), so one failing
 * click can be followed browser console -> nginx -> backend (F016, ADR 0011). The backend only
 * accepts `[A-Za-z0-9-]{1,64}`, which a UUID satisfies. `crypto.randomUUID` only exists in secure
 * contexts (https or localhost), so fall back to random bytes when the app is opened some other
 * way (e.g. http://<lan-ip>) rather than failing every request.
 */
function newRequestId(): string {
  if (typeof crypto.randomUUID === 'function') return crypto.randomUUID()
  return Array.from(crypto.getRandomValues(new Uint8Array(16)), (byte) =>
    byte.toString(16).padStart(2, '0'),
  ).join('')
}

/** `GET /categories` — relative url only: no base URL, no query string, no params. */
function describeRequest(config: InternalAxiosRequestConfig | undefined): string {
  const method = (config?.method ?? 'get').toUpperCase()
  const url = (config?.url ?? '').split('?')[0]
  return `${method} ${url}`
}

function elapsedMs(startedAt: number | undefined): number {
  return startedAt === undefined ? 0 : Math.round(performance.now() - startedAt)
}

apiClient.interceptors.request.use((config) => {
  const requestId = newRequestId()
  config.headers.set(REQUEST_ID_HEADER, requestId)
  inFlight.set(config, { requestId, startedAt: performance.now() })
  return config
})

// HTTP logging lives here and only here, so no page/hook call site logs a failed request itself.
// Never log request/response bodies (amounts, descriptions) — only method, path, status, timing
// and the request id. The error interceptor re-rejects the original error untouched, so
// `unwrap()`/`ApiError`/`conflictMessage` behave exactly as they would without it.
apiClient.interceptors.response.use(
  (response) => {
    const tracked = inFlight.get(response.config)
    logger.debug(
      `${describeRequest(response.config)} ${response.status} (${elapsedMs(tracked?.startedAt)} ms)`,
      { requestId: tracked?.requestId },
    )
    return response
  },
  (error: unknown) => {
    if (isCancel(error)) {
      return Promise.reject(error)
    }
    if (isAxiosError(error)) {
      const tracked = error.config ? inFlight.get(error.config) : undefined
      const context = { requestId: tracked?.requestId }
      const request = describeRequest(error.config)
      const duration = `(${elapsedMs(tracked?.startedAt)} ms)`
      const status = error.response?.status
      if (status === undefined) {
        // Never reached the server (network down, CORS, backend not running) — status 0 in ApiError.
        logger.error(`${request} failed with no response ${duration}`, context)
      } else if (status >= 500) {
        logger.error(`${request} ${status} ${duration}`, context)
      } else {
        // 4xx: expected conflicts/validation failures land here.
        logger.warn(`${request} ${status} ${duration}`, context)
      }
    }
    return Promise.reject(error)
  },
)
