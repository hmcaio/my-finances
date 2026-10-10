import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { server } from '../../mocks/server'
import { seedAccounts } from '../../mocks/handlers/accounts'
import { seedCategories } from '../../mocks/handlers/categories'
import { seedVehicles } from '../../mocks/handlers/vehicles'
import { reportsBadRequestHandler } from '../../mocks/handlers/reports'
import { REVERSED_RANGE_MESSAGE } from '../../api/reports/reports'
import { selectOption } from '../../test/testUtils'
import { restoreViewport, setViewportWidth, VIEWPORT } from '../../test/viewport'
import { ReportsPage } from './ReportsPage'
import { renderWithQueryClient } from '../../test/renderWithQueryClient'

describe('ReportsPage', () => {
  beforeEach(() => {
    Object.assign(URL, { createObjectURL: vi.fn(() => 'blob:reports'), revokeObjectURL: vi.fn() })
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('shows no filter bar until a type is checked', () => {
    renderWithQueryClient(<ReportsPage />)

    expect(screen.queryByLabelText('From')).not.toBeInTheDocument()
  })

  it('shows only the filter dimensions the checked type(s) use', async () => {
    const user = userEvent.setup({ delay: null })
    renderWithQueryClient(<ReportsPage />)

    // Transactions & Transfers: dateRange + account + category, no month/asOf/vehicle.
    await user.click(screen.getByRole('checkbox', { name: 'Transactions & Transfers' }))

    expect(await screen.findByLabelText('From')).toBeInTheDocument()
    expect(screen.getByLabelText('To')).toBeInTheDocument()
    expect(screen.getByRole('combobox', { name: 'Account filter' })).toBeInTheDocument()
    expect(screen.getByRole('combobox', { name: 'Category filter' })).toBeInTheDocument()
    expect(screen.queryByLabelText('Month')).not.toBeInTheDocument()
    expect(screen.queryByRole('combobox', { name: 'Vehicle filter' })).not.toBeInTheDocument()
  })

  it('merges filter dimensions across multiple checked types', async () => {
    const user = userEvent.setup({ delay: null })
    renderWithQueryClient(<ReportsPage />)

    await user.click(screen.getByRole('checkbox', { name: 'Transactions & Transfers' }))
    await user.click(screen.getByRole('checkbox', { name: 'Fuel history & stats' }))

    expect(await screen.findByRole('combobox', { name: 'Vehicle filter' })).toBeInTheDocument()
    expect(screen.getByRole('combobox', { name: 'Account filter' })).toBeInTheDocument()
  })

  it('generates a report with the selected types and filters', async () => {
    let body: unknown
    server.use(
      http.post('/api/reports', async ({ request }) => {
        body = await request.clone().json()
        return HttpResponse.arrayBuffer(new ArrayBuffer(4), {
          headers: { 'Content-Type': 'text/csv' },
        })
      }),
    )
    const user = userEvent.setup({ delay: null })
    renderWithQueryClient(<ReportsPage />)
    const account = seedAccounts[0]
    const category = seedCategories[0]

    await user.click(screen.getByRole('checkbox', { name: 'Transactions & Transfers' }))
    await screen.findByRole('combobox', { name: 'Account filter' })
    await user.type(screen.getByLabelText('From'), '2026-01-01')
    await user.type(screen.getByLabelText('To'), '2026-01-31')
    await selectOption(user, 'Account filter', account.name)
    await selectOption(user, 'Category filter', category.name)
    await user.click(screen.getByRole('button', { name: 'Generate' }))

    await screen.findByText('Report downloaded.')
    expect(body).toEqual({
      types: ['TRANSACTIONS_TRANSFERS'],
      filter: {
        dateFrom: '2026-01-01',
        dateTo: '2026-01-31',
        accountId: account.id,
        categoryId: category.id,
      },
    })
  })

  it('shows the vehicle select once the fuel report is checked', async () => {
    const user = userEvent.setup({ delay: null })
    renderWithQueryClient(<ReportsPage />)
    const vehicle = seedVehicles[0]

    await user.click(screen.getByRole('checkbox', { name: 'Fuel history & stats' }))
    await screen.findByRole('combobox', { name: 'Vehicle filter' })

    await selectOption(user, 'Vehicle filter', vehicle.name)
    expect(screen.getByRole('combobox', { name: 'Vehicle filter' })).toHaveTextContent(vehicle.name)
  })

  it('rejects a reversed date range without calling the backend', async () => {
    const user = userEvent.setup({ delay: null })
    renderWithQueryClient(<ReportsPage />)

    await user.click(screen.getByRole('checkbox', { name: 'Transactions & Transfers' }))
    await user.type(await screen.findByLabelText('From'), '2026-03-01')
    await user.type(screen.getByLabelText('To'), '2026-01-01')
    await user.click(screen.getByRole('button', { name: 'Generate' }))

    expect(await screen.findByText(REVERSED_RANGE_MESSAGE)).toBeInTheDocument()
  })

  it('disables Generate until at least one type is checked', () => {
    renderWithQueryClient(<ReportsPage />)

    expect(screen.getByRole('button', { name: 'Generate' })).toBeDisabled()
  })

  it('shows the error when the backend refuses', async () => {
    server.use(reportsBadRequestHandler)
    const user = userEvent.setup({ delay: null })
    renderWithQueryClient(<ReportsPage />)

    await user.click(screen.getByRole('checkbox', { name: 'Annual summary' }))
    await user.click(screen.getByRole('button', { name: 'Generate' }))

    await waitFor(() => expect(screen.getByRole('button', { name: 'Generate' })).toBeEnabled())
    expect(screen.queryByText('Report downloaded.')).not.toBeInTheDocument()
  })
})

describe('ReportsPage responsive layout (F021)', () => {
  afterEach(restoreViewport)

  it('collapses the filters behind a button with an active-count badge on mobile', async () => {
    setViewportWidth(VIEWPORT.mobile)
    const user = userEvent.setup({ delay: null })
    renderWithQueryClient(<ReportsPage />)
    await user.click(screen.getByRole('checkbox', { name: 'Transactions & Transfers' }))
    expect(screen.queryByLabelText('From')).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Filters' }))
    await user.type(screen.getByLabelText('From'), '2026-01-01')
    await user.click(screen.getByRole('button', { name: 'Done' }))

    expect(await screen.findByRole('button', { name: 'Filters, 1 active' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Generate' })).toBeInTheDocument()
  })
})
