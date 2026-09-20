import { apiClient } from './client'
import { unwrap } from './apiError'
import type { components } from './generated/schema'

/**
 * An Institution as returned by the API (PRD S5.10, F017): a bank/broker/issuer that accounts sit
 * at. `builtIn` marks the single seeded "No institution" row - it can be renamed but never deleted.
 */
export interface Institution {
  id: string
  name: string
  builtIn: boolean
}

export type CreateInstitutionRequest = components['schemas']['CreateInstitutionRequest']
export type UpdateInstitutionRequest = components['schemas']['UpdateInstitutionRequest']

// The backend sends no message text, so every expected 409 needs its own wording here.
const CONFLICT_MESSAGE =
  'This institution is still used by an account — move its accounts to another institution before deleting it.'

const DUPLICATE_NAME_MESSAGE = 'An institution with this name already exists.'

/** Fetches every institution, sorted by name. Reused as the options source of `InstitutionSelect`. */
export async function getInstitutions(): Promise<Institution[]> {
  return unwrap(apiClient.get<Institution[]>('/institutions'))
}

export async function createInstitution(request: CreateInstitutionRequest): Promise<Institution> {
  return unwrap(apiClient.post<Institution>('/institutions', request), DUPLICATE_NAME_MESSAGE)
}

/** Renames an institution; also allowed on the built-in row. */
export async function renameInstitution(
  id: string,
  request: UpdateInstitutionRequest,
): Promise<Institution> {
  return unwrap(
    apiClient.patch<Institution>(`/institutions/${id}`, request),
    DUPLICATE_NAME_MESSAGE,
  )
}

/** Deletes an unreferenced, non-built-in institution; a 409 means an account still uses it. */
export async function deleteInstitution(id: string): Promise<void> {
  await unwrap(apiClient.delete<void>(`/institutions/${id}`), CONFLICT_MESSAGE)
}
