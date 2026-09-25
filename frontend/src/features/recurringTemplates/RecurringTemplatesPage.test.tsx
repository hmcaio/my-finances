import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { server } from '../../mocks/server'
import { seedGroceriesCategory } from '../../mocks/handlers/categories'
import {
  accountsWithInvestmentHandler,
  seedAccounts,
  seedCheckingAccount,
  seedInvestmentAccount,
} from '../../mocks/handlers/accounts'
import {
  seedRentPendingOccurrence,
  seedRentRecurringTemplate,
} from '../../mocks/handlers/recurringTemplates'
import { findRow } from '../../test/testUtils'
import { expectLoadStates } from '../../test/loadStates'
import { RecurringTemplatesPage } from './RecurringTemplatesPage'
import { renderWithQueryClient } from '../../test/renderWithQueryClient'

/** Scopes queries to the templates settings table - the pending-occurrences widget below it
 * renders overlapping text (description/category/account), so an unscoped `getByText` would be
 * ambiguous, same reasoning as F006's `BudgetsPage.test.tsx`. */
function templatesTable() {
  return within(screen.getAllByRole('table')[0])
}

function pendingWidgetTable() {
  return within(screen.getAllByRole('table')[1])
}

describe('RecurringTemplatesPage', () => {
  it('renders the seeded template with its current amount and day of month', async () => {
    renderWithQueryClient(<RecurringTemplatesPage />)

    const row = await findRow(seedRentRecurringTemplate.description, templatesTable())
    expect(row.getByText(seedRentRecurringTemplate.currentAmount!.toFixed(2))).toBeInTheDocument()
    expect(row.getByText(String(seedRentRecurringTemplate.currentDayOfMonth))).toBeInTheDocument()
    expect(row.getByText('Active')).toBeInTheDocument()
  })

  it('never offers an investment account in the create form account dropdown', async () => {
    server.use(accountsWithInvestmentHandler)
    const user = userEvent.setup()
    renderWithQueryClient(<RecurringTemplatesPage />)
    await findRow(seedRentRecurringTemplate.description, templatesTable())

    await user.click(screen.getByRole('combobox', { name: 'Account' }))

    const openAccount = seedAccounts.find((a) => !a.closed)!
    expect(await screen.findByRole('option', { name: openAccount.name })).toBeInTheDocument()
    expect(
      screen.queryByRole('option', { name: seedInvestmentAccount.name }),
    ).not.toBeInTheDocument()
  })

  it('adds a new recurring template', async () => {
    const user = userEvent.setup()
    renderWithQueryClient(<RecurringTemplatesPage />)
    await findRow(seedRentRecurringTemplate.description, templatesTable())

    await user.click(screen.getByLabelText('Category'))
    await user.click(await screen.findByRole('option', { name: seedGroceriesCategory.name }))
    await user.click(screen.getByLabelText('Account'))
    await user.click(await screen.findByRole('option', { name: seedCheckingAccount.name }))
    await user.type(screen.getByLabelText('Description'), 'Internet')
    await user.type(screen.getByLabelText('Amount'), '120')
    const dayInput = screen.getByLabelText('Day of month')
    await user.clear(dayInput)
    await user.type(dayInput, '15')
    await user.click(screen.getByRole('button', { name: 'Add' }))

    expect(await templatesTable().findByText('Internet')).toBeInTheDocument()
  })

  it('edits a template amount and day of month inline', async () => {
    const user = userEvent.setup()
    renderWithQueryClient(<RecurringTemplatesPage />)
    const row = await findRow(seedRentRecurringTemplate.description, templatesTable())

    await user.click(row.getByRole('button', { name: 'Edit amount and day' }))
    const amountInput = row.getByLabelText('Amount')
    await user.clear(amountInput)
    await user.type(amountInput, '1750')
    await user.click(row.getByRole('button', { name: 'Save cap' }))

    expect(await templatesTable().findByText('1750.00')).toBeInTheDocument()
  })

  it('refreshes the pending-occurrences widget after creating a new template', async () => {
    // A brand-new template's effectiveFrom defaults to the current month, so catch-up may
    // immediately generate a pending occurrence for it - the widget must refetch to pick that up
    // rather than only showing what was there before the template existed.
    let pendingCallCount = 0
    server.use(
      http.get('/api/recurring-templates/pending', () => {
        pendingCallCount += 1
        return HttpResponse.json([seedRentPendingOccurrence])
      }),
    )
    const user = userEvent.setup()
    renderWithQueryClient(<RecurringTemplatesPage />)
    await findRow(seedRentRecurringTemplate.description, templatesTable())
    await waitFor(() => expect(pendingCallCount).toBe(1))

    await user.click(screen.getByLabelText('Category'))
    await user.click(await screen.findByRole('option', { name: seedGroceriesCategory.name }))
    await user.click(screen.getByLabelText('Account'))
    await user.click(await screen.findByRole('option', { name: seedCheckingAccount.name }))
    await user.type(screen.getByLabelText('Description'), 'Internet')
    await user.type(screen.getByLabelText('Amount'), '120')
    const dayInput = screen.getByLabelText('Day of month')
    await user.clear(dayInput)
    await user.type(dayInput, '15')
    await user.click(screen.getByRole('button', { name: 'Add' }))
    await templatesTable().findByText('Internet')

    await waitFor(() => expect(pendingCallCount).toBe(2))
  })

  it('refreshes the pending-occurrences widget after an inline cap edit', async () => {
    // The widget fetches its own data once on mount and takes no props - this proves the page
    // actually triggers a refetch (via remounting it) after a cap edit, rather than leaving it
    // showing the pre-edit amount until a full page reload.
    let pendingCallCount = 0
    server.use(
      http.get('/api/recurring-templates/pending', () => {
        pendingCallCount += 1
        const amount = pendingCallCount === 1 ? 1500 : 1750
        return HttpResponse.json([{ ...seedRentPendingOccurrence, amount }])
      }),
    )
    const user = userEvent.setup()
    renderWithQueryClient(<RecurringTemplatesPage />)
    const row = await findRow(seedRentRecurringTemplate.description, templatesTable())
    expect(await pendingWidgetTable().findByText('1500.00')).toBeInTheDocument()

    await user.click(row.getByRole('button', { name: 'Edit amount and day' }))
    const amountInput = row.getByLabelText('Amount')
    await user.clear(amountInput)
    await user.type(amountInput, '1750')
    await user.click(row.getByRole('button', { name: 'Save cap' }))

    expect(await pendingWidgetTable().findByText('1750.00')).toBeInTheDocument()
  })

  it('stops an active template', async () => {
    const user = userEvent.setup()
    renderWithQueryClient(<RecurringTemplatesPage />)
    const row = await findRow(seedRentRecurringTemplate.description, templatesTable())

    await user.click(row.getByRole('button', { name: 'Stop' }))

    expect(await row.findByText('Stopped')).toBeInTheDocument()
  })

  // The templates table's load state is `combineLoadState(categoriesState, accountsState,
  // templatesState)` (F2 audit finding). Every one of those sources is also used by the embedded
  // `PendingOccurrencesWidget` (tested on its own above), so delaying/failing `/api/accounts` here
  // affects both tables at once - `expectLoadStates`'s assertions tolerate that (1..N matches)
  // rather than assuming a single "Loading…"/"Could not load data" instance.
  expectLoadStates({
    render: () => renderWithQueryClient(<RecurringTemplatesPage />),
    url: '/api/accounts',
    successBody: seedAccounts,
    loadedText: seedRentRecurringTemplate.description,
  })
})

describe('RecurringTemplatesPage local-time defaults', () => {
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

  it('sends the local month, not the UTC month, as the new template effectiveFrom', async () => {
    let sent: { effectiveFrom?: string } = {}
    server.use(
      http.post('/api/recurring-templates', async ({ request }) => {
        // Record the body, then fall through to the default (stateful) handler.
        sent = (await request.clone().json()) as { effectiveFrom?: string }
      }),
    )
    const user = userEvent.setup()
    renderWithQueryClient(<RecurringTemplatesPage />)
    await findRow(seedRentRecurringTemplate.description, templatesTable())

    await user.click(screen.getByLabelText('Category'))
    await user.click(await screen.findByRole('option', { name: seedGroceriesCategory.name }))
    await user.click(screen.getByLabelText('Account'))
    await user.click(await screen.findByRole('option', { name: seedCheckingAccount.name }))
    await user.type(screen.getByLabelText('Description'), 'Internet')
    await user.type(screen.getByLabelText('Amount'), '120')
    await user.click(screen.getByRole('button', { name: 'Add' }))
    await templatesTable().findByText('Internet')

    expect(sent.effectiveFrom).toBe('2026-03')
  })
})
