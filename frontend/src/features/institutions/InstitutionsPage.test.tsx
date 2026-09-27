import { afterEach, describe, expect, it } from 'vitest'
import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import {
  institutionCreateConflictHandler,
  institutionDeleteConflictHandler,
  seedInstitutions,
} from '../../mocks/handlers/institutions'
import {
  CONFLICT_MESSAGE,
  DUPLICATE_NAME_MESSAGE,
  INSTITUTION_NAME_MAX_LENGTH,
} from '../../api/institutions/institutions'
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
    conflict: { message: CONFLICT_MESSAGE, handler: institutionDeleteConflictHandler },
    duplicateName: { message: DUPLICATE_NAME_MESSAGE, handler: institutionCreateConflictHandler },
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

  it('offers no delete action on the built-in row, only rename', async () => {
    renderWithQueryClient(<InstitutionsPage />)
    await screen.findByText(builtIn.name)

    const row = await findRow(builtIn.name)
    expect(row.getByRole('button', { name: 'Rename' })).toBeInTheDocument()
    expect(row.queryByRole('button', { name: 'Delete' })).not.toBeInTheDocument()
    // Every other row does have one.
    expect(
      (await findRow(regular[0].name)).getByRole('button', { name: 'Delete' }),
    ).toBeInTheDocument()
  })

  it('renames the built-in row, which stays first and still has no delete action', async () => {
    const user = userEvent.setup()
    renderWithQueryClient(<InstitutionsPage />)
    await screen.findByText(builtIn.name)

    const row = await findRow(builtIn.name)
    await user.click(row.getByRole('button', { name: 'Rename' }))
    const input = row.getByRole('textbox')
    await user.clear(input)
    await user.type(input, 'Zzz Sem instituicao')
    await user.click(row.getByRole('button', { name: 'Save' }))

    expect(await screen.findByText('Zzz Sem instituicao')).toBeInTheDocument()
    expect(
      within(screen.getAllByRole('row')[1]).getByText('Zzz Sem instituicao'),
    ).toBeInTheDocument()
    expect(
      (await findRow('Zzz Sem instituicao')).queryByRole('button', { name: 'Delete' }),
    ).not.toBeInTheDocument()
  })
})

describe('InstitutionsPage responsive layout (F021)', () => {
  afterEach(restoreViewport)

  it('opens the add dialog full-screen on mobile', async () => {
    setViewportWidth(VIEWPORT.mobile)
    const user = userEvent.setup()
    renderWithQueryClient(<InstitutionsPage />)
    await screen.findByText(builtIn.name)

    await user.click(screen.getByRole('button', { name: 'Add institution' }))
    const dialog = await screen.findByRole('dialog')

    expect(dialog).toHaveClass('MuiDialog-paperFullScreen')
  })

  it('opens the add dialog as a regular (not full-screen) dialog on tablet', async () => {
    setViewportWidth(VIEWPORT.tablet)
    const user = userEvent.setup()
    renderWithQueryClient(<InstitutionsPage />)
    await screen.findByText(builtIn.name)

    await user.click(screen.getByRole('button', { name: 'Add institution' }))
    const dialog = await screen.findByRole('dialog')

    expect(dialog).not.toHaveClass('MuiDialog-paperFullScreen')
  })

  it('keeps the built-in row renamed inline at every size (the table never becomes cards)', async () => {
    setViewportWidth(VIEWPORT.mobile)
    const user = userEvent.setup()
    renderWithQueryClient(<InstitutionsPage />)
    await screen.findByText(builtIn.name)

    const row = await findRow(builtIn.name)
    await user.click(row.getByRole('button', { name: 'Rename' }))

    expect(row.getByRole('textbox')).toBeInTheDocument()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })
})
