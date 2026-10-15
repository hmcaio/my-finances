import { afterEach, describe, expect, it } from 'vitest'
import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import {
  institutionCreateConflictHandler,
  institutionDeleteConflictHandler,
  seedInstitutions,
} from '../../mocks/handlers/institutions'
import { INSTITUTION_NAME_MAX_LENGTH } from '../../api/institutions/institutions'
import { resolveErrorMessage } from '../../test/errorMessages'
import { findRow } from '../../test/testUtils'
import { describeSettingsPage } from '../../test/settingsPageContract'
import { restoreViewport, setViewportWidth, VIEWPORT } from '../../test/viewport'
import { InstitutionsPage } from './InstitutionsPage'
import { renderWithQueryClient } from '../../test/renderWithQueryClient'

const builtIn = seedInstitutions.find((i) => i.builtIn)!
const regular = seedInstitutions.filter((i) => !i.builtIn)

describe('InstitutionsPage', () => {
  describeSettingsPage({
    page: <InstitutionsPage />,
    seedRows: seedInstitutions,
    renameTarget: regular[0],
    deleteTarget: regular[1],
    newName: 'Inter',
    addButtonLabel: 'Add institution',
    conflict: {
      message: resolveErrorMessage('urn:my-finances:error:institution-in-use'),
      handler: institutionDeleteConflictHandler,
    },
    duplicateName: {
      message: resolveErrorMessage('urn:my-finances:error:institution-name-already-exists'),
      handler: institutionCreateConflictHandler,
    },
    maxLength: INSTITUTION_NAME_MAX_LENGTH,
    loadStates: { url: '/api/institutions', successBody: seedInstitutions },
  })

  // Bespoke: the built-in "No institution" row, its fixed first position, and the fact renaming
  // it doesn't move or unlock it have no equivalent in the other settings pages, so they stay here
  // on top of the shared contract.

  it('renders the seeded institutions with the built-in row first', async () => {
    renderWithQueryClient(<InstitutionsPage />)

    for (const institution of seedInstitutions) {
      expect(await screen.findByText(institution.name)).toBeInTheDocument()
    }
    // Row 0 is the header. The built-in row leads even though the backend list has it second.
    const rows = screen.getAllByRole('row')
    expect(within(rows[1]).getByText(builtIn.name)).toBeInTheDocument()
    expect(within(rows[2]).getByText(regular[0].name)).toBeInTheDocument()
    expect(within(rows[3]).getByText(regular[1].name)).toBeInTheDocument()
  })

  it('offers neither rename nor delete on the built-in row (F034/ADR 0028)', async () => {
    renderWithQueryClient(<InstitutionsPage />)
    await screen.findByText(builtIn.name)

    const row = await findRow(builtIn.name)
    expect(row.queryByRole('button', { name: 'Rename' })).not.toBeInTheDocument()
    expect(row.queryByRole('button', { name: 'Delete' })).not.toBeInTheDocument()
    // Every other row has both.
    const otherRow = await findRow(regular[0].name)
    expect(otherRow.getByRole('button', { name: 'Rename' })).toBeInTheDocument()
    expect(otherRow.getByRole('button', { name: 'Delete' })).toBeInTheDocument()
  })
})

describe('InstitutionsPage responsive layout (F021)', () => {
  afterEach(restoreViewport)

  it('renames a regular row inline at every size (the table never becomes cards)', async () => {
    setViewportWidth(VIEWPORT.mobile)
    const user = userEvent.setup({ delay: null })
    renderWithQueryClient(<InstitutionsPage />)
    await screen.findByText(regular[0].name)

    const row = await findRow(regular[0].name)
    await user.click(row.getByRole('button', { name: 'Rename' }))

    expect(row.getByRole('textbox')).toBeInTheDocument()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })
})
