/**
 * Shared error type + response-checking helper for every typed API client under `src/api`
 * (established here in F002, reused by F003+). Callers can inspect {@link ApiError.status} (e.g.
 * to detect a `409` delete-conflict, F002 spec) or just show {@link ApiError.message}.
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
 * Throws an {@link ApiError} if `response` isn't a 2xx. `conflictMessage`, when given, replaces
 * the generic message for a `409` — e.g. "N transactions use this - reassign them first" (F002
 * spec's delete-conflict UX). No endpoint returns 409 yet (the referenced-by-transaction delete
 * guard is deferred to F004 - see F002 plan.md), but the plumbing is ready for when one does.
 */
export async function assertOk(response: Response, conflictMessage?: string): Promise<void> {
  if (response.ok) return

  if (response.status === 409 && conflictMessage) {
    throw new ApiError(409, conflictMessage)
  }

  let detail = ''
  try {
    const body = (await response.json()) as { message?: string }
    detail = body?.message ?? ''
  } catch {
    // Response body wasn't JSON (or was empty) - fall back to the generic message below.
  }
  throw new ApiError(response.status, detail || `Request failed with status ${response.status}`)
}
