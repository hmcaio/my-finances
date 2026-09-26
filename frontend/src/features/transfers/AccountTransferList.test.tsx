import { afterEach, describe, expect, it } from 'vitest'
import { screen, within } from '@testing-library/react'
import { seedAccounts } from '../../mocks/handlers/accounts'
import { seedCreditCardPaymentTransfer, seedTransfers } from '../../mocks/handlers/transfers'
import { restoreViewport, setViewportWidth, VIEWPORT } from '../../test/viewport'
import { AccountTransferList } from './AccountTransferList'
import { renderWithQueryClient } from '../../test/renderWithQueryClient'

describe('AccountTransferList', () => {
  it('renders only transfers touching the given account, on either side', async () => {
    renderWithQueryClient(<AccountTransferList accountId="acct-1" />)

    for (const transfer of seedTransfers.filter(
      (t) => t.fromAccountId === 'acct-1' || t.toAccountId === 'acct-1',
    )) {
      expect(await screen.findByText(transfer.description)).toBeInTheDocument()
    }
  })

  it('shows an empty state when the account has no transfers', async () => {
    renderWithQueryClient(<AccountTransferList accountId="acct-with-no-transfers" />)

    expect(await screen.findByText('No transfers yet.')).toBeInTheDocument()
  })
})

describe('AccountTransferList responsive layout (F021)', () => {
  afterEach(restoreViewport)

  const target = seedCreditCardPaymentTransfer
  const name = (id: string) => seedAccounts.find((a) => a.id === id)!.name

  it('renders cards on mobile: description, date, labelled From/To/Amount', async () => {
    setViewportWidth(VIEWPORT.mobile)
    renderWithQueryClient(<AccountTransferList accountId={target.fromAccountId} />)

    const list = await screen.findByRole('list', { name: 'Account transfers' })
    const card = within(
      (await within(list).findByText(target.description)).closest('li') as HTMLElement,
    )
    expect(screen.queryByRole('table')).not.toBeInTheDocument()
    expect(card.getByText(target.date)).toBeInTheDocument()
    expect(card.getByText(name(target.fromAccountId))).toBeInTheDocument()
    expect(card.getByText(name(target.toAccountId))).toBeInTheDocument()
    expect(card.getByText(target.amount.toFixed(2))).toBeInTheDocument()
  })

  it.each([
    ['tablet', VIEWPORT.tablet],
    ['desktop', VIEWPORT.desktop],
  ])('shows all five columns as a table on %s', async (_band, width) => {
    setViewportWidth(width)
    renderWithQueryClient(<AccountTransferList accountId={target.fromAccountId} />)

    await screen.findByText(target.description)
    expect(screen.getAllByRole('columnheader').map((h) => h.textContent)).toEqual([
      'Date',
      'From',
      'To',
      'Amount',
      'Description',
    ])
    expect(screen.queryByRole('button', { name: 'Show details' })).not.toBeInTheDocument()
  })
})
