import { describe, expect, it } from 'vitest'
import {
  institutionCreateConflictHandler,
  institutionDeleteConflictHandler,
  institutionRenameConflictHandler,
  seedInstitutions,
} from '../mocks/handlers/institutions'
import { describeNamedEntityApi } from '../test/apiContract'
import {
  CONFLICT_MESSAGE,
  DUPLICATE_NAME_MESSAGE,
  createInstitution,
  deleteInstitution,
  getInstitutions,
  renameInstitution,
  type CreateInstitutionRequest,
  type Institution,
  type UpdateInstitutionRequest,
} from './institutions'

describeNamedEntityApi<Institution, CreateInstitutionRequest, UpdateInstitutionRequest>({
  label: 'institutions',
  api: {
    get: getInstitutions,
    create: createInstitution,
    rename: renameInstitution,
    remove: deleteInstitution,
  },
  seedList: seedInstitutions,
  create: {
    request: { name: 'Inter' },
    expect: { name: 'Inter', builtIn: false, id: expect.any(String) },
  },
  rename: {
    id: 'inst-1',
    request: { name: 'Itau Unibanco' },
    expect: { id: 'inst-1', name: 'Itau Unibanco', builtIn: false },
  },
  remove: { id: 'inst-1' },
  conflict: {
    message: CONFLICT_MESSAGE,
    duplicateNameMessage: DUPLICATE_NAME_MESSAGE,
    createHandler: institutionCreateConflictHandler,
    renameHandler: institutionRenameConflictHandler,
    deleteHandler: institutionDeleteConflictHandler,
    createRequest: { name: 'Nubank' },
    renameId: 'inst-1',
    renameRequest: { name: 'Nubank' },
  },
})

// Bespoke: institutions have a `builtIn` row with no equivalent in the other flat-taxonomy
// entities, so its own behaviour (kept flagged through a get/rename) doesn't fit the shared
// contract and stays here instead.
describe('institutions API client - built-in row', () => {
  it('getInstitutions includes exactly one built-in row', async () => {
    const institutions = await getInstitutions()

    expect(institutions.filter((i) => i.builtIn)).toHaveLength(1)
  })

  it('renameInstitution keeps the built-in flag when renaming the built-in row', async () => {
    const updated = await renameInstitution('inst-none', { name: 'Sem instituicao' })

    expect(updated).toMatchObject({ id: 'inst-none', name: 'Sem instituicao', builtIn: true })
  })
})
