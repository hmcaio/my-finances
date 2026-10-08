import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { server } from '../../mocks/server'
import {
  accountsWithInvestmentHandler,
  seedAccounts,
  seedInvestmentAccount,
} from '../../mocks/handlers/accounts'
import {
  seedBitcoinBuyTransfer,
  seedBitcoinSellTransfer,
  seedCreditCardPaymentTransfer,
  seedTransfers,
  transferClosedAccountConflictHandler,
} from '../../mocks/handlers/transfers'
import { TRANSFER_CONFLICT_MESSAGE } from '../../api/transfers/transfers'
import { findRow, renderWithRouter, selectOption } from '../../test/testUtils'
import { restoreViewport, setViewportWidth, VIEWPORT } from '../../test/viewport'
import { expectLoadStates } from '../../test/loadStates'
import { TransfersPage } from './TransfersPage'

/** The form only exists inside the header's Add dialog. */
async function openAddDialog(user: ReturnType<typeof userEvent.setup>) {
  await user.click(await screen.findByRole('button', { name: 'Add transfer' }))
  return screen.findByRole('dialog')
}

async function fillAndSubmitAddForm(user: ReturnType<typeof userEvent.setup>) {
  const openAccounts = seedAccounts.filter((a) => !a.closed)
  await user.type(await screen.findByRole('spinbutton', { name: 'Amount' }), '15')
  await selectOption(user, 'From Account', openAccounts[0].name)
  await selectOption(user, 'To Account', openAccounts[1].name)
  await user.type(screen.getByRole('textbox', { name: 'Description' }), 'Move to savings')
  await user.click(screen.getByRole('button', { name: 'Add' }))
}

