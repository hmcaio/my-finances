import { apiClient } from '../core/client'
import { ApiError, unwrap } from '../core/apiError'

/**
 * Whether the caller has a session (F036, ADR 0029). The backend answers `GET /auth/session` with
 * `200 { authenticated: true }` or a `401 unauthenticated` problem; this folds the 401 into
 * `{ authenticated: false }` because "not logged in" is the normal answer at boot, not a failure.
 */
export interface SessionStatus {
  authenticated: boolean
}

export interface LoginRequest {
  username: string
  password: string
}

/** Any non-401 failure (backend down, 5xx) still throws, so the guard can offer a retry. */
export async function getSession(): Promise<SessionStatus> {
  try {
    return await unwrap(apiClient.get<SessionStatus>('/auth/session'))
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) return { authenticated: false }
    throw error
  }
}

/**
 * 401 = wrong username/password (the backend never says which), 429 = the client is in a
 * failed-attempt cooldown. Both surface as an `ApiError` with that `status`.
 */
export async function login(request: LoginRequest): Promise<SessionStatus> {
  return unwrap(apiClient.post<SessionStatus>('/auth/login', request))
}

export async function logout(): Promise<void> {
  await unwrap(apiClient.post<void>('/auth/logout'))
}
