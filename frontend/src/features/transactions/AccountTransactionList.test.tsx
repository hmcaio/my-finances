import { describe, expect, it } from 'vitest'
import { screen } from '@testing-library/react'
import { seedTransactions } from '../../mocks/handlers/transactions'
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
