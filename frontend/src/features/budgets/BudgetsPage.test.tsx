import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { delay, http, HttpResponse } from 'msw'
import { server } from '../../mocks/server'
import {
  seedCategories,
  seedGroceriesCategory,
  seedSalaryCategory,
} from '../../mocks/handlers/categories'
import {
  budgetCreateConflictHandler,
  seedBudgets,
  seedGroceriesBudget,
  seedGroceriesBudgetReportLine,
} from '../../mocks/handlers/budgets'
import { CREATE_CONFLICT_MESSAGE } from '../../api/budgets'
import { findRow } from '../../test/testUtils'
import { expectLoadStates } from '../../test/loadStates'
import { BudgetsPage } from './BudgetsPage'
import { renderWithQueryClient } from '../../test/renderWithQueryClient'

/** Scopes queries to the budget settings table - the category name also appears in the
 * budget-vs-actual section below it, so an unscoped `getByText` would be ambiguous. */
function settingsTable() {
  return within(screen.getByRole('table'))
}

describe('BudgetsPage', () => {
  it('renders the seeded budget with its current cap', async () => {
    renderWithQueryClient(<BudgetsPage />)

    const row = await findRow(seedGroceriesCategory.name, settingsTable())
    expect(row.getByText(seedGroceriesBudget.currentCap!.toFixed(2))).toBeInTheDocument()
  })

  it('renders the budget-vs-actual report for the current month, flagging an over-cap category', async () => {
    renderWithQueryClient(<BudgetsPage />)

    const line = seedGroceriesBudgetReportLine
    expect(
      await screen.findByText(new RegExp(`${line.actual.toFixed(2)} / ${line.cap!.toFixed(2)}`)),
    ).toBeInTheDocument()
    expect(await screen.findByText(/over budget/)).toBeInTheDocument()
  })

  it('offers only unbudgeted expense categories in the add-budget picker', async () => {
    const user = userEvent.setup()
    renderWithQueryClient(<BudgetsPage />)
    await findRow(seedGroceriesCategory.name, settingsTable())

    // Groceries is already budgeted and Salary is income - neither belongs here.
    await user.click(screen.getByLabelText('Category'))
    expect(
      screen.queryByRole('option', { name: seedGroceriesCategory.name }),
    ).not.toBeInTheDocument()
    expect(screen.queryByRole('option', { name: seedSalaryCategory.name })).not.toBeInTheDocument()
  })

  it('adds a new budget for an unbudgeted expense category', async () => {
    server.use(
      http.get('/api/categories', () =>
        HttpResponse.json([
          ...seedCategories,
          { id: 'cat-5', name: 'Dining', type: 'EXPENSE', builtIn: false },
        ]),
      ),
    )
    const user = userEvent.setup()
    renderWithQueryClient(<BudgetsPage />)
    await findRow(seedGroceriesCategory.name, settingsTable())

    await user.click(screen.getByLabelText('Category'))
    await user.click(await screen.findByRole('option', { name: 'Dining' }))
    await user.type(screen.getByLabelText('Monthly cap'), '150')
    await user.click(screen.getByRole('button', { name: 'Add' }))

    expect(await settingsTable().findByText('Dining')).toBeInTheDocument()
  })

  it('surfaces the 409 conflict message when create fails', async () => {
    server.use(
      http.get('/api/categories', () =>
        HttpResponse.json([
          ...seedCategories,
          { id: 'cat-5', name: 'Dining', type: 'EXPENSE', builtIn: false },
        ]),
      ),
      budgetCreateConflictHandler,
    )
    const user = userEvent.setup()
    renderWithQueryClient(<BudgetsPage />)
    await findRow(seedGroceriesCategory.name, settingsTable())

    await user.click(screen.getByLabelText('Category'))
    await user.click(await screen.findByRole('option', { name: 'Dining' }))
    await user.type(screen.getByLabelText('Monthly cap'), '150')
    await user.click(screen.getByRole('button', { name: 'Add' }))

    expect(await screen.findByText(CREATE_CONFLICT_MESSAGE)).toBeInTheDocument()
  })

  it('edits a budget cap inline', async () => {
    const user = userEvent.setup()
    renderWithQueryClient(<BudgetsPage />)
    const row = await findRow(seedGroceriesCategory.name, settingsTable())
    await waitFor(() =>
      expect(row.getByText(seedGroceriesBudget.currentCap!.toFixed(2))).toBeInTheDocument(),
    )

    await user.click(row.getByRole('button', { name: 'Edit cap' }))
    const input = row.getByLabelText('Monthly cap')
    await user.clear(input)
    await user.type(input, '750')
    await user.click(row.getByRole('button', { name: 'Save cap' }))

    expect(await settingsTable().findByText('750.00')).toBeInTheDocument()
  })

  it('shows a report skeleton only when the report fetch is slow', async () => {
    server.use(
      http.get('/api/budgets/report', async () => {
        await delay(400)
        return HttpResponse.json([])
      }),
    )
    renderWithQueryClient(<BudgetsPage />)

    expect(await screen.findByRole('status', { name: 'Loading budget report' })).toBeInTheDocument()
    expect(await screen.findByText('No budgeted categories yet.')).toBeInTheDocument()
    expect(screen.queryByRole('status', { name: 'Loading budget report' })).not.toBeInTheDocument()
  })

  // The settings table's own load state is `combineLoadState(categoriesState, budgetsState)` (F2
  // audit finding) - distinct from the report skeleton test above, which only covers the
  // budget-vs-actual section. `successBody` must match what the default handler returns too (the
  // after-retry case falls through to it), so `loadedText` holds in both generated cases.
  expectLoadStates({
    render: () => renderWithQueryClient(<BudgetsPage />),
    url: '/api/budgets',
    successBody: seedBudgets,
    // The category name also appears in the budget-vs-actual report line below the table, so the
    // cap value (a lone `TableCell` string, unlike the report's composite line) is unambiguous.
    loadedText: seedGroceriesBudget.currentCap!.toFixed(2),
  })
})

describe('BudgetsPage local-time defaults', () => {
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

  it('defaults the report month to the local month, not the UTC month', async () => {
    renderWithQueryClient(<BudgetsPage />)

    expect(await screen.findByLabelText('Month')).toHaveValue('2026-03')
  })
})
