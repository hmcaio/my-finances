import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { server } from '../../mocks/server'
import {
  accountAlreadyClosedConflictHandler,
  accountCreateConflictHandler,
  accountEditConflictHandler,
  accountsWithInvestmentHandler,
  seedAccounts,
  seedCheckingAccount,
  seedInvestmentAccount,
} from '../../mocks/handlers/accounts'
import { BUILT_IN_INSTITUTION_ID, seedInstitutions } from '../../mocks/handlers/institutions'
import {
  CLOSE_CONFLICT_MESSAGE,
  DELETE_CONFLICT_MESSAGE,
  DUPLICATE_NAME_MESSAGE,
} from '../../api/accounts/accounts'
import { findRow, renderWithRouter } from '../../test/testUtils'
import { restoreViewport, setViewportWidth, VIEWPORT } from '../../test/viewport'
import { AccountsPage } from './AccountsPage'

function renderPage() {
  return renderWithRouter(<AccountsPage />)
}

/** The add form only exists inside the header's Add dialog. */
async function openAddDialog(user: ReturnType<typeof userEvent.setup>) {
  await user.click(await screen.findByRole('button', { name: 'Add account' }))
  return screen.findByRole('dialog')
}

function institutionName(id: string) {
  return seedInstitutions.find((i) => i.id === id)!.name
}

/**
 * Records the body of the next `POST /api/accounts`, then falls through to the default (stateful)
 * handler, so the created row is in the store the follow-up refetch reads.
 */
function captureCreateBody() {
  const sent: { body?: Record<string, unknown> } = {}
  server.use(
    http.post('/api/accounts', async ({ request }) => {
      sent.body = (await request.clone().json()) as Record<string, unknown>
    }),
  )
  return sent
}

