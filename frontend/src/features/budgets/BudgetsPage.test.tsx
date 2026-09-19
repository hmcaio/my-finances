import { describe, expect, it } from 'vitest'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { delay, http, HttpResponse } from 'msw'
import { server } from '../../mocks/server'
import { seedCategories } from '../../mocks/handlers/categories'
import {
  budgetCreateConflictHandler,
  seedBudgetReport,
  seedBudgets,
} from '../../mocks/handlers/budgets'
import { BudgetsPage } from './BudgetsPage'

/** Scopes queries to the budget settings table - the category name also appears in the
 * budget-vs-actual section below it, so an unscoped `getByText` would be ambiguous. */
function settingsTable() {
  return within(screen.getByRole('table'))
}

async function findRow(name: string) {
  const cell = await settingsTable().findByText(name)
  return within(cell.closest('tr') as HTMLElement)
}

describe('BudgetsPage', () => {
  it('renders the seeded budget with its current cap', async () => {
    render(<BudgetsPage />)

    const row = await findRow(seedCategories[0].name)
    expect(row.getByText(seedBudgets[0].currentCap!.toFixed(2))).toBeInTheDocument()
  })

  it('renders the budget-vs-actual report for the current month, flagging an over-cap category', async () => {
    render(<BudgetsPage />)

    const line = seedBudgetReport[0]
    expect(
      await screen.findByText(new RegExp(`${line.actual.toFixed(2)} / ${line.cap!.toFixed(2)}`)),
    ).toBeInTheDocument()
    expect(await screen.findByText(/over budget/)).toBeInTheDocument()
  })

  it('offers only unbudgeted expense categories in the add-budget picker', async () => {
    const user = userEvent.setup()
    render(<BudgetsPage />)
    await findRow(seedCategories[0].name)

    // cat-1 (Groceries) is already budgeted and cat-2 (Salary) is income - neither belongs here.
    await user.click(screen.getByLabelText('Category'))
    expect(screen.queryByRole('option', { name: seedCategories[0].name })).not.toBeInTheDocument()
    expect(screen.queryByRole('option', { name: seedCategories[1].name })).not.toBeInTheDocument()
  })

  it('adds a new budget for an unbudgeted expense category', async () => {
    server.use(
      http.get('/api/categories', () =>
        HttpResponse.json([...seedCategories, { id: 'cat-3', name: 'Dining', type: 'EXPENSE' }]),
      ),
    )
    const user = userEvent.setup()
    render(<BudgetsPage />)
    await findRow(seedCategories[0].name)

    await user.click(screen.getByLabelText('Category'))
    await user.click(await screen.findByRole('option', { name: 'Dining' }))
    await user.type(screen.getByLabelText('Monthly cap'), '150')
    await user.click(screen.getByRole('button', { name: 'Add' }))

    expect(await settingsTable().findByText('Dining')).toBeInTheDocument()
  })

  it('surfaces the 409 conflict message when create fails', async () => {
    server.use(
      http.get('/api/categories', () =>
        HttpResponse.json([...seedCategories, { id: 'cat-3', name: 'Dining', type: 'EXPENSE' }]),
      ),
      budgetCreateConflictHandler,
    )
    const user = userEvent.setup()
    render(<BudgetsPage />)
    await findRow(seedCategories[0].name)

    await user.click(screen.getByLabelText('Category'))
    await user.click(await screen.findByRole('option', { name: 'Dining' }))
    await user.type(screen.getByLabelText('Monthly cap'), '150')
    await user.click(screen.getByRole('button', { name: 'Add' }))

    expect(await screen.findByText(/cannot be budgeted/)).toBeInTheDocument()
  })

  it('edits a budget cap inline', async () => {
    const user = userEvent.setup()
    render(<BudgetsPage />)
    const row = await findRow(seedCategories[0].name)
    await waitFor(() =>
      expect(row.getByText(seedBudgets[0].currentCap!.toFixed(2))).toBeInTheDocument(),
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
    render(<BudgetsPage />)

    expect(await screen.findByRole('status', { name: 'Loading budget report' })).toBeInTheDocument()
    expect(await screen.findByText('No budgeted categories yet.')).toBeInTheDocument()
    expect(screen.queryByRole('status', { name: 'Loading budget report' })).not.toBeInTheDocument()
  })
})
