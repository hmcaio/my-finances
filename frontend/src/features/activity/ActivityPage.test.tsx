import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { delay, http, HttpResponse } from 'msw'
import { server } from '../../mocks/server'
import { seedAuditLogEntries } from '../../mocks/handlers/auditLog'
import { renderWithQueryClient } from '../../test/utils/renderWithQueryClient'
import { restoreViewport, setViewportWidth, VIEWPORT } from '../../test/utils/viewport'
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
    const user = userEvent.setup({ delay: null })
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
    const user = userEvent.setup({ delay: null })
    renderWithQueryClient(<ActivityPage />)
    await screen.findByText('March 16, 2026')

    await user.click(screen.getByRole('combobox', { name: 'Action filter' }))
    await user.click(await screen.findByRole('option', { name: 'Create' }))

    expect(await screen.findByText('March 15, 2026')).toBeInTheDocument()
    expect(screen.queryByText('March 16, 2026')).not.toBeInTheDocument()
  })

  it('filters by origin', async () => {
    const user = userEvent.setup({ delay: null })
    renderWithQueryClient(<ActivityPage />)
    await screen.findByText('March 16, 2026')

    await user.click(screen.getByRole('combobox', { name: 'Origin filter' }))
    await user.click(await screen.findByRole('option', { name: 'System' }))

    const row = await screen.findByText('Rent')
    expect(row).toBeInTheDocument()
    expect(screen.queryByText('Weekly groceries')).not.toBeInTheDocument()
  })

  it('clears every filter via "Clear filters"', async () => {
    const user = userEvent.setup({ delay: null })
    renderWithQueryClient(<ActivityPage />)
    await screen.findByText('March 16, 2026')

    await user.click(screen.getByRole('combobox', { name: 'Action filter' }))
    await user.click(await screen.findByRole('option', { name: 'Create' }))
    await waitFor(() => expect(screen.queryByText('March 16, 2026')).not.toBeInTheDocument())

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

describe('ActivityPage reference values', () => {
  it('shows a resolved reference as "label (id)", and falls back to the bare id with no label', async () => {
    server.use(
      http.get('/api/audit-log', () =>
        HttpResponse.json({
          content: [
            {
              id: 'audit-reference',
              occurredAt: '2026-03-16T14:30:00Z',
              entityType: 'ACCOUNT',
              entityId: 'account-9',
              entityLabel: 'Checking',
              action: 'UPDATE',
              origin: 'USER',
              changes: {
                institutionId: {
                  from: { id: 'f892b930-3956-4a1f-8b2f-c1f2acfdf1d4', label: 'Nubank' },
                  to: { id: 'f892b930-3956-4a1f-8b2f-c1f2acfdf1d5', label: null },
                },
              },
              requestId: null,
            },
          ],
          page: { size: 20, number: 0, totalElements: 1, totalPages: 1 },
        }),
      ),
    )
    const user = userEvent.setup({ delay: null })
    renderWithQueryClient(<ActivityPage />)

    await user.click(await screen.findByRole('button', { name: 'Expand details' }))

    expect(
      await screen.findByText('Nubank (f892b930-3956-4a1f-8b2f-c1f2acfdf1d4)'),
    ).toBeInTheDocument()
    expect(screen.getByText('f892b930-3956-4a1f-8b2f-c1f2acfdf1d5')).toBeInTheDocument()
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
    const user = userEvent.setup({ delay: null })
    renderWithQueryClient(<ActivityPage />)

    await user.click(await screen.findByRole('button', { name: 'Expand details' }))

    expect(await screen.findByText('Amount')).toBeInTheDocument()
    expect(screen.getByText('someFutureField')).toBeInTheDocument()
  })
})

describe('ActivityPage opening position entries (F038)', () => {
  const entries = ['CREATE', 'UPDATE', 'DELETE'].map((action, i) => ({
    id: `audit-open-${i}`,
    occurredAt: `2026-03-16T1${i}:00:00Z`,
    entityType: 'INVESTMENT_OPENING_POSITION',
    entityId: 'iopen-1',
    entityLabel: null,
    action,
    origin: 'USER',
    changes:
      action === 'UPDATE'
        ? { quantity: { from: 40, to: 50 } }
        : { asOfDate: { from: null, to: '2026-03-01' }, unitPrice: { from: null, to: 150 } },
    requestId: null,
  }))

  function useEntries() {
    server.use(
      http.get('/api/audit-log', () =>
        HttpResponse.json({
          content: entries,
          page: { size: 20, number: 0, totalElements: 3, totalPages: 1 },
        }),
      ),
    )
  }

  it('lists create, update and delete entries with a readable type and field labels in English', async () => {
    useEntries()
    const user = userEvent.setup({ delay: null })
    renderWithQueryClient(<ActivityPage />)

    expect(await screen.findAllByText('Investment opening position')).toHaveLength(3)
    await user.click(screen.getAllByRole('button', { name: 'Expand details' })[0])
    expect(await screen.findByText('As of')).toBeInTheDocument()
    expect(screen.getByText('Unit price')).toBeInTheDocument()
  })

  it('and in Brazilian Portuguese', async () => {
    useEntries()
    localStorage.setItem('my-finances:locale', 'pt-BR')
    const user = userEvent.setup({ delay: null })
    renderWithQueryClient(<ActivityPage />)

    expect(await screen.findAllByText('Posição inicial de investimento')).toHaveLength(3)
    await user.click(screen.getAllByRole('button', { name: /Expandir detalhes/ })[0])
    expect(await screen.findByText('Preço unitário')).toBeInTheDocument()
  })

  it('offers the type in the entity type filter', async () => {
    const user = userEvent.setup({ delay: null })
    renderWithQueryClient(<ActivityPage />)
    await screen.findByText('March 16, 2026')

    await user.click(screen.getByRole('combobox', { name: 'Entity type filter' }))

    expect(
      await screen.findByRole('option', { name: 'Investment opening position' }),
    ).toBeInTheDocument()
  })
})

/** Serves one page holding exactly `content` for `GET /api/audit-log`. */
function serveEntries(content: object[]) {
  server.use(
    http.get('/api/audit-log', () =>
      HttpResponse.json({
        content,
        page: { size: 20, number: 0, totalElements: content.length, totalPages: 1 },
      }),
    ),
  )
}

const baseEntry = {
  id: 'audit-x',
  occurredAt: '2026-03-16T14:30:00Z',
  entityType: 'ACCOUNT',
  entityId: 'acct-x',
  entityLabel: 'Checking',
  action: 'UPDATE',
  origin: 'USER',
  changes: { active: { from: true, to: false } },
  requestId: null,
}

describe('ActivityPage entry rendering', () => {
  afterEach(restoreViewport)

  describe('desktop', () => {
    beforeEach(() => setViewportWidth(VIEWPORT.desktop))

    it('lists each day as a table of time, action, type, label and origin', async () => {
      renderWithQueryClient(<ActivityPage />)

      const table = await screen.findByRole('table', { name: 'Activity on March 16, 2026' })
      for (const name of ['Time', 'Action', 'Entity type', 'Label', 'Origin']) {
        expect(within(table).getByRole('columnheader', { name })).toBeInTheDocument()
      }
      const row = within(within(table).getByText('Weekly groceries').closest('tr')!)
      // 14:30Z in the pinned America/Sao_Paulo zone.
      expect(row.getByText('11:30 AM')).toBeInTheDocument()
      expect(row.getByText('Update')).toBeInTheDocument()
      expect(row.getByText('Transaction')).toBeInTheDocument()
      expect(row.getByText('User')).toBeInTheDocument()
      expect(screen.getByRole('table', { name: 'Activity on March 15, 2026' })).toBeInTheDocument()
    })

    it('shows a dash for an entry without a label', async () => {
      serveEntries([{ ...baseEntry, entityLabel: null }])
      renderWithQueryClient(<ActivityPage />)

      const row = within((await screen.findByText('Account')).closest('tr')!)
      expect(row.getByText('—')).toBeInTheDocument()
    })

    it('toggles the diff by clicking the row, and the expand button toggles it exactly once', async () => {
      const user = userEvent.setup({ delay: null })
      serveEntries([baseEntry])
      renderWithQueryClient(<ActivityPage />)

      await user.click(await screen.findByText('Checking'))
      expect(await screen.findByRole('table', { name: 'Changes for Account' })).toBeInTheDocument()
      await user.click(screen.getByRole('button', { name: 'Collapse details' }))
      await waitFor(() =>
        expect(
          screen.queryByRole('table', { name: 'Changes for Account' }),
        ).not.toBeInTheDocument(),
      )

      await user.click(screen.getByRole('button', { name: 'Expand details' }))
      expect(screen.getByRole('button', { name: 'Collapse details' })).toHaveAttribute(
        'aria-expanded',
        'true',
      )
      expect(await screen.findByRole('table', { name: 'Changes for Account' })).toBeInTheDocument()
    })
  })

  describe('mobile', () => {
    beforeEach(() => setViewportWidth(VIEWPORT.mobile))

    it('renders cards instead of tables, with label, time, type, action and origin', async () => {
      renderWithQueryClient(<ActivityPage />)

      await screen.findByText('Weekly groceries')
      expect(screen.queryByRole('table')).not.toBeInTheDocument()
      expect(screen.getByText('11:30 AM · Transaction · Update')).toBeInTheDocument()
      expect(screen.getAllByText('System')).toHaveLength(1)
      expect(screen.getAllByRole('button', { name: 'Expand details' })).toHaveLength(4)
    })

    it('falls back to the entity type as the card title and expands to the diff', async () => {
      const user = userEvent.setup({ delay: null })
      serveEntries([{ ...baseEntry, entityLabel: null }])
      renderWithQueryClient(<ActivityPage />)

      expect(await screen.findByText('Account')).toBeInTheDocument()
      await user.click(screen.getByRole('button', { name: 'Expand details' }))

      expect(await screen.findByRole('table', { name: 'Changes for Account' })).toBeInTheDocument()
      expect(screen.getByText('Yes')).toBeInTheDocument()
      expect(screen.getByText('No')).toBeInTheDocument()
      await user.click(screen.getByRole('button', { name: 'Collapse details' }))
      await waitFor(() => expect(screen.queryByText('Yes')).not.toBeInTheDocument())
    })
  })
})

describe('ActivityPage diff values', () => {
  async function expandOnly(changes: object) {
    serveEntries([{ ...baseEntry, changes }])
    const user = userEvent.setup({ delay: null })
    renderWithQueryClient(<ActivityPage />)
    await user.click(await screen.findByRole('button', { name: 'Expand details' }))
  }

  it('formats null as a dash, booleans as Yes/No, integers as-is, decimals to 2 places, objects as JSON', async () => {
    await expandOnly({
      name: { from: null, to: 'Checking' },
      active: { from: true, to: false },
      amount: { from: 3, to: 1234.5 },
      extra: { from: { a: 1 }, to: 'text' },
    })

    expect(await screen.findByText('Yes')).toBeInTheDocument()
    expect(screen.getByText('No')).toBeInTheDocument()
    expect(screen.getByText('—')).toBeInTheDocument()
    expect(screen.getByText('3')).toBeInTheDocument()
    expect(screen.getByText('1,234.50')).toBeInTheDocument()
    expect(screen.getByText('{"a":1}')).toBeInTheDocument()
    expect(screen.getByText('text')).toBeInTheDocument()
  })

  it('says there are no field changes for an entry with an empty diff', async () => {
    await expandOnly({})

    expect(await screen.findByText('No field changes recorded.')).toBeInTheDocument()
  })
})

describe('ActivityPage filters sent to the API', () => {
  function captureQueries() {
    const queries: URLSearchParams[] = []
    server.use(
      http.get('/api/audit-log', ({ request }) => {
        queries.push(new URL(request.url).searchParams)
        return HttpResponse.json({
          content: seedAuditLogEntries,
          page: { size: 20, number: 0, totalElements: seedAuditLogEntries.length, totalPages: 1 },
        })
      }),
    )
    return queries
  }

  it('sends the date range as local start and end of day instants (pinned America/Sao_Paulo)', async () => {
    const queries = captureQueries()
    const user = userEvent.setup({ delay: null })
    renderWithQueryClient(<ActivityPage />)
    await screen.findByText('March 16, 2026')

    await user.type(screen.getByLabelText('From'), '2026-03-16')
    await user.type(screen.getByLabelText('To'), '2026-03-16')

    await waitFor(() => {
      const last = queries.at(-1)!
      expect(last.get('from')).toBe('2026-03-16T03:00:00.000Z')
      expect(last.get('to')).toBe('2026-03-17T02:59:59.999Z')
    })
    expect(screen.getByLabelText('From')).toHaveValue('2026-03-16')
  })

  it('sends the chosen entity type', async () => {
    const queries = captureQueries()
    const user = userEvent.setup({ delay: null })
    renderWithQueryClient(<ActivityPage />)
    await screen.findByText('March 16, 2026')

    await user.click(screen.getByRole('combobox', { name: 'Entity type filter' }))
    await user.click(await screen.findByRole('option', { name: 'Budget' }))

    await waitFor(() => expect(queries.at(-1)!.get('entityType')).toBe('BUDGET'))
  })
})