describe('AccountsPage', () => {
  // jsdom has no viewport, which MUI treats as the tablet band (Type column hidden): these tests
  // assert the full desktop table.
  beforeEach(() => setViewportWidth(VIEWPORT.desktop))
  afterEach(restoreViewport)

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
    await openAddDialog(user)
    await screen.findByDisplayValue('No institution')

    await user.type(screen.getByRole('textbox', { name: 'Name' }), 'New Wallet')
    await user.click(screen.getByRole('button', { name: 'Add' }))

    expect(await screen.findByText('New Wallet')).toBeInTheDocument()
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
  })

  it('shows the institution of each account by name', async () => {
    renderPage()
    const openAccount = seedAccounts.find((a) => !a.closed)!
    await screen.findByText(openAccount.name)

    expect(
      (await findRow(openAccount.name)).getByText(institutionName(openAccount.institutionId)),
    ).toBeInTheDocument()
  })

  it('shows a retryable notice instead of raw ids when the institutions cannot be loaded', async () => {
    server.use(http.get('/api/institutions', () => new HttpResponse(null, { status: 500 })))
    renderPage()

    expect((await screen.findAllByText(/Could not load data/)).length).toBeGreaterThan(0)
    expect(screen.queryByText(seedCheckingAccount.name)).not.toBeInTheDocument()
  })

  it('preselects "No institution" in the add form and sends its id', async () => {
    const user = userEvent.setup()
    const sent = captureCreateBody()
    renderPage()
    await screen.findByText(seedAccounts.find((a) => !a.closed)!.name)
    await openAddDialog(user)

    expect(await screen.findByDisplayValue('No institution')).toBeInTheDocument()
    await user.type(screen.getByRole('textbox', { name: 'Name' }), 'New Wallet')
    await user.click(screen.getByRole('button', { name: 'Add' }))

    await waitFor(() =>
      expect(sent.body).toMatchObject({
        name: 'New Wallet',
        institutionId: BUILT_IN_INSTITUTION_ID,
      }),
    )
    expect(await screen.findByText('New Wallet')).toBeInTheDocument()
    // The dialog closes; reopening it starts from the default for the next account.
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    await openAddDialog(user)
    expect(await screen.findByDisplayValue('No institution')).toBeInTheDocument()
    expect(screen.getByRole('textbox', { name: 'Name' })).toHaveValue('')
  })

  it('adds an account at the institution picked in the add form', async () => {
    const user = userEvent.setup()
    const sent = captureCreateBody()
    renderPage()
    await openAddDialog(user)
    await screen.findByDisplayValue('No institution')

    await user.click(screen.getByRole('combobox', { name: 'Institution' }))
    await user.click(await screen.findByRole('option', { name: 'Nubank' }))
    await user.type(screen.getByRole('textbox', { name: 'Name' }), 'Nu Wallet')
    await user.click(screen.getByRole('button', { name: 'Add' }))

    await waitFor(() => expect(sent.body).toMatchObject({ institutionId: 'inst-2' }))
    expect((await findRow('Nu Wallet')).getByText('Nubank')).toBeInTheDocument()
  })

  it('creates an institution inline from the add form and assigns it to the new account', async () => {
    const user = userEvent.setup()
    const sent = captureCreateBody()
    renderPage()
    await openAddDialog(user)
    await screen.findByDisplayValue('No institution')

    const picker = screen.getByRole('combobox', { name: 'Institution' })
    await user.click(picker)
    await user.clear(picker)
    await user.type(picker, 'Inter')
    await user.click(await screen.findByRole('option', { name: 'Add “Inter”' }))
    await screen.findByDisplayValue('Inter')
    await user.type(screen.getByRole('textbox', { name: 'Name' }), 'Inter Account')
    await user.click(screen.getByRole('button', { name: 'Add' }))

    await waitFor(() => expect(sent.body).toMatchObject({ institutionId: 'inst-new' }))
    // The list column resolves the brand-new institution, not a raw id.
    expect((await findRow('Inter Account')).getByText('Inter')).toBeInTheDocument()
  })

  it('edits an account name and institution inline', async () => {
    const user = userEvent.setup()
    renderPage()
    const openAccount = seedAccounts.find((a) => !a.closed)!
    await screen.findByText(openAccount.name)

    const row = await findRow(openAccount.name)
    await user.click(row.getByRole('button', { name: 'Edit' }))
    const nameInput = row.getByRole('textbox', { name: 'Name' })
    await user.clear(nameInput)
    await user.type(nameInput, 'Renamed Account')
    // The institution picker starts from the account's current institution.
    const picker = row.getByRole('combobox', { name: 'Institution' })
    expect(picker).toHaveValue(institutionName(openAccount.institutionId))
    await user.click(picker)
    await user.click(await screen.findByRole('option', { name: 'Nubank' }))
    await user.click(row.getByRole('button', { name: 'Save' }))

    expect(await screen.findByText('Renamed Account')).toBeInTheDocument()
    expect((await findRow('Renamed Account')).getByText('Nubank')).toBeInTheDocument()
  })

  it('does not offer a type/opening-balance input when editing - only name/institution', async () => {
    const user = userEvent.setup()
    renderPage()
    const openAccount = seedAccounts.find((a) => !a.closed)!
    await screen.findByText(openAccount.name)

    const row = await findRow(openAccount.name)
    await user.click(row.getByRole('button', { name: 'Edit' }))

    // Only the two editable fields render as inputs in the row: the name and the institution picker.
    expect(row.getAllByRole('textbox')).toHaveLength(1)
    expect(row.getAllByRole('combobox')).toHaveLength(1)
    await user.click(row.getByRole('button', { name: 'Cancel' }))
  })

  it('closes an account after confirming the dialog', async () => {
    const user = userEvent.setup()
    renderPage()
    const openAccount = seedAccounts.find((a) => !a.closed)!
    await screen.findByText(openAccount.name)

    const row = await findRow(openAccount.name)
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

    const row = await findRow(openAccount.name)
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

    const row = await findRow(openAccount.name)
    await user.click(row.getByRole('button', { name: 'Close' }))
    await user.click(screen.getByRole('button', { name: 'Close account' }))

    // The backend sends no message text, so the client supplies one covering the 409 cases.
    expect(await screen.findByText(CLOSE_CONFLICT_MESSAGE)).toBeInTheDocument()
    expect(screen.getByText(openAccount.name)).toBeInTheDocument()
  })

  it('deletes an account without history after confirming, warning about net worth', async () => {
    const user = userEvent.setup()
    renderPage()
    const closedAccount = seedAccounts.find((a) => a.closed)!
    await screen.findByText(seedAccounts.find((a) => !a.closed)!.name)
    await user.click(screen.getByRole('switch', { name: 'Show closed accounts' }))

    const row = await findRow(closedAccount.name)
    await user.click(row.getByRole('button', { name: 'Delete' }))

    expect(await screen.findByText(/changes your past net worth/)).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Delete account' }))

    await waitFor(() => expect(screen.queryByText(closedAccount.name)).not.toBeInTheDocument())
  })

  it('cancelling the delete dialog leaves the account untouched', async () => {
    const user = userEvent.setup()
    renderPage()
    const openAccount = seedAccounts.find((a) => !a.closed)!
    await screen.findByText(openAccount.name)

    const row = await findRow(openAccount.name)
    await user.click(row.getByRole('button', { name: 'Delete' }))
    await screen.findByText(/permanently removes/)
    await user.click(screen.getByRole('button', { name: 'Cancel' }))

    await waitFor(() => expect(screen.queryByText(/permanently removes/)).not.toBeInTheDocument())
    expect(screen.getByText(openAccount.name)).toBeInTheDocument()
  })

  it('tells the user to close the account when it has history', async () => {
    const user = userEvent.setup()
    renderPage()
    const openAccount = seedAccounts.find((a) => !a.closed)!
    await screen.findByText(openAccount.name)

    const row = await findRow(openAccount.name)
    await user.click(row.getByRole('button', { name: 'Delete' }))
    await user.click(screen.getByRole('button', { name: 'Delete account' }))

    expect(await screen.findByText(DELETE_CONFLICT_MESSAGE)).toBeInTheDocument()
    expect(screen.getByText(openAccount.name)).toBeInTheDocument()
  })

  it('shows the opening balance fields for every type except Investment', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText(seedAccounts.find((a) => !a.closed)!.name)
    await openAddDialog(user)

    expect(screen.getByLabelText('Opening Balance')).toBeInTheDocument()
    expect(screen.getByLabelText('Opening Balance Date')).toBeInTheDocument()

    await user.click(screen.getByRole('combobox', { name: 'Account type' }))
    await user.click(await screen.findByRole('option', { name: 'Investment' }))

    expect(screen.queryByLabelText('Opening Balance')).not.toBeInTheDocument()
    expect(screen.queryByLabelText('Opening Balance Date')).not.toBeInTheDocument()

    // Switching back brings them back.
    await user.click(screen.getByRole('combobox', { name: 'Account type' }))
    await user.click(await screen.findByRole('option', { name: 'Savings' }))
    expect(screen.getByLabelText('Opening Balance')).toBeInTheDocument()
  })

  it('creates an investment account without opening balance or date', async () => {
    const user = userEvent.setup()
    const sent = captureCreateBody()
    renderPage()
    await screen.findByText(seedAccounts.find((a) => !a.closed)!.name)
    await openAddDialog(user)
    await screen.findByDisplayValue('No institution')

    await user.type(screen.getByRole('textbox', { name: 'Name' }), 'XP Investimentos')
    await user.click(screen.getByRole('combobox', { name: 'Account type' }))
    await user.click(await screen.findByRole('option', { name: 'Investment' }))
    await user.click(screen.getByRole('button', { name: 'Add' }))

    await waitFor(() =>
      expect(sent.body).toEqual({
        name: 'XP Investimentos',
        institutionId: BUILT_IN_INSTITUTION_ID,
        type: 'INVESTMENT',
      }),
    )
  })

  it('still sends the opening balance and date for a non-investment account', async () => {
    const user = userEvent.setup()
    const sent = captureCreateBody()
    renderPage()
    await screen.findByText(seedAccounts.find((a) => !a.closed)!.name)
    await openAddDialog(user)
    await screen.findByDisplayValue('No institution')

    await user.type(screen.getByRole('textbox', { name: 'Name' }), 'Cash')
    await user.click(screen.getByRole('button', { name: 'Add' }))

    await waitFor(() =>
      expect(sent.body).toMatchObject({
        type: 'CHECKING',
        openingBalance: 0,
        openingBalanceDate: expect.stringMatching(/^[0-9]{4}-[0-9]{2}-[0-9]{2}$/) as string,
      }),
    )
  })

  it('lists an investment account with its type and a zero balance', async () => {
    server.use(accountsWithInvestmentHandler)
    renderPage()

    expect(await screen.findByText(seedInvestmentAccount.name)).toBeInTheDocument()
    const row = await findRow(seedInvestmentAccount.name)
    expect(row.getByText('Investment')).toBeInTheDocument()
    expect(row.getByText('0.00')).toBeInTheDocument()
  })
})

