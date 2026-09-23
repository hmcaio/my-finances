import { describe, expect, it } from 'vitest'
import { server } from '../mocks/server'
import {
  institutionCreateConflictHandler,
  institutionDeleteConflictHandler,
  institutionRenameConflictHandler,
  seedInstitutions,
} from '../mocks/handlers/institutions'
import { ApiError } from './apiError'
import {
  CONFLICT_MESSAGE,
  DUPLICATE_NAME_MESSAGE,
  createInstitution,
  deleteInstitution,
  getInstitutions,
  renameInstitution,
} from './institutions'

describe('institutions API client', () => {
  it('getInstitutions returns the seeded list including the built-in row', async () => {
    const institutions = await getInstitutions()

    expect(institutions).toEqual(seedInstitutions)
    expect(institutions.filter((i) => i.builtIn)).toHaveLength(1)
  })

  it('createInstitution posts the new institution and returns it as not built-in', async () => {
    const created = await createInstitution({ name: 'Inter' })

    expect(created).toMatchObject({ name: 'Inter', builtIn: false })
    expect(created.id).toBeTruthy()
  })

  it('renameInstitution patches the name and returns the updated institution', async () => {
    const updated = await renameInstitution('inst-1', { name: 'Itau Unibanco' })

    expect(updated).toMatchObject({ id: 'inst-1', name: 'Itau Unibanco', builtIn: false })
  })

  it('renameInstitution keeps the built-in flag when renaming the built-in row', async () => {
    const updated = await renameInstitution('inst-none', { name: 'Sem instituicao' })

    expect(updated).toMatchObject({ id: 'inst-none', name: 'Sem instituicao', builtIn: true })
  })

  it('deleteInstitution resolves on success', async () => {
    await expect(deleteInstitution('inst-1')).resolves.toBeUndefined()
  })

  it('deleteInstitution maps a 409 to the still-used message', async () => {
    server.use(institutionDeleteConflictHandler)

    const error: unknown = await deleteInstitution('inst-1').catch((err: unknown) => err)

    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).status).toBe(409)
    expect((error as ApiError).message).toBe(CONFLICT_MESSAGE)
  })

  it('createInstitution maps a 409 to the duplicate-name message', async () => {
    server.use(institutionCreateConflictHandler)

    const error: unknown = await createInstitution({ name: 'Nubank' }).catch((err: unknown) => err)

    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).status).toBe(409)
    expect((error as ApiError).message).toBe(DUPLICATE_NAME_MESSAGE)
  })

  it('renameInstitution maps a 409 to the duplicate-name message', async () => {
    server.use(institutionRenameConflictHandler)

    const error: unknown = await renameInstitution('inst-1', { name: 'Nubank' }).catch(
      (err: unknown) => err,
    )

    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).status).toBe(409)
    expect((error as ApiError).message).toBe(DUPLICATE_NAME_MESSAGE)
  })
})
