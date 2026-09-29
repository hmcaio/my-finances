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
import { CREATE_CONFLICT_MESSAGE } from '../../api/budgets/budgets'
import { findRow } from '../../test/testUtils'
import { expectLoadStates } from '../../test/loadStates'
import { restoreViewport, setViewportWidth, VIEWPORT } from '../../test/viewport'
import { BudgetsPage } from './BudgetsPage'
import { renderWithQueryClient } from '../../test/renderWithQueryClient'

/** Scopes queries to the budget settings table - the category name also appears in the
 * budget-vs-actual section below it, so an unscoped `getByText` would be ambiguous. */
function settingsTable() {
  return within(screen.getByRole('table'))
}

/** The add-budget fields only exist inside the header's Add dialog. */
async function openAddDialog(user: ReturnType<typeof userEvent.setup>) {
  await user.click(await screen.findByRole('button', { name: 'Add budget' }))
  return screen.findByRole('dialog')
}

describe('BudgetsPage', () => {
  // jsdom has no viewport, which MUI treats as the tablet band: no column is hidden there for this
  // page, but pinning desktop keeps these tests unambiguous about which band they exercise.
  beforeEach(() => setViewportWidth(VIEWPORT.desktop))
  afterEach(restoreViewport)

  it('fetches the categories once for the page and its embedded report (shared query)', async () => {
    let categoryRequests = 0
    server.use(
      http.get('/api/categories', () => {
        categoryRequests += 1
        return HttpResponse.json(seedCategories)
      }),
    )
    renderWithQueryClient(<BudgetsPage />)

    await findRow(seedGroceriesCategory.name, settingsTable())
    await screen.findByText(/620\.00 \/ 500\.00 — over budget/)

    expect(categoryRequests).toBe(1)
  })

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
    const user = userEvent.setup({ delay: null })
    renderWithQueryClient(<BudgetsPage />)
    await findRow(seedGroceriesCategory.name, settingsTable())
    const dialog = await openAddDialog(user)

    // Groceries is already budgeted and Salary is income - neither belongs here.
    await user.click(within(dialog).getByLabelText('Category'))
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
    const user = userEvent.setup({ delay: null })
    renderWithQueryClient(<BudgetsPage />)
    await findRow(seedGroceriesCategory.name, settingsTable())
    const dialog = await openAddDialog(user)

    await user.click(within(dialog).getByLabelText('Category'))
    await user.click(await screen.findByRole('option', { name: 'Dining' }))
    await user.type(within(dialog).getByLabelText('Monthly cap'), '150')
    await user.click(within(dialog).getByRole('button', { name: 'Add' }))

    // The dialog (which hides the table from the accessibility tree while open) must close before
    // a `getByRole('table')`-scoped query can find it again.
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
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
    const user = userEvent.setup({ delay: null })
    renderWithQueryClient(<BudgetsPage />)
    await findRow(seedGroceriesCategory.name, settingsTable())
    const dialog = await openAddDialog(user)

    await user.click(within(dialog).getByLabelText('Category'))
    await user.click(await screen.findByRole('option', { name: 'Dining' }))
    await user.type(within(dialog).getByLabelText('Monthly cap'), '150')
    await user.click(within(dialog).getByRole('button', { name: 'Add' }))

    expect(await within(dialog).findByText(CREATE_CONFLICT_MESSAGE)).toBeInTheDocument()
    expect(screen.getByRole('dialog')).toBeInTheDocument()
  })

  it('edits a budget cap inline', async () => {
    const user = userEvent.setup({ delay: null })
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

  it('stops a budget after confirming, keeping past months and hiding it from the picker', async () => {
    // Once stopped the backend omits the line from the current month's report.
    server.use(http.get('/api/budgets/report', () => HttpResponse.json([])))
    const user = userEvent.setup({ delay: null })
    renderWithQueryClient(<BudgetsPage />)
    const row = await findRow(seedGroceriesCategory.name, settingsTable())

    await user.click(row.getByRole('button', { name: 'Stop budget' }))
    const dialog = await screen.findByRole('dialog')
    expect(within(dialog).getByText(/Past months keep the cap they had/)).toBeInTheDocument()
    await user.click(within(dialog).getByRole('button', { name: 'Stop budget' }))

    await waitFor(() => expect(settingsTable().getByText('Stopped')).toBeInTheDocument())
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    const stoppedRow = await findRow(seedGroceriesCategory.name, settingsTable())
    expect(stoppedRow.queryByRole('button', { name: 'Stop budget' })).not.toBeInTheDocument()
    expect(stoppedRow.getByRole('button', { name: 'Resume budget' })).toBeInTheDocument()
    expect(await screen.findByText('No active budgets for this month.')).toBeInTheDocument()

    // Its category already has a budget row: not offered for a duplicate.
    const addDialog = await openAddDialog(user)
    await user.click(within(addDialog).getByLabelText('Category'))
    expect(
      screen.queryByRole('option', { name: seedGroceriesCategory.name }),
    ).not.toBeInTheDocument()
  })

  it('does nothing when the stop confirmation is cancelled', async () => {
    const user = userEvent.setup({ delay: null })
    renderWithQueryClient(<BudgetsPage />)
    const row = await findRow(seedGroceriesCategory.name, settingsTable())

    await user.click(row.getByRole('button', { name: 'Stop budget' }))
    await user.click(
      within(await screen.findByRole('dialog')).getByRole('button', { name: 'Cancel' }),
    )

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(settingsTable().queryByText('Stopped')).not.toBeInTheDocument()
  })

  it('shows an error banner when stopping fails', async () => {
    server.use(http.post('/api/budgets/:id/stop', () => new HttpResponse(null, { status: 404 })))
    const user = userEvent.setup({ delay: null })
    renderWithQueryClient(<BudgetsPage />)
    const row = await findRow(seedGroceriesCategory.name, settingsTable())

    await user.click(row.getByRole('button', { name: 'Stop budget' }))
    await user.click(
      within(await screen.findByRole('dialog')).getByRole('button', { name: 'Stop budget' }),
    )

    expect(await screen.findByRole('alert')).toBeInTheDocument()
    expect(settingsTable().queryByText('Stopped')).not.toBeInTheDocument()
  })

  it('resumes a stopped budget through the cap edit', async () => {
    const user = userEvent.setup({ delay: null })
    renderWithQueryClient(<BudgetsPage />)
    const row = await findRow(seedGroceriesCategory.name, settingsTable())
    await user.click(row.getByRole('button', { name: 'Stop budget' }))
    await user.click(
      within(await screen.findByRole('dialog')).getByRole('button', { name: 'Stop budget' }),
    )
    await waitFor(() => expect(settingsTable().getByText('Stopped')).toBeInTheDocument())

    const stoppedRow = await findRow(seedGroceriesCategory.name, settingsTable())
    await user.click(stoppedRow.getByRole('button', { name: 'Resume budget' }))
    await user.type(stoppedRow.getByLabelText('Monthly cap'), '640')
    await user.click(stoppedRow.getByRole('button', { name: 'Resume with this cap' }))

    expect(await settingsTable().findByText('640.00')).toBeInTheDocument()
    expect(settingsTable().queryByText('Stopped')).not.toBeInTheDocument()
    expect(
      (await findRow(seedGroceriesCategory.name, settingsTable())).getByRole('button', {
        name: 'Stop budget',
      }),
    ).toBeInTheDocument()
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
    expect(await screen.findByText('No active budgets for this month.')).toBeInTheDocument()
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

describe('BudgetsPage responsive layout (F021)', () => {
  afterEach(restoreViewport)

  async function findCard(name: string) {
    const list = await screen.findByRole('list', { name: 'Budgets' })
    await within(list).findByText(name)
    const items = within(list).getAllByRole('listitem')
    return within(items.find((item) => within(item).queryByText(name))!)
  }

  describe('mobile', () => {
    beforeEach(() => setViewportWidth(VIEWPORT.mobile))

    it('renders cards instead of a table, with the cap and effective-from date', async () => {
      renderWithQueryClient(<BudgetsPage />)

      const card = await findCard(seedGroceriesCategory.name)
      expect(screen.queryByRole('table')).not.toBeInTheDocument()
      expect(card.getByText(seedGroceriesBudget.currentCap!.toFixed(2))).toBeInTheDocument()
      expect(card.getByText(seedGroceriesBudget.currentCapEffectiveFrom!)).toBeInTheDocument()
      expect(card.getByRole('button', { name: 'Edit cap' })).toBeInTheDocument()
      expect(card.getByRole('button', { name: 'Stop budget' })).toBeInTheDocument()
    })

    it('Add opens a full-screen dialog', async () => {
      const user = userEvent.setup({ delay: null })
      renderWithQueryClient(<BudgetsPage />)
      await findCard(seedGroceriesCategory.name)

      const dialog = await openAddDialog(user)

      expect(dialog).toHaveClass('MuiDialog-paperFullScreen')
    })

    it('Edit cap opens a full-screen dialog instead of editing on the card', async () => {
      const user = userEvent.setup({ delay: null })
      renderWithQueryClient(<BudgetsPage />)
      const card = await findCard(seedGroceriesCategory.name)

      await user.click(card.getByRole('button', { name: 'Edit cap' }))

      const dialog = await screen.findByRole('dialog')
      expect(dialog).toHaveClass('MuiDialog-paperFullScreen')
      const input = within(dialog).getByLabelText('Monthly cap')
      expect(input).toHaveValue(seedGroceriesBudget.currentCap)
      await user.clear(input)
      await user.type(input, '900')
      await user.click(within(dialog).getByRole('button', { name: 'Save cap' }))

      expect(await screen.findByText('900.00')).toBeInTheDocument()
      await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    })
  })

  describe('tablet', () => {
    beforeEach(() => setViewportWidth(VIEWPORT.tablet))

    it('still edits the cap inline in the row, with no edit dialog', async () => {
      const user = userEvent.setup({ delay: null })
      renderWithQueryClient(<BudgetsPage />)
      const row = await findRow(seedGroceriesCategory.name, settingsTable())

      await user.click(row.getByRole('button', { name: 'Edit cap' }))

      expect(row.getByLabelText('Monthly cap')).toHaveValue(seedGroceriesBudget.currentCap)
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    })

    it('Add opens a regular (not full-screen) dialog', async () => {
      const user = userEvent.setup({ delay: null })
      renderWithQueryClient(<BudgetsPage />)
      await findRow(seedGroceriesCategory.name, settingsTable())

      const dialog = await openAddDialog(user)

      expect(dialog).not.toHaveClass('MuiDialog-paperFullScreen')
    })
  })
})
