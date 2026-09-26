import { useState } from 'react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { DialogContent, DialogTitle, TextField } from '@mui/material'
import { restoreViewport, setViewportWidth, VIEWPORT } from '../../test/viewport'
import type { LoadState } from '../../hooks/queryState'
import { ResponsiveDialog } from '../feedback/ResponsiveDialog'
import { InlineEditActions } from './InlineEditActions'
import {
  ResponsiveTable,
  type ResponsiveColumn,
  type ResponsiveTableProps,
} from './ResponsiveTable'

interface Row {
  id: string
  name: string
  kind: string
  balance: string
  notes: string
}

const ROWS: Row[] = [
  { id: '1', name: 'Checking', kind: 'Bank', balance: '100.00', notes: 'main' },
  { id: '2', name: 'Savings', kind: 'Bank', balance: '900.00', notes: 'rainy day' },
]

const COLUMNS: ResponsiveColumn<Row>[] = [
  { key: 'name', header: 'Name', render: (r) => r.name, role: 'primary' },
  { key: 'kind', header: 'Kind', render: (r) => r.kind, role: 'secondary' },
  { key: 'balance', header: 'Balance', render: (r) => r.balance, align: 'right' },
  { key: 'notes', header: 'Notes', render: (r) => r.notes, tabletPriority: 'low' },
]

const LOADED: LoadState = { loading: false, loadError: null, reload: () => {} }

function renderTable(props: Partial<ResponsiveTableProps<Row>> = {}, columns = COLUMNS) {
  return render(
    <ResponsiveTable
      columns={columns}
      rows={ROWS}
      getRowKey={(r) => r.id}
      state={LOADED}
      actions={(r) => <button>{`Edit ${r.name}`}</button>}
      {...props}
    />,
  )
}

const DESKTOP_AND_MOBILE = [
  ['desktop', VIEWPORT.desktop],
  ['mobile', VIEWPORT.mobile],
] as const

