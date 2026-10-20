import { afterEach, describe, expect, it, vi } from 'vitest'
import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { LedgerEntry } from '../../api/ledger/ledger'
import type { LoadState } from '../../hooks/queryState'
import { renderWithQueryClient } from '../../test/renderWithQueryClient'
import { restoreViewport, setViewportWidth, VIEWPORT } from '../../test/viewport'
import { LedgerTable } from './LedgerTable'
import type { LedgerLookups } from './useLedgerLookups'

const loaded: LoadState = { loading: false, loadError: null, reload: () => {} }

const NAMES: Record<string, string> = {
  checking: 'Checking',
  card: 'Credit Card',
  broker: 'Broker',
  groceries: 'Groceries',
  salary: 'Salary',
  debit: 'Debit Card',
  btc: 'Bitcoin',
  eth: 'Ether',
  sol: 'Solana',
  ada: 'Cardano',
  dot: 'Polkadot',
}
const lookups: LedgerLookups = {
  categoryName: (id) => NAMES[id] ?? id,
  accountName: (id) => NAMES[id] ?? id,
  paymentMethodName: (id) => NAMES[id] ?? id,
  productName: (id) => NAMES[id] ?? id,
}

const base = {
  additionalNotes: null,
  categoryId: null,
  accountId: null,
  paymentMethodId: null,
  fromAccountId: null,
  toAccountId: null,
  tradeLines: [],
} satisfies Partial<LedgerEntry>

const expense: LedgerEntry = {
  ...base,
  id: 'tx-expense',
  type: 'EXPENSE',
  date: '2026-03-12',
  amount: 42.5,
  description: 'Weekly groceries',
  categoryId: 'groceries',
  accountId: 'checking',
  paymentMethodId: 'debit',
}
const income: LedgerEntry = {
  ...base,
  id: 'tx-income',
  type: 'INCOME',
  date: '2026-03-10',
  amount: 3000,
  description: 'Monthly salary',
  categoryId: 'salary',
  accountId: 'checking',
  paymentMethodId: 'debit',
}
const transfer: LedgerEntry = {
  ...base,
  id: 'trf-1',
  type: 'TRANSFER',
  date: '2026-03-05',
  amount: 200,
  description: 'Card payment',
  fromAccountId: 'checking',
  toAccountId: 'card',
}

function trade(productIds: string[]): LedgerEntry {
  return {
    ...transfer,
    id: 'trf-trade',
    description: 'Crypto settlement',
    fromAccountId: 'checking',
    toAccountId: 'broker',
    tradeLines: productIds.map((productId, i) => ({
      productId,
      side: i % 2 === 0 ? ('BUY' as const) : ('SELL' as const),
    })),
  }
}

function renderTable(
  entries: LedgerEntry[],
  props: Partial<Parameters<typeof LedgerTable>[0]> = {},
) {
  return renderWithQueryClient(
    <LedgerTable entries={entries} state={loaded} lookups={lookups} {...props} />,
  )
}

