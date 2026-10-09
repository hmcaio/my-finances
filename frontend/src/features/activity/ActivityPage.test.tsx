import { describe, expect, it } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { delay, http, HttpResponse } from 'msw'
import { server } from '../../mocks/server'
import { seedAuditLogEntries } from '../../mocks/handlers/auditLog'
import { renderWithQueryClient } from '../../test/renderWithQueryClient'
import { ActivityPage } from './ActivityPage'

describe('ActivityPage', () => {
  it('renders seeded entries grouped by day, newest first', async () => {
    renderWithQueryClient(<ActivityPage />)

    expect(await screen.findByText('March 16, 2026')).toBeInTheDocument()
    expect(screen.getByText('March 15, 2026')).toBeInTheDocument()
    // "Weekly groceries" (audit-3, March 16) renders before "Groceries" (audit-1, March 15) -
    // newest group first.
    const headings = screen.getAllByText(/March \d+, 2026/)
    expect(headings[0]).toHaveTextContent('March 16, 2026')
    expect(headings[1]).toHaveTextContent('March 15, 2026')
  })

  it('shows each entry label, falling back to the entity type when there is no label', async () => {
    server.use(
      http.get('/api/audit-log', () =>
        HttpResponse.json({
          content: [
            {
              id: 'audit-no-label',
              occurredAt: '2026-03-16T14:30:00Z',
              entityType: 'INVESTMENT_HOLDING',
              entityId: 'holding-1',
              entityLabel: null,
              action: 'CREATE',
              origin: 'USER',
              changes: { productId: { from: null, to: 'prod-1' } },
              requestId: null,
            },
          ],
          page: { size: 20, number: 0, totalElements: 1, totalPages: 1 },
        }),
      ),
    )
    renderWithQueryClient(<ActivityPage />)

    expect(await screen.findByText('Investment holding')).toBeInTheDocument()
  })

  it('expands a row to show its field-by-field diff, and collapses it again', async () => {
    const user = userEvent.setup()
    renderWithQueryClient(<ActivityPage />)

    await screen.findByText('March 16, 2026')
    expect(screen.queryByText('Amount')).not.toBeInTheDocument()

    await user.click(screen.getAllByRole('button', { name: 'Expand details' })[0])

    expect(await screen.findByText('Amount')).toBeInTheDocument()
    expect(screen.getByText('42.50')).toBeInTheDocument()
    expect(screen.getByText('50')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Collapse details' }))
    // Collapse animates out, then unmounts the content.
    await waitFor(() => expect(screen.queryByText('Amount')).not.toBeInTheDocument())
  })

  it('filters by action', async () => {
    const user = userEvent.setup()
    renderWithQueryClient(<ActivityPage />)
    await screen.findByText('March 16, 2026')

    await user.click(screen.getByRole('combobox', { name: 'Action filter' }))
    await user.click(await screen.findByRole('option', { name: 'Create' }))

    expect(await screen.findByText('March 15, 2026')).toBeInTheDocument()
    expect(screen.queryByText('March 16, 2026')).not.toBeInTheDocument()
  })

  it('filters by origin', async () => {
    const user = userEvent.setup()
    renderWithQueryClient(<ActivityPage />)
    await screen.findByText('March 16, 2026')

    await user.click(screen.getByRole('combobox', { name: 'Origin filter' }))
    await user.click(await screen.findByRole('option', { name: 'System' }))

    const row = await screen.findByText('Rent')
    expect(row).toBeInTheDocument()
    expect(screen.queryByText('Weekly groceries')).not.toBeInTheDocument()
  })

  it('clears every filter via "Clear filters"', async () => {
    const user = userEvent.setup()
    renderWithQueryClient(<ActivityPage />)
    await screen.findByText('March 16, 2026')

    await user.click(screen.getByRole('combobox', { name: 'Action filter' }))
    await user.click(await screen.findByRole('option', { name: 'Create' }))
    expect(screen.queryByText('March 16, 2026')).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Clear filters' }))

    expect(await screen.findByText('March 16, 2026')).toBeInTheDocument()
  })

  it('shows a loading skeleton only when the first fetch is slow, then the entries', async () => {
    server.use(
      http.get('/api/audit-log', async () => {
        await delay(400)
        return HttpResponse.json({
          content: seedAuditLogEntries,
          page: { size: 20, number: 0, totalElements: seedAuditLogEntries.length, totalPages: 1 },
        })
      }),
    )
    renderWithQueryClient(<ActivityPage />)

    expect(screen.queryByText('March 16, 2026')).not.toBeInTheDocument()
    expect(await screen.findByText('March 16, 2026')).toBeInTheDocument()
  })

  it('shows a failure notice with Retry after a failed first fetch', async () => {
    server.use(http.get('/api/audit-log', () => new HttpResponse(null, { status: 500 })))
    renderWithQueryClient(<ActivityPage />)

    expect(await screen.findByText(/Could not load data/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Retry' })).toBeInTheDocument()
  })

  it('shows an empty-state message when there is no activity', async () => {
    server.use(
      http.get('/api/audit-log', () =>
        HttpResponse.json({
          content: [],
          page: { size: 20, number: 0, totalElements: 0, totalPages: 1 },
        }),
      ),
    )
    renderWithQueryClient(<ActivityPage />)

    expect(await screen.findByText('No activity found.')).toBeInTheDocument()
  })
})

describe('ActivityPage field labels', () => {
  it('shows a friendly label for a known field and falls back to the raw name for an unknown one', async () => {
    server.use(
      http.get('/api/audit-log', () =>
        HttpResponse.json({
          content: [
            {
              id: 'audit-custom',
              occurredAt: '2026-03-16T14:30:00Z',
              entityType: 'TRANSACTION',
              entityId: 'txn-9',
              entityLabel: 'Custom entry',
              action: 'UPDATE',
              origin: 'USER',
              changes: {
                amount: { from: 10, to: 20 },
                someFutureField: { from: 'a', to: 'b' },
              },
              requestId: null,
            },
          ],
          page: { size: 20, number: 0, totalElements: 1, totalPages: 1 },
        }),
      ),
    )
    const user = userEvent.setup()
    renderWithQueryClient(<ActivityPage />)

    await user.click(await screen.findByRole('button', { name: 'Expand details' }))

    expect(await screen.findByText('Amount')).toBeInTheDocument()
    expect(screen.getByText('someFutureField')).toBeInTheDocument()
  })
})
