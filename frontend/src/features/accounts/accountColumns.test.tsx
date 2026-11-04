import { describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import i18n from '../../i18n/i18n'
import { formatMoney } from '../../i18n/format'
import { seedAccounts } from '../../mocks/handlers/accounts'
import { renderWithQueryClient } from '../../test/renderWithQueryClient'
import {
  buildAccountColumns,
  type AccountColumnsContext,
  type AccountInlineEdit,
} from './accountColumns'
import { AccountRowActions } from './AccountRowActions'

const open = seedAccounts.find((a) => !a.closed)!
const closed = seedAccounts.find((a) => a.closed)!

function context(inlineEdit: AccountInlineEdit | null = null): AccountColumnsContext {
  return {
    t: i18n.getFixedT('en', ['accounts', 'common']),
    locale: 'en',
    institutionName: (id) => `institution ${id}`,
    inlineEdit,
  }
}

function cell(key: string, account = open, inlineEdit: AccountInlineEdit | null = null) {
  const column = buildAccountColumns(context(inlineEdit)).find((c) => c.key === key)!
  return renderWithQueryClient(<MemoryRouter>{column.render(account)}</MemoryRouter>)
}

describe('buildAccountColumns', () => {
  it('declares the columns in order with their card roles, dropping only Type on tablet', () => {
    const columns = buildAccountColumns(context())

    expect(columns.map((c) => [c.key, c.role, c.tabletPriority, c.align])).toEqual([
      ['name', 'primary', undefined, undefined],
      ['institution', 'secondary', undefined, undefined],
      ['type', 'secondary', 'low', undefined],
      ['balance', undefined, undefined, 'right'],
      ['status', undefined, undefined, undefined],
    ])
  })

  it('renders the name as a link to the detail page, plus institution, type and balance', () => {
    cell('name')
    cell('institution')
    cell('type')
    cell('balance')

    expect(screen.getByRole('link', { name: open.name })).toHaveAttribute(
      'href',
      `/accounts/${open.id}`,
    )
    expect(screen.getByText(`institution ${open.institutionId}`)).toBeInTheDocument()
    expect(screen.getByText('Checking')).toBeInTheDocument()
    expect(screen.getByText(formatMoney(open.balance, 'en'))).toBeInTheDocument()
  })

  it('renders an Open chip for an open account and a Closed chip for a closed one', () => {
    cell('status', open)
    cell('status', closed)

    expect(screen.getByText('Open')).toBeInTheDocument()
    expect(screen.getByText('Closed')).toBeInTheDocument()
  })

  it('swaps the name and institution cells for inputs only on the row being edited inline', async () => {
    const user = userEvent.setup({ delay: null })
    const inlineEdit: AccountInlineEdit = {
      rowId: open.id,
      name: 'Draft',
      institutionId: open.institutionId,
      onNameChange: vi.fn(),
      onInstitutionChange: vi.fn(),
      onSave: vi.fn(),
      onCancel: vi.fn(),
    }
    cell('name', open, inlineEdit)
    cell('institution', open, inlineEdit)
    cell('name', closed, inlineEdit)

    expect(screen.getAllByRole('textbox', { name: 'Name' })).toHaveLength(1)
    expect(await screen.findAllByRole('combobox', { name: 'Institution' })).toHaveLength(1)
    expect(screen.getByRole('link', { name: closed.name })).toBeInTheDocument()
    await user.type(screen.getByRole('textbox', { name: 'Name' }), 'x')
    expect(inlineEdit.onNameChange).toHaveBeenCalledWith('Draftx')
    await user.type(screen.getByRole('textbox', { name: 'Name' }), '{Enter}')
    expect(inlineEdit.onSave).toHaveBeenCalledWith(open.id)
    await user.type(screen.getByRole('textbox', { name: 'Name' }), '{Escape}')
    expect(inlineEdit.onCancel).toHaveBeenCalled()
  })
})

describe('AccountRowActions', () => {
  function renderActions(overrides = {}) {
    const props = {
      account: open,
      editingInline: false,
      onStartEdit: vi.fn(),
      onSave: vi.fn(),
      onCancel: vi.fn(),
      onClose: vi.fn(),
      onDelete: vi.fn(),
      ...overrides,
    }
    render(<AccountRowActions {...props} />)
    return props
  }

  it('offers Edit, Close and Delete and forwards the account on each click', async () => {
    const user = userEvent.setup({ delay: null })
    const props = renderActions()

    await user.click(screen.getByRole('button', { name: 'Edit' }))
    await user.click(screen.getByRole('button', { name: 'Close' }))
    await user.click(screen.getByRole('button', { name: 'Delete' }))

    expect(props.onStartEdit).toHaveBeenCalledWith(open)
    expect(props.onClose).toHaveBeenCalledWith(open)
    expect(props.onDelete).toHaveBeenCalledWith(open)
  })

  it('disables Close for a closed account', () => {
    renderActions({ account: closed })

    expect(screen.getByRole('button', { name: 'Close' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Delete' })).toBeEnabled()
  })

  it('shows Save and Cancel only, without Close and Delete, while editing inline', async () => {
    const user = userEvent.setup({ delay: null })
    const props = renderActions({ editingInline: true })

    await user.click(screen.getByRole('button', { name: 'Save' }))
    await user.click(screen.getByRole('button', { name: 'Cancel' }))

    expect(props.onSave).toHaveBeenCalledWith(open.id)
    expect(props.onCancel).toHaveBeenCalled()
    expect(screen.queryByRole('button', { name: 'Close' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Delete' })).not.toBeInTheDocument()
  })
})