describe('LedgerTable', () => {
  afterEach(restoreViewport)

  describe('desktop', () => {
    function row(description: string) {
      return within(screen.getByText(description).closest('tr') as HTMLElement)
    }

    it('shows income as a signed green row with an accessible up-arrow', () => {
      setViewportWidth(VIEWPORT.desktop)
      renderTable([expense, income, transfer])

      const r = row('Monthly salary')
      expect(r.getByRole('img', { name: 'Income' })).toBeInTheDocument()
      expect(r.getByText('+3,000.00')).toBeInTheDocument()
      expect(r.getByText('Salary')).toBeInTheDocument()
      expect(r.getByText('Checking')).toBeInTheDocument()
      expect(r.getByText('Debit Card')).toBeInTheDocument()
    })

    it('shows expense with a minus and an accessible down-arrow', () => {
      setViewportWidth(VIEWPORT.desktop)
      renderTable([expense])

      const r = row('Weekly groceries')
      expect(r.getByRole('img', { name: 'Expense' })).toBeInTheDocument()
      expect(r.getByText('-42.50')).toBeInTheDocument()
    })

    it('shows a transfer unsigned with From -> To and dashes for category and payment method', () => {
      setViewportWidth(VIEWPORT.desktop)
      renderTable([transfer])

      const r = row('Card payment')
      expect(r.getByRole('img', { name: 'Transfer' })).toBeInTheDocument()
      expect(r.getByText('200.00')).toBeInTheDocument()
      expect(r.queryByText('+200.00')).not.toBeInTheDocument()
      expect(r.queryByText('-200.00')).not.toBeInTheDocument()
      expect(r.getByText('Checking → Credit Card')).toBeInTheDocument()
      expect(r.getAllByText('—')).toHaveLength(2)
    })

    it('shows a Buy/Sell chip per trade line in the account cell', () => {
      setViewportWidth(VIEWPORT.desktop)
      renderTable([trade(['btc', 'eth'])])

      const r = row('Crypto settlement')
      expect(r.getByText('Buy Bitcoin')).toBeInTheDocument()
      expect(r.getByText('Sell Ether')).toBeInTheDocument()
    })

    it('collapses more than three chips into "+N more"', () => {
      setViewportWidth(VIEWPORT.desktop)
      renderTable([trade(['btc', 'eth', 'sol', 'ada', 'dot'])])

      const r = row('Crypto settlement')
      expect(r.getByText('Buy Bitcoin')).toBeInTheDocument()
      expect(r.getByText('Buy Solana')).toBeInTheDocument()
      expect(r.queryByText('Sell Cardano')).not.toBeInTheDocument()
      expect(r.getByText('+2 more')).toBeInTheDocument()
    })

    it('has no edit or delete buttons without handlers (read-only)', () => {
      setViewportWidth(VIEWPORT.desktop)
      renderTable([expense])

      expect(screen.queryByRole('button', { name: 'Edit' })).not.toBeInTheDocument()
      expect(screen.queryByRole('button', { name: 'Delete' })).not.toBeInTheDocument()
      expect(screen.queryByRole('columnheader', { name: 'Actions' })).not.toBeInTheDocument()
    })

    it('calls onEdit and onDelete with the entry of the clicked row', async () => {
      setViewportWidth(VIEWPORT.desktop)
      const user = userEvent.setup({ delay: null })
      const onEdit = vi.fn()
      const onDelete = vi.fn()
      renderTable([expense, transfer], { onEdit, onDelete })

      await user.click(row('Card payment').getByRole('button', { name: 'Edit' }))
      await user.click(row('Weekly groceries').getByRole('button', { name: 'Delete' }))

      expect(onEdit).toHaveBeenCalledWith(transfer)
      expect(onDelete).toHaveBeenCalledWith(expense)
    })

    it('shows every column at desktop width', () => {
      setViewportWidth(VIEWPORT.desktop)
      renderTable([expense])

      for (const name of [
        'Date',
        'Type',
        'Description',
        'Category',
        'Account',
        'Payment Method',
        'Amount',
      ]) {
        expect(screen.getByRole('columnheader', { name })).toBeInTheDocument()
      }
    })

    it('drops the payment method column on tablet', () => {
      setViewportWidth(VIEWPORT.tablet)
      renderTable([expense])

      expect(screen.getByRole('columnheader', { name: 'Amount' })).toBeInTheDocument()
      expect(screen.queryByRole('columnheader', { name: 'Payment Method' })).not.toBeInTheDocument()
    })

    it('shows the empty message when there are no entries', () => {
      setViewportWidth(VIEWPORT.desktop)
      renderTable([], { emptyMessage: 'Nothing here.' })

      expect(screen.getByText('Nothing here.')).toBeInTheDocument()
    })

    it('shows a failure with Retry when the first load failed', async () => {
      setViewportWidth(VIEWPORT.desktop)
      const user = userEvent.setup({ delay: null })
      const onRetry = vi.fn()
      renderTable([], { state: { loading: false, loadError: 'boom', reload: () => {} }, onRetry })

      await user.click(await screen.findByRole('button', { name: 'Retry' }))

      expect(onRetry).toHaveBeenCalled()
    })
  })

  describe('mobile', () => {
    it('renders a card per entry with icon, signed amount, and the actions', () => {
      setViewportWidth(VIEWPORT.mobile)
      renderTable([expense, income, transfer], { onEdit: () => {}, onDelete: () => {} })

      expect(screen.queryByRole('table')).not.toBeInTheDocument()
      const list = screen.getByRole('list', { name: 'Ledger' })
      const cards = within(list).getAllByRole('listitem')
      expect(cards).toHaveLength(3)

      const expenseCard = within(cards[0])
      expect(expenseCard.getByRole('img', { name: 'Expense' })).toBeInTheDocument()
      expect(expenseCard.getByText('-42.50')).toBeInTheDocument()
      expect(expenseCard.getByText('2026-03-12 · Groceries')).toBeInTheDocument()
      expect(expenseCard.getByText('Checking · Debit Card')).toBeInTheDocument()
      expect(expenseCard.getByRole('button', { name: 'Edit' })).toBeInTheDocument()

      const incomeCard = within(cards[1])
      expect(incomeCard.getByRole('img', { name: 'Income' })).toBeInTheDocument()
      expect(incomeCard.getByText('+3,000.00')).toBeInTheDocument()

      const transferCard = within(cards[2])
      expect(transferCard.getByRole('img', { name: 'Transfer' })).toBeInTheDocument()
      expect(transferCard.getByText('200.00')).toBeInTheDocument()
      expect(transferCard.getByText('Checking → Credit Card')).toBeInTheDocument()
    })

    it('read-only cards carry no action buttons', () => {
      setViewportWidth(VIEWPORT.mobile)
      renderTable([expense])

      expect(screen.queryByRole('button', { name: 'Edit' })).not.toBeInTheDocument()
    })
  })
})
