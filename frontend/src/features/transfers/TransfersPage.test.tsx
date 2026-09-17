import { describe, expect, it } from 'vitest'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { server } from '../../mocks/server'
import { seedAccounts } from '../../mocks/handlers/accounts'
import { seedTransfers, transferClosedAccountConflictHandler } from '../../mocks/handlers/transfers'
import { TransfersPage } from './TransfersPage'

async function selectOption(
  user: ReturnType<typeof userEvent.setup>,
  comboboxName: string,
  optionName: string,
) {
  await user.click(screen.getByRole('combobox', { name: comboboxName }))
  await user.click(await screen.findByRole('option', { name: optionName }))
}

describe('TransfersPage', () => {
  it('renders the seeded transfers', async () => {
    render(<TransfersPage />)

    for (const transfer of seedTransfers) {
      expect(await screen.findByText(transfer.description)).toBeInTheDocument()
    }
  })

  it('filters by account, matching either side', async () => {
    const user = userEvent.setup()
    render(<TransfersPage />)
    await screen.findByText(seedTransfers[0].description)

    const otherAccount = seedAccounts.find(
      (a) => a.id !== seedTransfers[0].fromAccountId && a.id !== seedTransfers[0].toAccountId,
    )!
    await selectOption(user, 'Account filter', otherAccount.name)

    for (const transfer of seedTransfers) {
      const matches =
        transfer.fromAccountId === otherAccount.id || transfer.toAccountId === otherAccount.id
      if (matches) {
        expect(await screen.findByText(transfer.description)).toBeInTheDocument()
      } else {
        expect(screen.queryByText(transfer.description)).not.toBeInTheDocument()
      }
    }
  })

  it('adds a new transfer with the create form', async () => {
    const user = userEvent.setup()
    render(<TransfersPage />)
    await screen.findByText(seedTransfers[0].description)

    const openAccounts = seedAccounts.filter((a) => !a.closed)
    await user.type(screen.getByRole('spinbutton', { name: 'Amount' }), '15')
    await selectOption(user, 'From Account', openAccounts[0].name)
    await selectOption(user, 'To Account', openAccounts[1].name)
    await user.type(screen.getByRole('textbox', { name: 'Description' }), 'Move to savings')
    await user.click(screen.getByRole('button', { name: 'Add' }))

    expect(await screen.findByText('Move to savings')).toBeInTheDocument()
  })

  it("excludes the selected From account from the To account dropdown - can't pick the same account twice", async () => {
    const user = userEvent.setup()
    render(<TransfersPage />)
    await screen.findByText(seedTransfers[0].description)

    const openAccounts = seedAccounts.filter((a) => !a.closed)
    await selectOption(user, 'From Account', openAccounts[0].name)
    await user.click(screen.getByRole('combobox', { name: 'To Account' }))

    expect(screen.queryByRole('option', { name: openAccounts[0].name })).not.toBeInTheDocument()
  })

  it('excludes closed accounts from the create/edit form account dropdowns', async () => {
    const user = userEvent.setup()
    render(<TransfersPage />)
    await screen.findByText(seedTransfers[0].description)

    const closedAccount = seedAccounts.find((a) => a.closed)!
    await user.click(screen.getByRole('combobox', { name: 'From Account' }))

    expect(screen.queryByRole('option', { name: closedAccount.name })).not.toBeInTheDocument()
  })

  it('edits a transfer', async () => {
    const user = userEvent.setup()
    render(<TransfersPage />)
    const target = seedTransfers[0]
    await screen.findByText(target.description)

    const row = screen.getByText(target.description).closest('tr') as HTMLElement
    await user.click(within(row).getByRole('button', { name: 'Edit' }))

    expect(await screen.findByRole('button', { name: 'Save changes' })).toBeInTheDocument()
    const descriptionInput = screen.getByRole('textbox', { name: 'Description' })
    await user.clear(descriptionInput)
    await user.type(descriptionInput, 'Updated note')
    await user.click(screen.getByRole('button', { name: 'Save changes' }))

    expect(await screen.findByText('Updated note')).toBeInTheDocument()
  })

  it('deletes a transfer after confirming the dialog', async () => {
    const user = userEvent.setup()
    render(<TransfersPage />)
    const target = seedTransfers[0]
    await screen.findByText(target.description)

    const row = screen.getByText(target.description).closest('tr') as HTMLElement
    await user.click(within(row).getByRole('button', { name: 'Delete' }))
    await screen.findByText('Delete this transfer?')
    await user.click(screen.getByRole('button', { name: 'Delete transfer' }))

    await waitFor(() => expect(screen.queryByText(target.description)).not.toBeInTheDocument())
  })

  it('surfaces the closed-account conflict message on create', async () => {
    server.use(transferClosedAccountConflictHandler)
    const user = userEvent.setup()
    render(<TransfersPage />)
    await screen.findByText(seedTransfers[0].description)

    const openAccounts = seedAccounts.filter((a) => !a.closed)
    await user.type(screen.getByRole('spinbutton', { name: 'Amount' }), '15')
    await selectOption(user, 'From Account', openAccounts[0].name)
    await selectOption(user, 'To Account', openAccounts[1].name)
    await user.type(screen.getByRole('textbox', { name: 'Description' }), 'Move to savings')
    await user.click(screen.getByRole('button', { name: 'Add' }))

    expect(await screen.findByText(/cannot accept new transfers/)).toBeInTheDocument()
  })
})
