import { http, HttpResponse } from 'msw'
import type { Institution } from '../../api/institutions'

/**
 * Seed data returned by the default `GET /api/institutions` handler below, in the backend's order
 * (sorted by name), so the built-in "No institution" row is NOT first here - the UI is what puts
 * it first. Exported so tests can assert against it instead of duplicating the fixture.
 */
export const seedInstitutions: Institution[] = [
  { id: 'inst-1', name: 'Itau', builtIn: false },
  { id: 'inst-none', name: 'No institution', builtIn: true },
  { id: 'inst-2', name: 'Nubank', builtIn: false },
]

/** The built-in row's id: the default for a new account. */
export const BUILT_IN_INSTITUTION_ID = 'inst-none'

const INSTITUTIONS_URL = '/api/institutions'

interface InstitutionRequestBody {
  name: string
}

/**
 * Default success-path handlers for every institutions endpoint (F017's REST API). Same
 * request-echoing approach as `categories.ts` - no mutation of `seedInstitutions`, so every test
 * starts from the same fixture regardless of execution order.
 */
export const institutionsHandlers = [
  http.get(INSTITUTIONS_URL, () => HttpResponse.json(seedInstitutions)),

  http.post(INSTITUTIONS_URL, async ({ request }) => {
    const body = (await request.json()) as InstitutionRequestBody
    const created: Institution = { id: 'inst-new', name: body.name, builtIn: false }
    return HttpResponse.json(created, { status: 201 })
  }),

  http.patch(`${INSTITUTIONS_URL}/:id`, async ({ request, params }) => {
    const body = (await request.json()) as InstitutionRequestBody
    const existing = seedInstitutions.find((institution) => institution.id === params.id)
    const updated: Institution = {
      id: params.id as string,
      name: body.name,
      builtIn: existing?.builtIn ?? false,
    }
    return HttpResponse.json(updated)
  }),

  http.delete(`${INSTITUTIONS_URL}/:id`, () => new HttpResponse(null, { status: 204 })),
]

/**
 * `409` variant for deleting an institution an account still references (F017's
 * `InstitutionInUseException`) - applied via `server.use(...)`. The backend sends no message text
 * (`include-message: never`), so the body is a placeholder: what the UI shows comes from
 * `institutions.ts`'s own `conflictMessage`.
 */
export const institutionDeleteConflictHandler = http.delete(`${INSTITUTIONS_URL}/:id`, () =>
  HttpResponse.json({ message: 'Institution is in use' }, { status: 409 }),
)

/** `409` variants for the duplicate-name case on create/rename (`InstitutionNameAlreadyExistsException`). */
export const institutionCreateConflictHandler = http.post(INSTITUTIONS_URL, () =>
  HttpResponse.json({ message: 'Institution name already exists' }, { status: 409 }),
)

export const institutionRenameConflictHandler = http.patch(`${INSTITUTIONS_URL}/:id`, () =>
  HttpResponse.json({ message: 'Institution name already exists' }, { status: 409 }),
)
