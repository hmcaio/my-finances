import { apiClient } from '../core/client'
import { unwrap } from '../core/apiError'
import type { components } from '../generated/schema'

/**
 * An Institution as returned by the API (PRD S5.10, F017): a bank/broker/issuer that accounts sit
 * at. `builtIn` marks the single seeded "No institution" row - it can be renamed but never deleted.
 */
export interface Institution {
  id: string
  name: string
  builtIn: boolean
}

/** Matches the backend's `TextFieldConstraints.MAX_NAME_LENGTH`, so inputs can cap what is typed. */
export const INSTITUTION_NAME_MAX_LENGTH = 100

export type CreateInstitutionRequest = components['schemas']['CreateInstitutionRequest']
export type UpdateInstitutionRequest = components['schemas']['UpdateInstitutionRequest']

/** Fetches every institution, sorted by name. Reused as the options source of `InstitutionSelect`. */
export async function getInstitutions(): Promise<Institution[]> {
  return unwrap(apiClient.get<Institution[]>('/institutions'))
}

export async function createInstitution(request: CreateInstitutionRequest): Promise<Institution> {
  return unwrap(apiClient.post<Institution>('/institutions', request))
}

/** Renames an institution; also allowed on the built-in row. */
export async function renameInstitution(
  id: string,
  request: UpdateInstitutionRequest,
): Promise<Institution> {
  return unwrap(apiClient.patch<Institution>(`/institutions/${id}`, request))
}

/** Deletes an unreferenced, non-built-in institution; a `409` is `BuiltInInstitutionException` or
 * `InstitutionInUseException` (F031/ADR 0026, see `PROBLEM_TYPE_MESSAGES` in `apiError.ts`). */
export async function deleteInstitution(id: string): Promise<void> {
  await unwrap(apiClient.delete<void>(`/institutions/${id}`))
}