describe('AccountsPage local-time defaults', () => {
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

  it('defaults the opening balance date to the local date, not the UTC date', async () => {
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
    renderPage()

    await user.click(await screen.findByRole('button', { name: 'Add account' }))
    expect(await screen.findByLabelText('Opening Balance Date')).toHaveValue('2026-03-31')
  })
})

describe('AccountsPage responsive layout (F021)', () => {
  afterEach(restoreViewport)

  const target = seedCheckingAccount

  async function findCard(name: string) {
    const list = await screen.findByRole('list', { name: 'Accounts' })
    await within(list).findByText(name)
    const items = within(list).getAllByRole('listitem')
    return within(items.find((item) => within(item).queryByText(name))!)
  }

  describe('mobile', () => {
    beforeEach(() => setViewportWidth(VIEWPORT.mobile))

    it('renders cards instead of a table, with institution, type, balance and status', async () => {
      renderPage()

      const card = await findCard(target.name)
      expect(screen.queryByRole('table')).not.toBeInTheDocument()
      expect(card.getByRole('link', { name: target.name })).toHaveAttribute(
        'href',
        `/accounts/${target.id}`,
      )
      expect(card.getByText(institutionName(target.institutionId))).toBeInTheDocument()
      expect(card.getByText('Checking')).toBeInTheDocument()
      expect(card.getByText(target.balance.toFixed(2))).toBeInTheDocument()
      expect(card.getByText('Open')).toBeInTheDocument()
      for (const name of ['Edit', 'Close', 'Delete']) {
        expect(card.getByRole('button', { name })).toBeInTheDocument()
      }
    })

    it('Add opens a full-screen dialog that creates an account', async () => {
      const user = userEvent.setup()
      renderPage()
      await findCard(target.name)

      const dialog = await openAddDialog(user)

      expect(dialog).toHaveClass('MuiDialog-paperFullScreen')
      await screen.findByDisplayValue('No institution')
      await user.type(screen.getByRole('textbox', { name: 'Name' }), 'New Wallet')
      await user.click(screen.getByRole('button', { name: 'Add' }))

      expect(await screen.findByText('New Wallet')).toBeInTheDocument()
      await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    })

    it('shows a create error inside the open dialog', async () => {
      server.use(accountCreateConflictHandler)
      const user = userEvent.setup()
      renderPage()
      await findCard(target.name)

      const dialog = await openAddDialog(user)
      await screen.findByDisplayValue('No institution')
      await user.type(within(dialog).getByRole('textbox', { name: 'Name' }), 'New Wallet')
      await user.click(within(dialog).getByRole('button', { name: 'Add' }))

      expect(await within(dialog).findByText(DUPLICATE_NAME_MESSAGE)).toBeInTheDocument()
      expect(screen.getByRole('dialog')).toBeInTheDocument()
    })

    it('Edit opens a full-screen dialog with the name and institution, and saves them', async () => {
      const user = userEvent.setup()
      renderPage()
      const card = await findCard(target.name)

      await user.click(card.getByRole('button', { name: 'Edit' }))

      const dialog = await screen.findByRole('dialog')
      expect(dialog).toHaveClass('MuiDialog-paperFullScreen')
      expect(within(dialog).getByText('Edit account')).toBeInTheDocument()
      // Same two fields as the inline row, prefilled; the card itself shows no inputs.
      const name = within(dialog).getByRole('textbox', { name: 'Name' })
      expect(name).toHaveValue(target.name)
      expect(within(dialog).getAllByRole('textbox')).toHaveLength(1)
      expect(within(dialog).getByRole('combobox', { name: 'Institution' })).toHaveValue(
        institutionName(target.institutionId),
      )
      await user.clear(name)
      await user.type(name, 'Renamed Account')
      await user.click(within(dialog).getByRole('combobox', { name: 'Institution' }))
      await user.click(await screen.findByRole('option', { name: 'Nubank' }))
      await user.click(within(dialog).getByRole('button', { name: 'Save changes' }))

      expect(await screen.findByText('Renamed Account')).toBeInTheDocument()
      await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
      expect((await findCard('Renamed Account')).getByText('Nubank')).toBeInTheDocument()
    })

    it('does not save an empty name from the edit dialog', async () => {
      const user = userEvent.setup()
      renderPage()
      const card = await findCard(target.name)
      await user.click(card.getByRole('button', { name: 'Edit' }))

      const dialog = await screen.findByRole('dialog')
      await user.clear(within(dialog).getByRole('textbox', { name: 'Name' }))

      expect(within(dialog).getByRole('button', { name: 'Save changes' })).toBeDisabled()
    })

    it('shows an edit error inside the dialog, which stays open', async () => {
      server.use(accountEditConflictHandler)
      const user = userEvent.setup()
      renderPage()
      const card = await findCard(target.name)
      await user.click(card.getByRole('button', { name: 'Edit' }))

      const dialog = await screen.findByRole('dialog')
      await user.type(within(dialog).getByRole('textbox', { name: 'Name' }), ' 2')
      await user.click(within(dialog).getByRole('button', { name: 'Save changes' }))

      expect(await within(dialog).findByText(DUPLICATE_NAME_MESSAGE)).toBeInTheDocument()
    })

    it('Cancel closes the edit dialog without saving', async () => {
      const user = userEvent.setup()
      renderPage()
      const card = await findCard(target.name)
      await user.click(card.getByRole('button', { name: 'Edit' }))

      const dialog = await screen.findByRole('dialog')
      await user.click(within(dialog).getByRole('button', { name: 'Cancel' }))

      await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
      expect(screen.getByText(target.name)).toBeInTheDocument()
    })

    it('keeps the delete confirmation on the card', async () => {
      const user = userEvent.setup()
      renderPage()
      const card = await findCard(target.name)

      await user.click(card.getByRole('button', { name: 'Delete' }))

      expect(await screen.findByText(/permanently removes/)).toBeInTheDocument()
    })
  })

  describe('tablet', () => {
    beforeEach(() => setViewportWidth(VIEWPORT.tablet))

    it('keeps the table without the Type column, reachable through the row expander', async () => {
      const user = userEvent.setup()
      renderPage()

      const row = await findRow(target.name)
      expect(screen.getByRole('table')).toBeInTheDocument()
      expect(screen.getByRole('columnheader', { name: 'Balance' })).toBeInTheDocument()
      expect(screen.queryByRole('columnheader', { name: 'Type' })).not.toBeInTheDocument()

      await user.click(row.getByRole('button', { name: 'Show details' }))
      expect(await screen.findByText('Checking')).toBeInTheDocument()
    })

    it('still renames inline in the row, with no edit dialog', async () => {
      const user = userEvent.setup()
      renderPage()
      const row = await findRow(target.name)

      await user.click(row.getByRole('button', { name: 'Edit' }))

      expect(row.getByRole('textbox', { name: 'Name' })).toHaveValue(target.name)
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    })

    it('Add opens a regular (not full-screen) dialog', async () => {
      const user = userEvent.setup()
      renderPage()
      await findRow(target.name)

      const dialog = await openAddDialog(user)

      expect(dialog).not.toHaveClass('MuiDialog-paperFullScreen')
    })
  })

  describe('desktop', () => {
    beforeEach(() => setViewportWidth(VIEWPORT.desktop))

    it('shows every column with no form panel below the table', async () => {
      renderPage()

      await screen.findByText(target.name)
      for (const name of ['Name', 'Institution', 'Type', 'Balance', 'Status', 'Actions']) {
        expect(screen.getByRole('columnheader', { name })).toBeInTheDocument()
      }
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
      expect(screen.queryByRole('textbox', { name: 'Name' })).not.toBeInTheDocument()
      expect(screen.getByRole('button', { name: 'Add account' })).toBeInTheDocument()
    })
  })
})
