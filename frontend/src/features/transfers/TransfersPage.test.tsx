import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { server } from '../../mocks/server'
import { seedAccounts } from '../../mocks/handlers/accounts'
import {
  seedBitcoinBuyTransfer,
  seedBitcoinSellTransfer,
  seedCreditCardPaymentTransfer,
  seedTransfers,
  transferClosedAccountConflictHandler,
} from '../../mocks/handlers/transfers'
import { TRANSFER_CONFLICT_MESSAGE } from '../../api/transfers'
import { findRow, selectOption } from '../../test/testUtils'
import { expectLoadStates } from '../../test/loadStates'
import { TransfersPage } from './TransfersPage'

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
    await screen.findByText(seedCreditCardPaymentTransfer.description)

    const otherAccount = seedAccounts.find(
      (a) =>
        a.id !== seedCreditCardPaymentTransfer.fromAccountId &&
        a.id !== seedCreditCardPaymentTransfer.toAccountId,
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
    await screen.findByText(seedCreditCardPaymentTransfer.description)

    const openAccounts = seedAccounts.filter((a) => !a.closed)
    await user.type(screen.getByRole('spinbutton', { name: 'Amount' }), '15')
    await selectOption(user, 'From Account', openAccounts[0].name)
    await selectOption(user, 'To Account', openAccounts[1].name)
    await user.type(screen.getByRole('textbox', { name: 'Description' }), 'Move to savings')
    await user.click(screen.getByRole('button', { name: 'Add' }))

    expect(await screen.findByText('Move to savings')).toBeInTheDocument()
  })

  it('excludes the selected From account from the To dropdown (no same-account transfer)', async () => {
    const user = userEvent.setup()
    render(<TransfersPage />)
    await screen.findByText(seedCreditCardPaymentTransfer.description)

    const openAccounts = seedAccounts.filter((a) => !a.closed)
    await selectOption(user, 'From Account', openAccounts[0].name)
    await user.click(screen.getByRole('combobox', { name: 'To Account' }))

    expect(screen.queryByRole('option', { name: openAccounts[0].name })).not.toBeInTheDocument()
  })

  it('excludes closed accounts from the create/edit form account dropdowns', async () => {
    const user = userEvent.setup()
    render(<TransfersPage />)
    await screen.findByText(seedCreditCardPaymentTransfer.description)

    const closedAccount = seedAccounts.find((a) => a.closed)!
    await user.click(screen.getByRole('combobox', { name: 'From Account' }))

    expect(screen.queryByRole('option', { name: closedAccount.name })).not.toBeInTheDocument()
  })

  it('edits a transfer', async () => {
    const user = userEvent.setup()
    render(<TransfersPage />)
    const target = seedCreditCardPaymentTransfer
    await screen.findByText(target.description)

    const row = await findRow(target.description)
    await user.click(row.getByRole('button', { name: 'Edit' }))

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
    const target = seedCreditCardPaymentTransfer
    await screen.findByText(target.description)

    const row = await findRow(target.description)
    await user.click(row.getByRole('button', { name: 'Delete' }))
    await screen.findByText('Delete this transfer?')
    await user.click(screen.getByRole('button', { name: 'Delete transfer' }))

    await waitFor(() => expect(screen.queryByText(target.description)).not.toBeInTheDocument())
  })

  it('surfaces the closed-account conflict message on create', async () => {
    server.use(transferClosedAccountConflictHandler)
    const user = userEvent.setup()
    render(<TransfersPage />)
    await screen.findByText(seedCreditCardPaymentTransfer.description)

    const openAccounts = seedAccounts.filter((a) => !a.closed)
    await user.type(screen.getByRole('spinbutton', { name: 'Amount' }), '15')
    await selectOption(user, 'From Account', openAccounts[0].name)
    await selectOption(user, 'To Account', openAccounts[1].name)
    await user.type(screen.getByRole('textbox', { name: 'Description' }), 'Move to savings')
    await user.click(screen.getByRole('button', { name: 'Add' }))

    expect(await screen.findByText(TRANSFER_CONFLICT_MESSAGE)).toBeInTheDocument()
  })

  // The table's load state is `combineLoadState(accountsState, transfersState)` (F2 audit finding);
  // intercepting either composed source shows the same skeleton/failure - accounts is the simpler
  // body to fake a delayed/failing response for.
  expectLoadStates({
    render: () => render(<TransfersPage />),
    url: '/api/accounts',
    successBody: seedAccounts,
    loadedText: seedCreditCardPaymentTransfer.description,
  })
})

describe('TransfersPage local-time defaults', () => {
  beforeEach(() => {
    // 23:30 on 31 March in UTC-3 is already 1 April in UTC.
    vi.stubEnv('TZ', 'America/Sao_Paulo')
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date(2026, 2, 31, 23, 30))
  })

  afterEach(() => {
    vi.useRealTimers()
    vi.unstubAllEnvs()
  })

  it('defaults the form date to the local date, not the UTC date', async () => {
    render(<TransfersPage />)

    expect(await screen.findByLabelText('Date')).toHaveValue('2026-03-31')
  })
})

describe('TransfersPage trades', () => {
  it('labels a tagged transfer Buy or Sell with its product name', async () => {
    server.use(
      http.get('/api/transfers', () =>
        HttpResponse.json({
          content: [seedBitcoinSellTransfer, seedBitcoinBuyTransfer],
          page: { size: 20, number: 0, totalElements: 2, totalPages: 1 },
        }),
      ),
    )
    render(<TransfersPage />)

    expect(
      await screen.findByText('Buy Bitcoin', { selector: '.MuiChip-label' }),
    ).toBeInTheDocument()
    expect(screen.getByText('Sell Bitcoin', { selector: '.MuiChip-label' })).toBeInTheDocument()
  })
})
