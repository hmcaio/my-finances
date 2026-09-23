import type { ReactElement } from 'react'
import { render, screen, within, type RenderResult } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'

/**
 * Finds the `<tr>` containing the given text and scopes queries to it - the shape every list
 * page's row-level assertions and interactions share. Defaults to searching the whole document;
 * pass a `within(...)`-scoped table (e.g. `settingsTable()`) when the same text could also
 * appear elsewhere on the page (see `BudgetsPage.test.tsx`, `RecurringTemplatesPage.test.tsx`).
 */
export async function findRow(
  name: string,
  scope: Pick<typeof screen, 'findByText'> = screen,
): Promise<ReturnType<typeof within>> {
  const cell = await scope.findByText(name)
  return within(cell.closest('tr') as HTMLElement)
}

/**
 * Opens a MUI Select combobox by its accessible name and picks the named option. Defaults to
 * the whole screen; pass a `within(...)`-scoped container (a form, a dialog) when more than one
 * combobox with that name can render at once.
 */
export async function selectOption(
  user: ReturnType<typeof userEvent.setup>,
  comboboxName: string,
  optionName: string,
  scope: Pick<typeof screen, 'getByRole'> = screen,
): Promise<void> {
  await user.click(scope.getByRole('combobox', { name: comboboxName }))
  await user.click(await screen.findByRole('option', { name: optionName }))
}

/**
 * Renders `ui` inside a `MemoryRouter`, for a page/component that calls router hooks or renders
 * a `Link` outside of `App`'s own `BrowserRouter`. Pass `initialEntries` for a page that reads
 * its route params (e.g. `AccountDetailPage`'s `:id`).
 */
export function renderWithRouter(
  ui: ReactElement,
  options?: { initialEntries?: string[] },
): RenderResult {
  return render(<MemoryRouter initialEntries={options?.initialEntries}>{ui}</MemoryRouter>)
}
