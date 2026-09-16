import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import { seedTransfers } from '../../mocks/handlers/transfers'
import { AccountTransferList } from './AccountTransferList'

describe('AccountTransferList', () => {
  it('renders only transfers touching the given account, on either side', async () => {
    render(<AccountTransferList accountId="acct-1" />)

    for (const transfer of seedTransfers.filter(
      (t) => t.fromAccountId === 'acct-1' || t.toAccountId === 'acct-1',
    )) {
      expect(await screen.findByText(transfer.description)).toBeInTheDocument()
    }
  })

  it('shows an empty state when the account has no transfers', async () => {
    render(<AccountTransferList accountId="acct-with-no-transfers" />)

    expect(await screen.findByText('No transfers yet.')).toBeInTheDocument()
  })
})
