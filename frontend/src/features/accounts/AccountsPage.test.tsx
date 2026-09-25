import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { server } from '../../mocks/server'
import {
  accountAlreadyClosedConflictHandler,
  accountsWithInvestmentHandler,
  seedAccounts,
  seedCheckingAccount,
  seedInvestmentAccount,
} from '../../mocks/handlers/accounts'
import { BUILT_IN_INSTITUTION_ID, seedInstitutions } from '../../mocks/handlers/institutions'
import { CLOSE_CONFLICT_MESSAGE } from '../../api/accounts/accounts'
import { findRow, renderWithRouter } from '../../test/testUtils'
import { AccountsPage } from './AccountsPage'

function renderPage() {
  return renderWithRouter(<AccountsPage />)
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
    await screen.findByDisplayValue('No institution')

    await user.type(screen.getByRole('textbox', { name: 'Name' }), 'New Wallet')
    await user.click(screen.getByRole('button', { name: 'Add' }))

    expect(await screen.findByText('New Wallet')).toBeInTheDocument()
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
    // The form goes back to the default for the next account.
    expect(screen.getByDisplayValue('No institution')).toBeInTheDocument()
  })

  it('adds an account at the institution picked in the add form', async () => {
    const user = userEvent.setup()
    const sent = captureCreateBody()
    renderPage()
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

  it('shows the opening balance fields for every type except Investment', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText(seedAccounts.find((a) => !a.closed)!.name)

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
    renderPage()

    expect(await screen.findByLabelText('Opening Balance Date')).toHaveValue('2026-03-31')
  })
})
