import { describe, expect, it } from 'vitest'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { seedCategories } from '../../mocks/handlers/categories'
import { seedAccounts } from '../../mocks/handlers/accounts'
import { seedRecurringTemplates } from '../../mocks/handlers/recurringTemplates'
import { RecurringTemplatesPage } from './RecurringTemplatesPage'

/** Scopes queries to the templates settings table - the pending-occurrences widget below it
 * renders overlapping text (description/category/account), so an unscoped `getByText` would be
 * ambiguous, same reasoning as F006's `BudgetsPage.test.tsx`. */
function templatesTable() {
  return within(screen.getAllByRole('table')[0])
}

async function findRow(name: string) {
  const cell = await templatesTable().findByText(name)
  return within(cell.closest('tr') as HTMLElement)
}

describe('RecurringTemplatesPage', () => {
  it('renders the seeded template with its current amount and day of month', async () => {
    render(<RecurringTemplatesPage />)

    const row = await findRow(seedRecurringTemplates[0].description)
    expect(row.getByText(seedRecurringTemplates[0].currentAmount!.toFixed(2))).toBeInTheDocument()
    expect(
      row.getByText(String(seedRecurringTemplates[0].currentDayOfMonth)),
    ).toBeInTheDocument()
    expect(row.getByText('Active')).toBeInTheDocument()
  })

  it('adds a new recurring template', async () => {
    const user = userEvent.setup()
    render(<RecurringTemplatesPage />)
    await findRow(seedRecurringTemplates[0].description)

    await user.click(screen.getByLabelText('Category'))
    await user.click(await screen.findByRole('option', { name: seedCategories[0].name }))
    await user.click(screen.getByLabelText('Account'))
    await user.click(await screen.findByRole('option', { name: seedAccounts[0].name }))
    await user.type(screen.getByLabelText('Description'), 'Internet')
    await user.type(screen.getByLabelText('Amount'), '120')
    const dayInput = screen.getByLabelText('Day of month')
    await user.clear(dayInput)
    await user.type(dayInput, '15')
    await user.click(screen.getByRole('button', { name: 'Add' }))

    expect(await templatesTable().findByText('Internet')).toBeInTheDocument()
  })

  it('edits a template amount and day of month inline', async () => {
    const user = userEvent.setup()
    render(<RecurringTemplatesPage />)
    const row = await findRow(seedRecurringTemplates[0].description)

    await user.click(row.getByRole('button', { name: 'Edit amount and day' }))
    const amountInput = row.getByLabelText('Amount')
    await user.clear(amountInput)
    await user.type(amountInput, '1750')
    await user.click(row.getByRole('button', { name: 'Save cap' }))

    expect(await templatesTable().findByText('1750.00')).toBeInTheDocument()
  })

  it('stops an active template', async () => {
    const user = userEvent.setup()
    render(<RecurringTemplatesPage />)
    const row = await findRow(seedRecurringTemplates[0].description)

    await user.click(row.getByRole('button', { name: 'Stop' }))

    expect(await row.findByText('Stopped')).toBeInTheDocument()
  })
})
