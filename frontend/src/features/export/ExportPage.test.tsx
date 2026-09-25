import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { server } from '../../mocks/server'
import { seedAccounts } from '../../mocks/handlers/accounts'
import { seedCategories } from '../../mocks/handlers/categories'
import { exportBadRangeHandler } from '../../mocks/handlers/export'
import { REVERSED_RANGE_MESSAGE } from '../../api/export/export'
import { selectOption } from '../../test/testUtils'
import { ExportPage } from './ExportPage'
import { renderWithQueryClient } from '../../test/renderWithQueryClient'

describe('ExportPage', () => {
  let createObjectURL: ReturnType<typeof vi.fn>

  beforeEach(() => {
    createObjectURL = vi.fn(() => 'blob:export')
    Object.assign(URL, { createObjectURL, revokeObjectURL: vi.fn() })
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('downloads the full export when no filter is set', async () => {
    let query = 'unset'
    server.use(
      http.get('/api/export', ({ request }) => {
        query = new URL(request.url).search
        return HttpResponse.arrayBuffer(new ArrayBuffer(4), {
          headers: { 'Content-Type': 'application/zip' },
        })
      }),
    )
    const user = userEvent.setup()
    renderWithQueryClient(<ExportPage />)

    await user.click(screen.getByRole('button', { name: 'Download' }))

    expect(await screen.findByText('Export downloaded.')).toBeInTheDocument()
    expect(query).toBe('')
    expect(createObjectURL).toHaveBeenCalledOnce()
  })

  it('sends the chosen date range, account and category', async () => {
    let sent: Record<string, string> = {}
    server.use(
      http.get('/api/export', ({ request }) => {
        sent = Object.fromEntries(new URL(request.url).searchParams)
        return HttpResponse.arrayBuffer(new ArrayBuffer(4), {
          headers: { 'Content-Type': 'application/zip' },
        })
      }),
    )
    const user = userEvent.setup()
    renderWithQueryClient(<ExportPage />)
    const account = seedAccounts[0]
    const category = seedCategories[0]
    await screen.findByRole('combobox', { name: 'Account filter' })

    await user.type(screen.getByLabelText('From'), '2026-01-01')
    await user.type(screen.getByLabelText('To'), '2026-01-31')
    await selectOption(user, 'Account filter', account.name)
    await selectOption(user, 'Category filter', category.name)
    await user.click(screen.getByRole('button', { name: 'Download' }))

    await screen.findByText('Export downloaded.')
    expect(sent).toEqual({
      dateFrom: '2026-01-01',
      dateTo: '2026-01-31',
      accountId: account.id,
      categoryId: category.id,
    })
  })

  it('rejects a reversed date range without calling the backend', async () => {
    const user = userEvent.setup()
    renderWithQueryClient(<ExportPage />)

    await user.type(screen.getByLabelText('From'), '2026-03-01')
    await user.type(screen.getByLabelText('To'), '2026-01-01')
    await user.click(screen.getByRole('button', { name: 'Download' }))

    expect(await screen.findByText(REVERSED_RANGE_MESSAGE)).toBeInTheDocument()
    expect(createObjectURL).not.toHaveBeenCalled()
  })

  it('shows the error when the backend refuses', async () => {
    server.use(exportBadRangeHandler)
    const user = userEvent.setup()
    renderWithQueryClient(<ExportPage />)

    await user.click(screen.getByRole('button', { name: 'Download' }))

    expect(await screen.findByText(REVERSED_RANGE_MESSAGE)).toBeInTheDocument()
    expect(screen.queryByText('Export downloaded.')).not.toBeInTheDocument()
    await waitFor(() => expect(screen.getByRole('button', { name: 'Download' })).toBeEnabled())
  })

  it('clears the filters', async () => {
    const user = userEvent.setup()
    renderWithQueryClient(<ExportPage />)
    await user.type(screen.getByLabelText('From'), '2026-01-01')

    await user.click(screen.getByRole('button', { name: 'Clear filters' }))

    expect(screen.getByLabelText('From')).toHaveValue('')
  })
})
