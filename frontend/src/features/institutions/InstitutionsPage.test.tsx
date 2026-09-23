import { describe, expect, it } from 'vitest'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { server } from '../../mocks/server'
import {
  institutionCreateConflictHandler,
  institutionDeleteConflictHandler,
  seedInstitutions,
} from '../../mocks/handlers/institutions'
import { findRow } from '../../test/testUtils'
import { InstitutionsPage } from './InstitutionsPage'

const builtIn = seedInstitutions.find((i) => i.builtIn)!
const regular = seedInstitutions.filter((i) => !i.builtIn)

describe('InstitutionsPage', () => {
  it('renders the seeded institutions with the built-in row first', async () => {
    render(<InstitutionsPage />)

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
    render(<InstitutionsPage />)
    await screen.findByText(builtIn.name)

    const row = await findRow(builtIn.name)
    expect(row.getByRole('button', { name: 'Rename' })).toBeInTheDocument()
    expect(row.queryByRole('button', { name: 'Delete' })).not.toBeInTheDocument()
    // Every other row does have one.
    expect(
      (await findRow(regular[0].name)).getByRole('button', { name: 'Delete' }),
    ).toBeInTheDocument()
  })

  it('adds a new institution', async () => {
    const user = userEvent.setup()
    render(<InstitutionsPage />)
    await screen.findByText(regular[0].name)

    await user.type(screen.getByLabelText('Name'), 'Inter')
    await user.click(screen.getByRole('button', { name: 'Add' }))

    expect(await screen.findByText('Inter')).toBeInTheDocument()
  })

  it('caps the name inputs at the backend length limit', async () => {
    const user = userEvent.setup()
    render(<InstitutionsPage />)
    await screen.findByText(regular[0].name)

    expect(screen.getByLabelText('Name')).toHaveAttribute('maxlength', '100')

    // While a row is being edited its name is an input value, so hold on to the row first.
    const row = await findRow(regular[0].name)
    await user.click(row.getByRole('button', { name: 'Rename' }))
    expect(row.getByRole('textbox')).toHaveAttribute('maxlength', '100')
  })

  it('renames an institution inline', async () => {
    const user = userEvent.setup()
    render(<InstitutionsPage />)
    await screen.findByText(regular[0].name)

    const row = await findRow(regular[0].name)
    await user.click(row.getByRole('button', { name: 'Rename' }))
    const input = row.getByRole('textbox')
    await user.clear(input)
    await user.type(input, 'Itau Unibanco')
    await user.click(row.getByRole('button', { name: 'Save' }))

    expect(await screen.findByText('Itau Unibanco')).toBeInTheDocument()
    expect(screen.queryByText(regular[0].name)).not.toBeInTheDocument()
  })

  it('renames the built-in row, which stays first and still has no delete action', async () => {
    const user = userEvent.setup()
    render(<InstitutionsPage />)
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

  it('deletes an institution', async () => {
    const user = userEvent.setup()
    render(<InstitutionsPage />)
    const name = regular[1].name
    await screen.findByText(name)

    await user.click((await findRow(name)).getByRole('button', { name: 'Delete' }))

    await waitFor(() => expect(screen.queryByText(name)).not.toBeInTheDocument())
  })

  it('surfaces the 409 message when delete fails and keeps the row', async () => {
    server.use(institutionDeleteConflictHandler)
    const user = userEvent.setup()
    render(<InstitutionsPage />)
    const name = regular[0].name
    await screen.findByText(name)

    await user.click((await findRow(name)).getByRole('button', { name: 'Delete' }))

    expect(await screen.findByText(/still used by an account/)).toBeInTheDocument()
    // A 409 must not optimistically remove the row.
    expect(screen.getByText(name)).toBeInTheDocument()
  })

  it('surfaces the duplicate-name message when adding fails', async () => {
    server.use(institutionCreateConflictHandler)
    const user = userEvent.setup()
    render(<InstitutionsPage />)
    await screen.findByText(regular[0].name)

    await user.type(screen.getByLabelText('Name'), regular[0].name)
    await user.click(screen.getByRole('button', { name: 'Add' }))

    expect(await screen.findByText(/already exists/)).toBeInTheDocument()
  })
})
