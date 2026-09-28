import { afterEach, describe, expect, it, vi } from 'vitest'
import { fireEvent, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { delay, http, HttpResponse } from 'msw'
import { server } from '../../mocks/server'
import {
  categoryCreateConflictHandler,
  categoryDeleteConflictHandler,
  seedCategories,
  seedGroceriesCategory,
  seedSalaryCategory,
} from '../../mocks/handlers/categories'
import {
  CATEGORY_NAME_MAX_LENGTH,
  CONFLICT_MESSAGE,
  DUPLICATE_NAME_MESSAGE,
} from '../../api/categories/categories'
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
    conflict: { message: CONFLICT_MESSAGE, handler: categoryDeleteConflictHandler },
    duplicateName: { message: DUPLICATE_NAME_MESSAGE, handler: categoryCreateConflictHandler },
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

  it('offers no delete action on a built-in row, only rename', async () => {
    renderWithQueryClient(<CategoriesPage />)
    await screen.findByText(builtInExpense.name)

    const row = await findRow(builtInExpense.name)
    expect(row.getByRole('button', { name: 'Rename' })).toBeInTheDocument()
    expect(row.queryByRole('button', { name: 'Delete' })).not.toBeInTheDocument()
    // An ordinary row still has it.
    expect((await findRow('Groceries')).getByRole('button', { name: 'Delete' })).toBeInTheDocument()
  })

  it('renames a built-in row, which stays first and still has no delete action', async () => {
    const user = userEvent.setup()
    renderWithQueryClient(<CategoriesPage />)
    await screen.findByText(builtInIncome.name)

    const row = await findRow(builtInIncome.name)
    await user.click(row.getByRole('button', { name: 'Rename' }))
    const input = row.getByRole('textbox')
    await user.clear(input)
    await user.type(input, 'Outras receitas')
    await user.click(row.getByRole('button', { name: 'Save' }))

    expect(await screen.findByText('Outras receitas')).toBeInTheDocument()
    const renamed = await findRow('Outras receitas')
    expect(renamed.queryByRole('button', { name: 'Delete' })).not.toBeInTheDocument()
    const names = screen
      .getAllByRole('row')
      .slice(1)
      .map((r) => within(r).getAllByRole('cell')[0].textContent)
    expect(names.indexOf('Outras receitas')).toBeLessThan(names.indexOf('Salary'))
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
    const user = userEvent.setup()
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

  it('opens the add dialog full-screen on mobile', async () => {
    setViewportWidth(VIEWPORT.mobile)
    const user = userEvent.setup()
    renderWithQueryClient(<CategoriesPage />)
    await screen.findByText(seedGroceriesCategory.name)

    await user.click(screen.getByRole('button', { name: 'Add category' }))
    const dialog = await screen.findByRole('dialog')

    expect(dialog).toHaveClass('MuiDialog-paperFullScreen')
  })

  it('opens the add dialog as a regular (not full-screen) dialog on tablet', async () => {
    setViewportWidth(VIEWPORT.tablet)
    const user = userEvent.setup()
    renderWithQueryClient(<CategoriesPage />)
    await screen.findByText(seedGroceriesCategory.name)

    await user.click(screen.getByRole('button', { name: 'Add category' }))
    const dialog = await screen.findByRole('dialog')

    expect(dialog).not.toHaveClass('MuiDialog-paperFullScreen')
  })

  it('keeps inline rename in the table row at every size (the table never becomes cards)', async () => {
    setViewportWidth(VIEWPORT.mobile)
    const user = userEvent.setup()
    renderWithQueryClient(<CategoriesPage />)
    await screen.findByText(seedGroceriesCategory.name)

    const row = await findRow(seedGroceriesCategory.name)
    await user.click(row.getByRole('button', { name: 'Rename' }))

    expect(row.getByRole('textbox')).toBeInTheDocument()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })
})