describe('TransfersPage', () => {
  // jsdom has no viewport, which MUI treats as the tablet band: pin the desktop layout.
  beforeEach(() => setViewportWidth(VIEWPORT.desktop))
  afterEach(restoreViewport)

  it('renders the seeded transfers', async () => {
    renderWithRouter(<TransfersPage />)

    for (const transfer of seedTransfers) {
      expect(await screen.findByText(transfer.description)).toBeInTheDocument()
    }
  })

  it('filters by account, matching either side', async () => {
    const user = userEvent.setup({ delay: null })
    renderWithRouter(<TransfersPage />)
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
        // The filtered list arrives after a refetch: wait for the row to go, don't assert at once.
        await waitFor(() =>
          expect(screen.queryByText(transfer.description)).not.toBeInTheDocument(),
        )
      }
    }
  })

  it('adds a new transfer with the create form', async () => {
    const user = userEvent.setup({ delay: null })
    renderWithRouter(<TransfersPage />)
    await screen.findByText(seedCreditCardPaymentTransfer.description)

    await openAddDialog(user)
    await fillAndSubmitAddForm(user)

    expect(await screen.findByText('Move to savings')).toBeInTheDocument()
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
  })

  it('opens the edit dialog for the transfer named by ?focus=, even though it is not in the default list', async () => {
    server.use(accountsWithInvestmentHandler)
    renderWithRouter(<TransfersPage />, {
      initialEntries: [`/transfers?focus=${seedBitcoinBuyTransfer.id}`],
    })

    const dialog = await screen.findByRole('dialog')
    expect(within(dialog).getByText('Edit transfer')).toBeInTheDocument()
    expect(
      within(dialog).getByDisplayValue(seedBitcoinBuyTransfer.description),
    ).toBeInTheDocument()
  })

  it('shows an error and clears ?focus= for an unknown transfer id', async () => {
    renderWithRouter(<TransfersPage />, { initialEntries: ['/transfers?focus=trf-missing'] })

    expect(await screen.findByText(/could not be found/i)).toBeInTheDocument()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('excludes the selected From account from the To dropdown (no same-account transfer)', async () => {
    const user = userEvent.setup({ delay: null })
    renderWithRouter(<TransfersPage />)
    await screen.findByText(seedCreditCardPaymentTransfer.description)
    await openAddDialog(user)

    const openAccounts = seedAccounts.filter((a) => !a.closed)
    await selectOption(user, 'From Account', openAccounts[0].name)
    await user.click(screen.getByRole('combobox', { name: 'To Account' }))

    expect(screen.queryByRole('option', { name: openAccounts[0].name })).not.toBeInTheDocument()
  })

  it('excludes closed accounts from the create/edit form account dropdowns', async () => {
    const user = userEvent.setup({ delay: null })
    renderWithRouter(<TransfersPage />)
    await screen.findByText(seedCreditCardPaymentTransfer.description)
    await openAddDialog(user)

    const closedAccount = seedAccounts.find((a) => a.closed)!
    await user.click(screen.getByRole('combobox', { name: 'From Account' }))

    expect(screen.queryByRole('option', { name: closedAccount.name })).not.toBeInTheDocument()
  })

  it('edits a transfer', async () => {
    const user = userEvent.setup({ delay: null })
    renderWithRouter(<TransfersPage />)
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
    const user = userEvent.setup({ delay: null })
    renderWithRouter(<TransfersPage />)
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
    const user = userEvent.setup({ delay: null })
    renderWithRouter(<TransfersPage />)
    await screen.findByText(seedCreditCardPaymentTransfer.description)

    await openAddDialog(user)
    await fillAndSubmitAddForm(user)

    // The error shows inside the open dialog, which stays open.
    const dialog = screen.getByRole('dialog')
    expect(await within(dialog).findByText(TRANSFER_CONFLICT_MESSAGE)).toBeInTheDocument()
  })

  // The table's load state is `combineLoadState(accountsState, transfersState)` (F2 audit finding);
  // intercepting either composed source shows the same skeleton/failure - accounts is the simpler
  // body to fake a delayed/failing response for.
  expectLoadStates({
    render: () => renderWithRouter(<TransfersPage />),
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
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
    renderWithRouter(<TransfersPage />)

    await user.click(await screen.findByRole('button', { name: 'Add transfer' }))
    expect(await screen.findByLabelText('Date')).toHaveValue('2026-03-31')
  })
})

describe('TransfersPage trades', () => {
  beforeEach(() => setViewportWidth(VIEWPORT.desktop))
  afterEach(restoreViewport)

  it('labels a tagged transfer Buy or Sell with its product name', async () => {
    server.use(
      accountsWithInvestmentHandler,
      http.get('/api/transfers', () =>
        HttpResponse.json({
          content: [seedBitcoinSellTransfer, seedBitcoinBuyTransfer],
          page: { size: 20, number: 0, totalElements: 2, totalPages: 1 },
        }),
      ),
    )
    renderWithRouter(<TransfersPage />)

    expect(
      await screen.findByText('Buy Bitcoin', { selector: '.MuiChip-label' }),
    ).toBeInTheDocument()
    expect(screen.getByText('Sell Bitcoin', { selector: '.MuiChip-label' })).toBeInTheDocument()
  })
})

