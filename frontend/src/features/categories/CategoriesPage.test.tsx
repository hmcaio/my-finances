import { afterEach, describe, expect, it, vi } from 'vitest'
import { fireEvent, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { delay, http, HttpResponse } from 'msw'
import { server } from '../../mocks/server'
import {
  categoryCreateConflictHandler,
  categoryDeleteConflictHandler,
  seedCategories,
  seedDividendCategory,
  seedFuelCategory,
  seedGroceriesCategory,
  seedSalaryCategory,
} from '../../mocks/handlers/categories'
import { CATEGORY_NAME_MAX_LENGTH } from '../../api/categories/categories'
import { PROBLEM_TYPE_MESSAGES } from '../../api/core/apiError'
import { findRow } from '../../test/testUtils'
import { renderWithQueryClient } from '../../test/renderWithQueryClient'
import { describeSettingsPage } from '../../test/settingsPageContract'
import { restoreViewport, setViewportWidth, VIEWPORT } from '../../test/viewport'
import { CategoriesPage } from './CategoriesPage'

const builtInExpense = seedCategories.find((c) => c.builtIn && c.type === 'EXPENSE')!
const builtInIncome = seedCategories.find((c) => c.builtIn && c.type === 'INCOME')!

describe('CategoriesPage', () => {
  describeSettingsPage({
    page: <CategoriesPage />,
    seedRows: seedCategories,
    renameTarget: seedGroceriesCategory,
    deleteTarget: seedSalaryCategory,
    newName: 'Rent',
    addButtonLabel: 'Add category',
    conflict: {
      message: PROBLEM_TYPE_MESSAGES['urn:my-finances:error:category-in-use'],
      handler: categoryDeleteConflictHandler,
    },
    duplicateName: {
      message: PROBLEM_TYPE_MESSAGES['urn:my-finances:error:category-name-already-exists'],
      handler: categoryCreateConflictHandler,
    },
    maxLength: CATEGORY_NAME_MAX_LENGTH,
  })

  // Bespoke: type (income/expense), the built-in row per type, and their ordering have no
  // equivalent in the other settings pages, so they stay here on top of the shared contract.

  it('lists each type with its built-in row first', async () => {
    renderWithQueryClient(<CategoriesPage />)
    await screen.findByText(builtInExpense.name)

    // Row 0 is the header. Expenses lead, the built-in one first within them, then income.
    const names = screen
      .getAllByRole('row')
      .slice(1)
      .map((row) => within(row).getAllByRole('cell')[0].textContent)
    expect(names).toEqual([builtInExpense.name, 'Groceries', builtInIncome.name, 'Salary'])
  })

  it('offers neither a rename nor a delete action on a built-in row', async () => {
    // Issue #117: built-in used to allow Rename; now it's protected like fuel/dividend.
    renderWithQueryClient(<CategoriesPage />)
    await screen.findByText(builtInExpense.name)

    const row = await findRow(builtInExpense.name)
    expect(row.queryByRole('button', { name: 'Rename' })).not.toBeInTheDocument()
    expect(row.queryByRole('button', { name: 'Delete' })).not.toBeInTheDocument()
    // An ordinary row still has both.
    const ordinaryRow = await findRow('Groceries')
    expect(ordinaryRow.getByRole('button', { name: 'Rename' })).toBeInTheDocument()
    expect(ordinaryRow.getByRole('button', { name: 'Delete' })).toBeInTheDocument()
  })

  it('offers neither a rename nor a delete action on the fuel or dividend category', async () => {
    server.use(
      http.get('/api/categories', () =>
        HttpResponse.json([...seedCategories, seedFuelCategory, seedDividendCategory]),
      ),
    )
    renderWithQueryClient(<CategoriesPage />)
    await screen.findByText(seedFuelCategory.name)

    const fuelRow = await findRow(seedFuelCategory.name)
    expect(fuelRow.queryByRole('button', { name: 'Rename' })).not.toBeInTheDocument()
    expect(fuelRow.queryByRole('button', { name: 'Delete' })).not.toBeInTheDocument()
    const dividendRow = await findRow(seedDividendCategory.name)
    expect(dividendRow.queryByRole('button', { name: 'Rename' })).not.toBeInTheDocument()
    expect(dividendRow.queryByRole('button', { name: 'Delete' })).not.toBeInTheDocument()
  })

  // Bespoke load-state coverage: richer than the shared `expectLoadStates` pair (also checks that
  // dismissing the error banner doesn't bring the skeleton back), so it stays as-is rather than
  // being replaced by the generated cases (this page already had full F2 coverage).

  it('shows a loading skeleton only when the first fetch is slow, then the rows', async () => {
    server.use(
      http.get('/api/categories', async () => {
        await delay(400)
        return HttpResponse.json(seedCategories)
      }),
    )
    renderWithQueryClient(<CategoriesPage />)

    expect(await screen.findByText('Loading…')).toBeInTheDocument()
    expect(await screen.findByText(seedGroceriesCategory.name)).toBeInTheDocument()
    expect(screen.queryByText('Loading…')).not.toBeInTheDocument()
  })

  it('shows a failure row, never a skeleton, after a failed first fetch, even with the banner dismissed', async () => {
    server.use(http.get('/api/categories', () => new HttpResponse(null, { status: 500 })))
    renderWithQueryClient(<CategoriesPage />)

    const alert = await screen.findByRole('alert')
    expect(await screen.findByText(/Could not load data/)).toBeInTheDocument()
    // Fake only the timer functions, and only after the real-timer waits above: findBy* polling
    // needs real timers, while the skeleton gate (useDelayedFlag's setTimeout) must be steppable.
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] })
    try {
      // fireEvent, not userEvent: RTL's async wrapper awaits a setTimeout(0) that never fires under fake timers.
      fireEvent.click(within(alert).getByRole('button', { name: 'Close' }))
      // Advance past the 150ms skeleton delay: the dismissed banner must not bring the skeleton back.
      await vi.advanceTimersByTimeAsync(250)
    } finally {
      vi.useRealTimers()
    }

    expect(screen.queryByText('Loading…')).not.toBeInTheDocument()
    expect(screen.getByText(/Could not load data/)).toBeInTheDocument()
  })

  it('retries a failed first fetch from the failure row', async () => {
    server.use(
      http.get('/api/categories', () => new HttpResponse(null, { status: 500 }), { once: true }),
    )
    const user = userEvent.setup({ delay: null })
    renderWithQueryClient(<CategoriesPage />)

    await user.click(await screen.findByRole('button', { name: 'Retry' }))

    expect(await screen.findByText(seedGroceriesCategory.name)).toBeInTheDocument()
    expect(screen.queryByText(/Could not load data/)).not.toBeInTheDocument()
    // The banner from the failed attempt is cleared, not left showing after a successful retry.
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })
})

describe('CategoriesPage responsive layout (F021)', () => {
  afterEach(restoreViewport)

  it('keeps inline rename in the table row at every size (the table never becomes cards)', async () => {
    setViewportWidth(VIEWPORT.mobile)
    const user = userEvent.setup({ delay: null })
    renderWithQueryClient(<CategoriesPage />)
    await screen.findByText(seedGroceriesCategory.name)

    const row = await findRow(seedGroceriesCategory.name)
    await user.click(row.getByRole('button', { name: 'Rename' }))

    expect(row.getByRole('textbox')).toBeInTheDocument()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })
})
