import { isAxiosError } from 'axios'

/**
 * Shared error type for every typed API client under `src/api` (established here in F002, reused
 * by F003+). Callers can inspect {@link ApiError.status} (e.g. to detect a `409` delete-conflict,
 * F002 spec) or just show {@link ApiError.message}.
 */
export class ApiError extends Error {
  readonly status: number

  constructor(status: number, message: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

/**
 * Runs an Axios call and unwraps its `data` on success. On failure, converts the `AxiosError`
 * (Axios throws on any non-2xx by default, unlike `fetch`) into an {@link ApiError}.
 * `conflictMessage`, when given, replaces the generic message for a `409` — e.g. "N transactions
 * use this - reassign them first" (F002 spec's delete-conflict UX; F003's account-already-closed
 * and F004's referenced-by-transaction/closed-account guards are the other `409` sources).
 */
export async function unwrap<T>(
  request: Promise<{ data: T }>,
  conflictMessage?: string,
): Promise<T> {
  try {
    const response = await request
    return response.data
  } catch (error) {
    throw toApiError(error, conflictMessage)
  }
}

function toApiError(error: unknown, conflictMessage?: string): ApiError {
  if (isAxiosError(error)) {
    const status = error.response?.status
    if (status === undefined) {
      // Request never reached the server (network error, CORS, backend down, ...).
      return new ApiError(0, error.message || 'Network error')
    }
    if (status === 409 && conflictMessage) {
      return new ApiError(409, conflictMessage)
    }
    const detail = (error.response?.data as { message?: string } | undefined)?.message
    return new ApiError(status, detail || `Request failed with status ${status}`)
  }
  return new ApiError(0, error instanceof Error ? error.message : 'Unknown error')
}
