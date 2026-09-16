import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import { seedTransactions } from '../../mocks/handlers/transactions'
import { AccountTransactionList } from './AccountTransactionList'

describe('AccountTransactionList', () => {
  it('renders only transactions for the given account', async () => {
    render(<AccountTransactionList accountId="acct-1" />)

    for (const transaction of seedTransactions.filter((t) => t.accountId === 'acct-1')) {
      expect(await screen.findByText(transaction.date)).toBeInTheDocument()
    }
  })

  it('shows an empty state when the account has no transactions', async () => {
    render(<AccountTransactionList accountId="acct-with-no-transactions" />)

    expect(await screen.findByText('No transactions yet.')).toBeInTheDocument()
  })
})
