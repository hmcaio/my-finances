import { describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import i18n from '../../i18n/i18n'
import { seedGroceriesBudget } from '../../mocks/handlers/budgets'
import { buildBudgetColumns, type BudgetInlineEdit } from './budgetColumns'
import { BudgetRowActions } from './BudgetRowActions'

const budget = seedGroceriesBudget
const noCap = { ...budget, currentCap: null, currentCapEffectiveFrom: null }
const stopped = { ...noCap, stopped: true }

function cell(key: string, row = budget, inlineEdit: BudgetInlineEdit | null = null) {
  const column = buildBudgetColumns({
    t: i18n.getFixedT('en', ['budgets', 'common']),
    locale: 'en',
    categoryName: (id) => `category ${id}`,
    inlineEdit,
  }).find((c) => c.key === key)!
  return render(<>{column.render(row)}</>)
}

describe('buildBudgetColumns', () => {
  it('declares category (card title), cap (right-aligned) and effective-from, none dropped on tablet', () => {
    const columns = buildBudgetColumns({
      t: i18n.getFixedT('en', ['budgets', 'common']),
      locale: 'en',
      categoryName: (id) => id,
      inlineEdit: null,
    })

    expect(columns.map((c) => [c.key, c.role, c.tabletPriority, c.align])).toEqual([
      ['category', 'primary', undefined, undefined],
      ['cap', undefined, undefined, 'right'],
      ['effectiveFrom', undefined, undefined, undefined],
    ])
  })

  it('renders the looked-up category, the formatted cap and the effective month', () => {
    cell('category')
    cell('cap')
    cell('effectiveFrom')

    expect(screen.getByText(`category ${budget.categoryId}`)).toBeInTheDocument()
    expect(screen.getByText(budget.currentCap!.toFixed(2))).toBeInTheDocument()
    expect(screen.getByText(budget.currentCapEffectiveFrom!)).toBeInTheDocument()
  })

  it('renders "No cap yet" and a dash without a cap, and Stopped for a stopped budget', () => {
    cell('cap', noCap)
    cell('effectiveFrom', noCap)
    cell('cap', stopped)

    expect(screen.getByText('No cap yet')).toBeInTheDocument()
    expect(screen.getByText('—')).toBeInTheDocument()
    expect(screen.getByText('Stopped')).toBeInTheDocument()
  })

  it('swaps the cap cell for an input only on the row being edited inline', async () => {
    const user = userEvent.setup({ delay: null })
    const inlineEdit = { rowId: budget.id, cap: '500', onCapChange: vi.fn() }
    cell('cap', budget, inlineEdit)
    cell('cap', { ...budget, id: 'other' }, inlineEdit)

    expect(screen.getAllByLabelText('Monthly cap')).toHaveLength(1)
    await user.type(screen.getByLabelText('Monthly cap'), '1')
    expect(inlineEdit.onCapChange).toHaveBeenCalledWith('5001')
  })
})

describe('BudgetRowActions', () => {
  function renderActions(overrides = {}) {
    const props = {
      budget,
      editingInline: false,
      saving: false,
      onStartEdit: vi.fn(),
      onSave: vi.fn(),
      onCancel: vi.fn(),
      onStop: vi.fn(),
      ...overrides,
    }
    render(<BudgetRowActions {...props} />)
    return props
  }

  it('offers Edit cap and Stop budget for a running budget and forwards the clicks', async () => {
    const user = userEvent.setup({ delay: null })
    const props = renderActions()

    await user.click(screen.getByRole('button', { name: 'Edit cap' }))
    await user.click(screen.getByRole('button', { name: 'Stop budget' }))

    expect(props.onStartEdit).toHaveBeenCalledWith(budget)
    expect(props.onStop).toHaveBeenCalledWith(budget)
  })

  it('offers only Resume budget for a stopped budget', () => {
    renderActions({ budget: stopped })

    expect(screen.getByRole('button', { name: 'Resume budget' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Stop budget' })).not.toBeInTheDocument()
  })

  it('shows Save cap / Cancel and no Stop while editing inline, with Resume with this cap for a stopped one', async () => {
    const user = userEvent.setup({ delay: null })
    const props = renderActions({ editingInline: true })

    await user.click(screen.getByRole('button', { name: 'Save cap' }))
    await user.click(screen.getByRole('button', { name: 'Cancel' }))

    expect(props.onSave).toHaveBeenCalledWith(budget.id)
    expect(props.onCancel).toHaveBeenCalled()
    expect(screen.queryByRole('button', { name: 'Stop budget' })).not.toBeInTheDocument()
  })

  it('labels the save button "Resume with this cap" for a stopped budget', () => {
    renderActions({ budget: stopped, editingInline: true })

    expect(screen.getByRole('button', { name: 'Resume with this cap' })).toBeInTheDocument()
  })
})
