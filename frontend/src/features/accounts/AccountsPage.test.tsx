import { describe, expect, it } from 'vitest'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { server } from '../../mocks/server'
import { accountAlreadyClosedConflictHandler, seedAccounts } from '../../mocks/handlers/accounts'
import { AccountsPage } from './AccountsPage'

function renderPage() {
  return render(
    <MemoryRouter>
      <AccountsPage />
    </MemoryRouter>,
  )
}

function findRow(name: string) {
  const cell = screen.getByText(name)
  return within(cell.closest('tr') as HTMLElement)
}

describe('AccountsPage', () => {
  it('renders only open accounts by default', async () => {
    renderPage()

    const openAccount = seedAccounts.find((a) => !a.closed)!
    const closedAccount = seedAccounts.find((a) => a.closed)!

    expect(await screen.findByText(openAccount.name)).toBeInTheDocument()
    expect(screen.queryByText(closedAccount.name)).not.toBeInTheDocument()
  })

  it('shows closed accounts once the toggle is switched on', async () => {
    const user = userEvent.setup()
    renderPage()
    const openAccount = seedAccounts.find((a) => !a.closed)!
    const closedAccount = seedAccounts.find((a) => a.closed)!
    await screen.findByText(openAccount.name)

    await user.click(screen.getByRole('switch', { name: 'Show closed accounts' }))

    expect(await screen.findByText(closedAccount.name)).toBeInTheDocument()
  })

  it('adds a new account with the create form', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText(seedAccounts.find((a) => !a.closed)!.name)

    await user.type(screen.getByRole('textbox', { name: 'Name' }), 'New Wallet')
    await user.click(screen.getByRole('button', { name: 'Add' }))

    expect(await screen.findByText('New Wallet')).toBeInTheDocument()
  })

  it('edits an account name and institution inline', async () => {
    const user = userEvent.setup()
    renderPage()
    const openAccount = seedAccounts.find((a) => !a.closed)!
    await screen.findByText(openAccount.name)

    const row = findRow(openAccount.name)
    await user.click(row.getByRole('button', { name: 'Edit' }))
    const nameInput = row.getByRole('textbox', { name: 'Name' })
    await user.clear(nameInput)
    await user.type(nameInput, 'Renamed Account')
    await user.click(row.getByRole('button', { name: 'Save' }))

    expect(await screen.findByText('Renamed Account')).toBeInTheDocument()
  })

  it('does not offer a type/opening-balance input when editing - only name/institution', async () => {
    const user = userEvent.setup()
    renderPage()
    const openAccount = seedAccounts.find((a) => !a.closed)!
    await screen.findByText(openAccount.name)

    const row = findRow(openAccount.name)
    await user.click(row.getByRole('button', { name: 'Edit' }))

    // Only the two editable fields render as inputs in the row.
    expect(row.getAllByRole('textbox')).toHaveLength(2)
    await user.click(row.getByRole('button', { name: 'Cancel' }))
  })

  it('closes an account after confirming the dialog', async () => {
    const user = userEvent.setup()
    renderPage()
    const openAccount = seedAccounts.find((a) => !a.closed)!
    await screen.findByText(openAccount.name)

    const row = findRow(openAccount.name)
    await user.click(row.getByRole('button', { name: 'Close' }))

    expect(await screen.findByText(/not reversible/)).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Close account' }))

    await waitFor(() => expect(screen.queryByText(openAccount.name)).not.toBeInTheDocument())
  })

  it('cancelling the close dialog leaves the account untouched', async () => {
    const user = userEvent.setup()
    renderPage()
    const openAccount = seedAccounts.find((a) => !a.closed)!
    await screen.findByText(openAccount.name)

    const row = findRow(openAccount.name)
    await user.click(row.getByRole('button', { name: 'Close' }))
    await screen.findByText(/not reversible/)
    await user.click(screen.getByRole('button', { name: 'Cancel' }))

    await waitFor(() => expect(screen.queryByText(/not reversible/)).not.toBeInTheDocument())
    expect(screen.getByText(openAccount.name)).toBeInTheDocument()
  })

  it('surfaces an error message when closing fails', async () => {
    server.use(accountAlreadyClosedConflictHandler)
    const user = userEvent.setup()
    renderPage()
    const openAccount = seedAccounts.find((a) => !a.closed)!
    await screen.findByText(openAccount.name)

    const row = findRow(openAccount.name)
    await user.click(row.getByRole('button', { name: 'Close' }))
    await user.click(screen.getByRole('button', { name: 'Close account' }))

    expect(await screen.findByText('Account is already closed')).toBeInTheDocument()
    expect(screen.getByText(openAccount.name)).toBeInTheDocument()
  })
})