describe('TransfersPage responsive layout (F021)', () => {
  afterEach(restoreViewport)

  const target = seedCreditCardPaymentTransfer
  const name = (id: string) => seedAccounts.find((a) => a.id === id)!.name

  async function findCard(description: string) {
    const list = await screen.findByRole('list', { name: 'Transfers' })
    await within(list).findByText(description)
    const items = within(list).getAllByRole('listitem')
    return within(items.find((item) => within(item).queryByText(description))!)
  }

  describe('mobile', () => {
    beforeEach(() => setViewportWidth(VIEWPORT.mobile))

    it('renders cards instead of a table: description, date, From, To and Amount', async () => {
      renderWithRouter(<TransfersPage />)

      const card = await findCard(target.description)
      expect(screen.queryByRole('table')).not.toBeInTheDocument()
      expect(card.getByText(target.date)).toBeInTheDocument()
      expect(card.getByText(name(target.fromAccountId))).toBeInTheDocument()
      expect(card.getByText(name(target.toAccountId))).toBeInTheDocument()
      expect(card.getByText(target.amount.toFixed(2))).toBeInTheDocument()
      expect(card.getByRole('button', { name: 'Edit' })).toBeInTheDocument()
      expect(card.getByRole('button', { name: 'Delete' })).toBeInTheDocument()
    })

    it('shows the Buy/Sell chip on a tagged transfer card', async () => {
      server.use(
        accountsWithInvestmentHandler,
        http.get('/api/transfers', () =>
          HttpResponse.json({
            content: [seedBitcoinBuyTransfer],
            page: { size: 20, number: 0, totalElements: 1, totalPages: 1 },
          }),
        ),
      )
      renderWithRouter(<TransfersPage />)

      const list = await screen.findByRole('list', { name: 'Transfers' })
      expect(
        await within(list).findByText('Buy Bitcoin', { selector: '.MuiChip-label' }),
      ).toBeInTheDocument()
    })

    it('collapses the filters behind a button with an active-count badge', async () => {
      const user = userEvent.setup({ delay: null })
      renderWithRouter(<TransfersPage />)
      await findCard(target.description)
      expect(screen.queryByRole('combobox', { name: 'Account filter' })).not.toBeInTheDocument()

      await user.click(screen.getByRole('button', { name: 'Filters' }))
      await selectOption(user, 'Account filter', name('acct-2'))
      await user.click(screen.getByRole('button', { name: 'Done' }))

      expect(await screen.findByRole('button', { name: 'Filters, 1 active' })).toBeInTheDocument()
      await waitFor(() => expect(screen.queryByText(target.description)).not.toBeInTheDocument())
    })

    it('Add opens a full-screen dialog that creates a transfer', async () => {
      const user = userEvent.setup({ delay: null })
      renderWithRouter(<TransfersPage />)
      await findCard(target.description)

      const dialog = await openAddDialog(user)

      expect(dialog).toHaveClass('MuiDialog-paperFullScreen')
      await fillAndSubmitAddForm(user)
      expect(await screen.findByText('Move to savings')).toBeInTheDocument()
      await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    })

    it('shows a save error inside the dialog, which stays open', async () => {
      server.use(transferClosedAccountConflictHandler)
      const user = userEvent.setup({ delay: null })
      renderWithRouter(<TransfersPage />)
      await findCard(target.description)

      const dialog = await openAddDialog(user)
      await fillAndSubmitAddForm(user)

      expect(await within(dialog).findByText(TRANSFER_CONFLICT_MESSAGE)).toBeInTheDocument()
    })

    it('Edit opens the dialog prefilled and saves through the same mutation', async () => {
      const user = userEvent.setup({ delay: null })
      renderWithRouter(<TransfersPage />)
      const card = await findCard(target.description)

      await user.click(card.getByRole('button', { name: 'Edit' }))

      const dialog = await screen.findByRole('dialog')
      expect(dialog).toHaveClass('MuiDialog-paperFullScreen')
      expect(within(dialog).getByText('Edit transfer')).toBeInTheDocument()
      const description = within(dialog).getByRole('textbox', { name: 'Description' })
      expect(description).toHaveValue(target.description)
      await user.clear(description)
      await user.type(description, 'Updated note')
      await user.click(within(dialog).getByRole('button', { name: 'Save changes' }))

      expect(await screen.findByText('Updated note')).toBeInTheDocument()
      await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    })

    it('Cancel closes the dialog without saving', async () => {
      const user = userEvent.setup({ delay: null })
      renderWithRouter(<TransfersPage />)
      const card = await findCard(target.description)
      await user.click(card.getByRole('button', { name: 'Edit' }))

      const dialog = await screen.findByRole('dialog')
      await user.click(within(dialog).getByRole('button', { name: 'Cancel' }))

      await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
      expect(screen.getByText(target.description)).toBeInTheDocument()
    })

    it('keeps the delete confirmation on the card', async () => {
      const user = userEvent.setup({ delay: null })
      renderWithRouter(<TransfersPage />)
      const card = await findCard(target.description)

      await user.click(card.getByRole('button', { name: 'Delete' }))
      await user.click(await screen.findByRole('button', { name: 'Delete transfer' }))

      await waitFor(() => expect(screen.queryByText(target.description)).not.toBeInTheDocument())
    })

    it('offers the trade-confirmation fields in the dialog once Trade confirmation is picked', async () => {
      server.use(accountsWithInvestmentHandler)
      const user = userEvent.setup({ delay: null })
      renderWithRouter(<TransfersPage />)
      await findCard(target.description)

      const dialog = await openAddDialog(user)
      await user.click(within(dialog).getByRole('button', { name: 'Trade confirmation' }))
      await selectOption(user, 'Cash Account', name('acct-1'), within(dialog))
      await selectOption(user, 'Investment Account', seedInvestmentAccount.name, within(dialog))
      await selectOption(user, 'Product', 'Bitcoin', within(dialog))

      const trade = within(dialog).getByRole('group', { name: 'Trade lines' })
      expect(within(trade).getByRole('spinbutton', { name: 'Quantity' })).toBeInTheDocument()
      expect(within(trade).getByRole('spinbutton', { name: 'Unit price' })).toBeInTheDocument()
      expect(within(dialog).getByRole('spinbutton', { name: 'Taxes' })).toBeInTheDocument()
      expect(
        within(trade).getByRole('spinbutton', { name: 'Resulting balance' }),
      ).toBeInTheDocument()
    })
  })

  describe('tablet', () => {
    beforeEach(() => setViewportWidth(VIEWPORT.tablet))

    it('keeps the full table with inline filters and the Add dialog', async () => {
      renderWithRouter(<TransfersPage />)

      await screen.findByText(target.description)
      expect(screen.getByRole('table')).toBeInTheDocument()
      for (const header of ['Date', 'From', 'To', 'Amount', 'Description']) {
        expect(screen.getByRole('columnheader', { name: header })).toBeInTheDocument()
      }
      expect(screen.getByRole('combobox', { name: 'Account filter' })).toBeInTheDocument()
      expect(screen.queryByRole('button', { name: 'Filters' })).not.toBeInTheDocument()
      // No inline form panel: the form only exists inside the dialog.
      expect(screen.queryByRole('spinbutton', { name: 'Amount' })).not.toBeInTheDocument()
      expect(screen.getByRole('button', { name: 'Add transfer' })).toBeInTheDocument()
    })
  })

  describe('desktop', () => {
    beforeEach(() => setViewportWidth(VIEWPORT.desktop))

    it('Add opens a regular (not full-screen) dialog', async () => {
      const user = userEvent.setup({ delay: null })
      renderWithRouter(<TransfersPage />)
      await screen.findByText(target.description)
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument()

      const dialog = await openAddDialog(user)

      expect(dialog).not.toHaveClass('MuiDialog-paperFullScreen')
    })

    it('Edit opens the dialog prefilled', async () => {
      const user = userEvent.setup({ delay: null })
      renderWithRouter(<TransfersPage />)
      const row = await findRow(target.description)

      await user.click(row.getByRole('button', { name: 'Edit' }))

      const dialog = await screen.findByRole('dialog')
      expect(dialog).not.toHaveClass('MuiDialog-paperFullScreen')
      expect(within(dialog).getByRole('textbox', { name: 'Description' })).toHaveValue(
        target.description,
      )
      expect(within(dialog).getByRole('button', { name: 'Save changes' })).toBeInTheDocument()
    })
  })
})
