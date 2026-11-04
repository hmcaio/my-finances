import { describe, expect, it, vi } from 'vitest'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import i18n from '../../i18n/i18n'
import { formatMoney } from '../../i18n/format'
import { seedRentRecurringTemplate } from '../../mocks/handlers/recurringTemplates'
import {
  buildRecurringTemplateColumns,
  renderRecurringTemplateCard,
  type RecurringTemplateDisplayContext,
  type RecurringTemplateInlineEdit,
} from './recurringTemplateColumns'
import { RecurringTemplateRowActions } from './RecurringTemplateRowActions'

const display: RecurringTemplateDisplayContext = {
  t: i18n.getFixedT('en', ['recurringTemplates', 'common']),
  locale: 'en',
  categoryName: (id) => `category ${id}`,
  accountName: (id) => `account ${id}`,
}

const noCap = { ...seedRentRecurringTemplate, currentAmount: null, currentDayOfMonth: null }

function cell(
  key: string,
  template = seedRentRecurringTemplate,
  inlineEdit: RecurringTemplateInlineEdit | null = null,
) {
  const column = buildRecurringTemplateColumns({ ...display, inlineEdit }).find(
    (c) => c.key === key,
  )!
  return render(<>{column.render(template)}</>)
}

describe('buildRecurringTemplateColumns', () => {
  it('declares the columns in order, with only Category dropped on tablet and the numbers right-aligned', () => {
    const columns = buildRecurringTemplateColumns({ ...display, inlineEdit: null })

    expect(columns.map((c) => c.key)).toEqual([
      'description',
      'category',
      'account',
      'amount',
      'dayOfMonth',
      'status',
    ])
    expect(columns.filter((c) => c.tabletPriority === 'low').map((c) => c.key)).toEqual([
      'category',
    ])
    expect(columns.filter((c) => c.align === 'right').map((c) => c.key)).toEqual([
      'amount',
      'dayOfMonth',
    ])
  })

  it('renders the looked-up names, the formatted amount, the day and the status chip', () => {
    cell('category')
    cell('account')
    cell('amount')
    cell('dayOfMonth')
    cell('status')

    expect(screen.getByText('category cat-1')).toBeInTheDocument()
    expect(screen.getByText('account acct-1')).toBeInTheDocument()
    expect(
      screen.getByText(formatMoney(seedRentRecurringTemplate.currentAmount!, 'en')),
    ).toBeInTheDocument()
    expect(
      screen.getByText(String(seedRentRecurringTemplate.currentDayOfMonth)),
    ).toBeInTheDocument()
    expect(screen.getByText('Active')).toBeInTheDocument()
  })

  it('renders "No cap yet" and a dash when the template has no cap, and Stopped when inactive', () => {
    cell('amount', noCap)
    cell('dayOfMonth', noCap)
    cell('status', { ...noCap, active: false })

    expect(screen.getByText('No cap yet')).toBeInTheDocument()
    expect(screen.getByText('—')).toBeInTheDocument()
    expect(screen.getByText('Stopped')).toBeInTheDocument()
  })

  it('swaps the amount and day cells for inputs only on the row being edited inline', async () => {
    const user = userEvent.setup({ delay: null })
    const onAmountChange = vi.fn()
    const onDayOfMonthChange = vi.fn()
    const inlineEdit = {
      rowId: seedRentRecurringTemplate.id,
      amount: '1500',
      dayOfMonth: '5',
      onAmountChange,
      onDayOfMonthChange,
    }
    cell('amount', seedRentRecurringTemplate, inlineEdit)
    cell('dayOfMonth', seedRentRecurringTemplate, inlineEdit)
    cell('amount', { ...seedRentRecurringTemplate, id: 'other' }, inlineEdit)

    expect(screen.getAllByLabelText('Amount')).toHaveLength(1)
    await user.type(screen.getByLabelText('Amount'), '0')
    await user.type(screen.getByLabelText('Day of month'), '1')
    expect(onAmountChange).toHaveBeenCalledWith('15000')
    expect(onDayOfMonthChange).toHaveBeenCalledWith('51')
    expect(
      screen.getByText(formatMoney(seedRentRecurringTemplate.currentAmount!, 'en')),
    ).toBeInTheDocument()
  })
})

describe('renderRecurringTemplateCard', () => {
  it('leads with description and status, then names, then amount and day, then the actions', () => {
    render(
      <>{renderRecurringTemplateCard(display, seedRentRecurringTemplate, <button>act</button>)}</>,
    )

    expect(screen.getByText(seedRentRecurringTemplate.description)).toBeInTheDocument()
    expect(screen.getByText('Active')).toBeInTheDocument()
    expect(screen.getByText('category cat-1 · account acct-1')).toBeInTheDocument()
    expect(
      screen.getByText(
        new RegExp(
          `${formatMoney(seedRentRecurringTemplate.currentAmount!, 'en').replace('.', '\\.')}.*Day.*5`,
        ),
      ),
    ).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'act' })).toBeInTheDocument()
  })

  it('shows "No cap yet" with a dash day and omits the footer without actions', () => {
    const { container } = render(<>{renderRecurringTemplateCard(display, noCap, null)}</>)

    expect(screen.getByText(/No cap yet.*Day.*—/)).toBeInTheDocument()
    expect(container.querySelectorAll('button')).toHaveLength(0)
  })
})

describe('RecurringTemplateRowActions', () => {
  function renderActions(overrides = {}) {
    const props = {
      template: seedRentRecurringTemplate,
      editingInline: false,
      saving: false,
      toggling: false,
      onStartEdit: vi.fn(),
      onSave: vi.fn(),
      onCancel: vi.fn(),
      onToggle: vi.fn(),
      ...overrides,
    }
    render(<RecurringTemplateRowActions {...props} />)
    return props
  }

  it('offers Edit and Stop for an active template and forwards the clicks', async () => {
    const user = userEvent.setup({ delay: null })
    const props = renderActions()

    await user.click(screen.getByRole('button', { name: 'Edit amount and day' }))
    await user.click(screen.getByRole('button', { name: 'Stop' }))

    expect(props.onStartEdit).toHaveBeenCalledWith(seedRentRecurringTemplate)
    expect(props.onToggle).toHaveBeenCalledWith(seedRentRecurringTemplate)
  })

  it('offers Reactivate for a stopped template and disables the toggle while it runs', () => {
    renderActions({ template: { ...seedRentRecurringTemplate, active: false }, toggling: true })

    expect(screen.getByRole('button', { name: 'Reactivate' })).toBeDisabled()
  })

  it('shows Save cap / Cancel and no toggle while editing inline', async () => {
    const user = userEvent.setup({ delay: null })
    const props = renderActions({ editingInline: true })
    const actions = within(document.body)

    await user.click(actions.getByRole('button', { name: 'Save cap' }))
    await user.click(actions.getByRole('button', { name: 'Cancel' }))

    expect(props.onSave).toHaveBeenCalledWith(seedRentRecurringTemplate.id)
    expect(props.onCancel).toHaveBeenCalled()
    expect(actions.queryByRole('button', { name: 'Stop' })).not.toBeInTheDocument()
  })
})
