import { afterEach, describe, expect, it } from 'vitest'
import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { seedCategories } from '../../mocks/handlers/categories'
import { seedGroceriesTransaction, seedTransactions } from '../../mocks/handlers/transactions'
import { restoreViewport, setViewportWidth, VIEWPORT } from '../../test/viewport'
import { AccountTransactionList } from './AccountTransactionList'
import { renderWithQueryClient } from '../../test/renderWithQueryClient'

describe('AccountTransactionList', () => {
  it('renders only transactions for the given account', async () => {
    renderWithQueryClient(<AccountTransactionList accountId="acct-1" />)

    for (const transaction of seedTransactions.filter((t) => t.accountId === 'acct-1')) {
      expect(await screen.findByText(transaction.date)).toBeInTheDocument()
    }
  })

  it('shows an empty state when the account has no transactions', async () => {
    renderWithQueryClient(<AccountTransactionList accountId="acct-with-no-transactions" />)

    expect(await screen.findByText('No transactions yet.')).toBeInTheDocument()
  })
})

describe('AccountTransactionList responsive layout (F021)', () => {
  afterEach(restoreViewport)

  const target = seedGroceriesTransaction

  it('renders cards on mobile: description, date, labelled fields, no actions', async () => {
    setViewportWidth(VIEWPORT.mobile)
    renderWithQueryClient(<AccountTransactionList accountId={target.accountId} />)

    const list = await screen.findByRole('list', { name: 'Account transactions' })
    const card = within(
      (await within(list).findByText(target.description)).closest('li') as HTMLElement,
    )
    expect(screen.queryByRole('table')).not.toBeInTheDocument()
    expect(card.getByText(target.date)).toBeInTheDocument()
    expect(card.getByText(`-${target.amount.toFixed(2)}`)).toBeInTheDocument()
    const categoryName = seedCategories.find((c) => c.id === target.categoryId)!.name
    expect(card.getByText(categoryName)).toBeInTheDocument()
    expect(card.queryByRole('button')).not.toBeInTheDocument()
  })

  it('keeps a table on tablet with Payment Method behind the row expander', async () => {
    setViewportWidth(VIEWPORT.tablet)
    const user = userEvent.setup()
    renderWithQueryClient(<AccountTransactionList accountId={target.accountId} />)

    await screen.findByText(target.description)
    expect(screen.getByRole('table')).toBeInTheDocument()
    expect(screen.queryByRole('columnheader', { name: 'Payment Method' })).not.toBeInTheDocument()
    await user.click(screen.getAllByRole('button', { name: 'Show details' })[0])
    expect(await screen.findByText('Payment Method')).toBeInTheDocument()
  })

  it('shows the full five-column table on desktop', async () => {
    setViewportWidth(VIEWPORT.desktop)
    renderWithQueryClient(<AccountTransactionList accountId={target.accountId} />)

    await screen.findByText(target.description)
    expect(screen.getAllByRole('columnheader').map((h) => h.textContent)).toEqual([
      'Date',
      'Category',
      'Payment Method',
      'Amount',
      'Description',
    ])
  })
})