describe('ResponsiveTable', () => {
  afterEach(restoreViewport)

  it('shows every column, plus actions, on desktop', () => {
    setViewportWidth(VIEWPORT.desktop)
    renderTable()

    expect(screen.getAllByRole('columnheader').map((h) => h.textContent)).toEqual([
      'Name',
      'Kind',
      'Balance',
      'Notes',
      'Actions',
    ])
    expect(screen.getByRole('button', { name: 'Edit Checking' })).toBeInTheDocument()
  })

  it('hides low-priority columns on tablet but keeps the table and actions', () => {
    setViewportWidth(VIEWPORT.tablet)
    renderTable()

    expect(screen.getAllByRole('columnheader').map((h) => h.textContent)).toEqual([
      'Name',
      'Kind',
      'Balance',
      'Actions',
    ])
    expect(screen.queryByText('main')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Edit Savings' })).toBeInTheDocument()
  })

  it('renders cards on mobile: title, muted line, labelled fields, no hideOnCard, actions', () => {
    setViewportWidth(VIEWPORT.mobile)
    renderTable(
      {},
      COLUMNS.map((c) => (c.key === 'notes' ? { ...c, role: 'hideOnCard' as const } : c)),
    )

    expect(screen.queryByRole('table')).not.toBeInTheDocument()
    const cards = screen.getAllByRole('listitem')
    expect(cards).toHaveLength(2)
    const first = within(cards[0])
    expect(first.getByText('Checking')).toBeInTheDocument()
    expect(first.getByText('Bank')).toBeInTheDocument()
    expect(first.getByText('Balance')).toBeInTheDocument()
    expect(first.getByText('100.00')).toBeInTheDocument()
    expect(first.queryByText('main')).not.toBeInTheDocument()
    expect(first.getByRole('button', { name: 'Edit Checking' })).toBeInTheDocument()
  })

  it('honours renderCard on mobile and hands it the rendered actions', () => {
    setViewportWidth(VIEWPORT.mobile)
    renderTable({
      renderCard: (row, actions) => (
        <div>
          custom {row.name} {actions}
        </div>
      ),
    })

    expect(screen.getByText(/custom Checking/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Edit Checking' })).toBeInTheDocument()
  })

  it('ignores renderCard where a table is shown', () => {
    setViewportWidth(VIEWPORT.desktop)
    renderTable({ renderCard: () => <div>custom card</div> })

    expect(screen.queryByText('custom card')).not.toBeInTheDocument()
    expect(screen.getByRole('table')).toBeInTheDocument()
  })

  it.each([1, 2])('keeps a table with %i column(s) on mobile', (count) => {
    setViewportWidth(VIEWPORT.mobile)
    renderTable({}, COLUMNS.slice(0, count))

    expect(screen.getByRole('table')).toBeInTheDocument()
    expect(screen.queryByRole('listitem')).not.toBeInTheDocument()
  })

  it.each(DESKTOP_AND_MOBILE)('shows the empty message on %s', (_name, width) => {
    setViewportWidth(width)
    renderTable({ rows: [], emptyMessage: 'No accounts.' })

    expect(screen.getByText('No accounts.')).toBeInTheDocument()
  })

  it.each(DESKTOP_AND_MOBILE)('shows the delayed skeleton, then the data, on %s', async (_n, w) => {
    setViewportWidth(w)
    const { rerender } = renderTable({
      rows: undefined,
      state: { loading: true, loadError: null, reload: () => {} },
    })

    expect((await screen.findAllByText('Loading…')).length).toBeGreaterThan(0)
    expect(screen.queryByText('Checking')).not.toBeInTheDocument()

    rerender(
      <ResponsiveTable columns={COLUMNS} rows={ROWS} getRowKey={(r) => r.id} state={LOADED} />,
    )
    expect(screen.getByText('Checking')).toBeInTheDocument()
    expect(screen.queryByText('Loading…')).not.toBeInTheDocument()
  })

  it.each(DESKTOP_AND_MOBILE)(
    'shows a failure notice with a working Retry on %s',
    async (_n, w) => {
      setViewportWidth(w)
      const reload = vi.fn()
      const onRetry = vi.fn()
      const user = userEvent.setup()
      renderTable({
        rows: undefined,
        state: { loading: false, loadError: 'boom', reload },
        onRetry,
      })

      expect(screen.getByText(/Could not load data/)).toBeInTheDocument()
      await user.click(screen.getByRole('button', { name: 'Retry' }))
      expect(onRetry).toHaveBeenCalledOnce()
      expect(reload).not.toHaveBeenCalled()
    },
  )

  it('falls back to state.reload when no onRetry is given', async () => {
    setViewportWidth(VIEWPORT.mobile)
    const reload = vi.fn()
    const user = userEvent.setup()
    renderTable({ rows: undefined, state: { loading: false, loadError: 'boom', reload } })

    await user.click(screen.getByRole('button', { name: 'Retry' }))
    expect(reload).toHaveBeenCalledOnce()
  })
})

/**
 * Pattern example (documented in frontend/CLAUDE.md): the row's edit inputs are one component,
 * rendered inline in the cell on tablet/desktop and inside a `ResponsiveDialog` on mobile, where
 * the card's Edit button opens the dialog instead of editing in place.
 */
function NameField({ value, onChange }: { value: string; onChange: (v: string) => void }) {
  return (
    <TextField label="Name" size="small" value={value} onChange={(e) => onChange(e.target.value)} />
  )
}

function EditableTable({ mobile }: { mobile: boolean }) {
  const [editingId, setEditingId] = useState<string | null>(null)
  const [draft, setDraft] = useState('')
  const columns: ResponsiveColumn<Row>[] = [
    {
      key: 'name',
      header: 'Name',
      role: 'primary',
      render: (r) =>
        editingId === r.id && !mobile ? <NameField value={draft} onChange={setDraft} /> : r.name,
    },
    { key: 'kind', header: 'Kind', render: (r) => r.kind },
    { key: 'balance', header: 'Balance', render: (r) => r.balance },
  ]
  return (
    <>
      <ResponsiveTable
        columns={columns}
        rows={ROWS}
        getRowKey={(r) => r.id}
        state={LOADED}
        actions={(r) => (
          <InlineEditActions
            editing={editingId === r.id && !mobile}
            editLabel={`Edit ${r.name}`}
            onEdit={() => {
              setEditingId(r.id)
              setDraft(r.name)
            }}
            onSave={() => setEditingId(null)}
            onCancel={() => setEditingId(null)}
          />
        )}
      />
      {mobile && (
        <ResponsiveDialog open={editingId !== null} onClose={() => setEditingId(null)}>
          <DialogTitle>Edit account</DialogTitle>
          <DialogContent>
            <NameField value={draft} onChange={setDraft} />
          </DialogContent>
        </ResponsiveDialog>
      )}
    </>
  )
}

describe('shared row-edit fields (pattern example)', () => {
  afterEach(restoreViewport)

  it('desktop: the field renders inline in the table row', async () => {
    setViewportWidth(VIEWPORT.desktop)
    const user = userEvent.setup()
    render(<EditableTable mobile={false} />)

    await user.click(screen.getByRole('button', { name: 'Edit Checking' }))
    expect(screen.getByRole('table')).toContainElement(screen.getByLabelText('Name'))
  })

  it('mobile: the same field renders in a full-screen dialog', async () => {
    setViewportWidth(VIEWPORT.mobile)
    const user = userEvent.setup()
    render(<EditableTable mobile />)

    await user.click(screen.getByRole('button', { name: 'Edit Checking' }))
    const dialog = await screen.findByRole('dialog')
    expect(dialog).toHaveClass('MuiDialog-paperFullScreen')
    expect(within(dialog).getByLabelText('Name')).toHaveValue('Checking')
  })
})
