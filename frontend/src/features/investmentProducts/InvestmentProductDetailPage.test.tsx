import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { delay, http, HttpResponse } from 'msw'
import { Route, Routes } from 'react-router-dom'
import { server } from '../../mocks/server'
import { seedAccounts, seedInvestmentAccount } from '../../mocks/handlers/accounts'
import type { Account } from '../../api/accounts/accounts'
import {
  investmentProductDeleteConflictHandler,
  investmentProductEditConflictHandler,
  seedInvestmentProducts,
} from '../../mocks/handlers/investmentProducts'
import {
  investmentHoldingCloseConflictHandler,
  investmentHoldingCreateConflictHandler,
  investmentHoldingDeleteConflictHandler,
} from '../../mocks/handlers/investmentHoldings'
import {
  investmentOpeningPositionsStore,
  seedOpeningPosition,
} from '../../mocks/handlers/investmentOpeningPositions'
import { seedBitcoinSnapshots } from '../../mocks/handlers/investmentSnapshots'
import { seedBitcoinBuyTransfer, seedBitcoinSellTransfer } from '../../mocks/handlers/transfers'
import { resolveErrorMessage } from '../../test/errorMessages'
import { renderWithRouter, selectOption } from '../../test/testUtils'
import { restoreViewport, setViewportWidth, VIEWPORT } from '../../test/viewport'
import { formatMoney } from '../../i18n/format'
import { InvestmentProductDetailPage } from './InvestmentProductDetailPage'

const bitcoin = seedInvestmentProducts.find((p) => p.id === 'iprod-btc')!

function renderDetail(id = 'iprod-btc', extraAccounts: Account[] = []) {
  // The real accounts endpoint includes INVESTMENT accounts; the default seed keeps them out.
  server.use(
    http.get('/api/accounts', () =>
      HttpResponse.json([...seedAccounts, seedInvestmentAccount, ...extraAccounts]),
    ),
  )
  return renderWithRouter(
    <Routes>
      <Route path="/investment-products/:id" element={<InvestmentProductDetailPage />} />
      <Route path="/accounts" element={<p>Accounts page</p>} />
    </Routes>,
    { initialEntries: [`/investment-products/${id}`] },
  )
}

describe('InvestmentProductDetailPage', () => {
  // jsdom has no viewport, which MUI treats as the tablet band: pin the desktop layout so the
  // trades table's record-only columns (hidden on tablet behind the row expander) are visible.
  beforeEach(() => setViewportWidth(VIEWPORT.desktop))
  afterEach(restoreViewport)

  it('shows the product, its latest value and the needs-snapshot badge', async () => {
    renderDetail()

    expect(await screen.findByRole('heading', { name: 'Bitcoin' })).toBeInTheDocument()
    expect(screen.getByText('Needs snapshot')).toBeInTheDocument()
    expect(screen.getByText('Latest snapshot 2026-08-05')).toBeInTheDocument()
    // The big figure is the latest snapshot (also a row of the history table below).
    expect(screen.getAllByText('900.00').length).toBeGreaterThan(0)
    expect(screen.getByRole('link', { name: /Back to account/ })).toHaveAttribute(
      'href',
      `/accounts/${seedInvestmentAccount.id}`,
    )
  })

  it("shows the selected holding's opening position card, with an add action when there is none (F038)", async () => {
    renderDetail('iprod-btc')

    expect(await screen.findByText('Opening position')).toBeInTheDocument()
    expect(await screen.findByRole('button', { name: 'Add opening position' })).toBeInTheDocument()
  })

  it('shows the recorded opening position of the holding (F038)', async () => {
    investmentOpeningPositionsStore.add({ ...seedOpeningPosition, holdingId: 'iholding-btc' })
    renderDetail('iprod-btc')

    expect(await screen.findByText('Amount contributed')).toBeInTheDocument()
    expect(screen.getByText('6,000.00')).toBeInTheDocument()
  })

  it('lists the snapshot history, most recent first', async () => {
    renderDetail()
    const history = await screen.findByRole('table', { name: 'Snapshot history' })
    // The snapshot query only starts once the holdings list resolves (one round trip after the
    // table itself appears), so wait for real data before reading the rows.
    await within(history).findByText(seedBitcoinSnapshots[0].date)

    const dates = within(history)
      .getAllByRole('row')
      .slice(1)
      .map((row) => within(row).getAllByRole('cell')[0].textContent)
    expect(dates).toEqual(seedBitcoinSnapshots.map((s) => s.date))
  })

  it('lists one row per line with its derived side, quantity/unit price and a link to the confirmation', async () => {
    renderDetail()
    const trades = await screen.findByRole('table', { name: 'Trades' })
    await within(trades).findByText(seedBitcoinBuyTransfer.date)

    const buy = within(trades).getByText(seedBitcoinBuyTransfer.date).closest('tr')!
    expect(within(buy).getByText('Buy')).toBeInTheDocument()
    expect(within(buy).getByText('0.01')).toBeInTheDocument()
    expect(within(buy).getByText('100000')).toBeInTheDocument()
    expect(within(buy).getByRole('link', { name: 'View confirmation' })).toHaveAttribute(
      'href',
      `/ledger?focus=${seedBitcoinBuyTransfer.id}`,
    )
    const sell = within(trades).getByText(seedBitcoinSellTransfer.date).closest('tr')!
    expect(within(sell).getByText('Sell')).toBeInTheDocument()
    // No Taxes column at all - a confirmation's taxes cover its whole settlement, not one line.
    expect(within(trades).queryByRole('columnheader', { name: 'Taxes' })).not.toBeInTheDocument()
  })

  it('draws the value/contribution chart and lists the raw monthly numbers with units', async () => {
    renderDetail()

    expect(await screen.findByRole('img', { name: /Value and contributions/ })).toBeInTheDocument()
    const monthly = await screen.findByRole('table', { name: 'Monthly values' })
    expect(within(monthly).getByText('2026-06')).toBeInTheDocument()
    const august = within(monthly).getByText('2026-08').closest('tr')!
    expect(within(august).getByText('900.00')).toBeInTheDocument()
    expect(within(august).getByText('500.00')).toBeInTheDocument()
    expect(within(august).getByText('0.01')).toBeInTheDocument()
    // No snapshot yet in June: a dash, not a zero.
    expect(within(within(monthly).getByText('2026-06').closest('tr')!).getByText('-')).toBeVisible()
  })

  it('records a snapshot and adds it to the history', async () => {
    const user = userEvent.setup({ delay: null })
    let sent: unknown = null
    server.use(
      http.post('/api/investment-holdings/:id/snapshots', async ({ request }) => {
        // Record the body, then fall through to the default (stateful) handler.
        sent = await request.clone().json()
      }),
    )
    renderDetail()
    await screen.findByRole('heading', { name: 'Bitcoin' })
    // Grab the (still-visible-in-the-DOM) table before the dialog opens: role queries against it
    // would otherwise fail while the dialog hides the rest of the page from the accessibility tree.
    const history = screen.getByRole('table', { name: 'Snapshot history' })

    await user.click(screen.getByRole('button', { name: 'Record snapshot' }))
    const dialog = within(await screen.findByRole('dialog', { name: 'Record snapshot' }))
    await user.clear(dialog.getByLabelText('Snapshot date'))
    await user.type(dialog.getByLabelText('Snapshot date'), '2026-09-01')
    await user.type(dialog.getByRole('spinbutton', { name: 'Balance' }), '1234.5')
    await user.click(dialog.getByRole('button', { name: 'Record snapshot' }))

    expect(await within(history).findByText(formatMoney(1234.5, 'en'))).toBeInTheDocument()
    expect(sent).toEqual({ date: '2026-09-01', balance: 1234.5 })
    // The dialog closes on success, restoring the page to the accessibility tree.
    await waitFor(() =>
      expect(screen.queryByRole('dialog', { name: 'Record snapshot' })).not.toBeInTheDocument(),
    )
    // Newest first.
    expect(within(history).getAllByRole('row')[1]).toHaveTextContent('2026-09-01')
  })

  it('a same-day snapshot replaces the earlier row instead of adding one', async () => {
    const user = userEvent.setup({ delay: null })
    renderDetail()
    await screen.findByRole('heading', { name: 'Bitcoin' })
    const history = screen.getByRole('table', { name: 'Snapshot history' })
    await within(history).findByText('800.00')

    await user.click(screen.getByRole('button', { name: 'Record snapshot' }))
    const dialog = within(await screen.findByRole('dialog', { name: 'Record snapshot' }))
    await user.clear(dialog.getByLabelText('Snapshot date'))
    await user.type(dialog.getByLabelText('Snapshot date'), seedBitcoinSnapshots[0].date)
    await user.type(dialog.getByRole('spinbutton', { name: 'Balance' }), '950')
    await user.click(dialog.getByRole('button', { name: 'Record snapshot' }))

    expect(await within(history).findByText('950.00')).toBeInTheDocument()
    expect(within(history).queryByText('900.00')).not.toBeInTheDocument()
    // The dialog closes on success, restoring the page to the accessibility tree.
    await waitFor(() =>
      expect(screen.queryByRole('dialog', { name: 'Record snapshot' })).not.toBeInTheDocument(),
    )
    expect(within(history).getAllByRole('row')).toHaveLength(3)
  })

  it('accepts a zero balance (a liquidated position) and rejects a negative one', async () => {
    const user = userEvent.setup({ delay: null })
    renderDetail()
    await screen.findByRole('heading', { name: 'Bitcoin' })
    await user.click(screen.getByRole('button', { name: 'Record snapshot' }))
    const dialog = within(await screen.findByRole('dialog', { name: 'Record snapshot' }))
    const balance = dialog.getByRole('spinbutton', { name: 'Balance' })
    const button = dialog.getByRole('button', { name: 'Record snapshot' })

    expect(button).toBeDisabled()
    await user.type(balance, '0')
    expect(button).toBeEnabled()
    await user.clear(balance)
    await user.type(balance, '-1')
    expect(button).toBeDisabled()
  })

  it('edits a snapshot date and balance in place', async () => {
    const user = userEvent.setup({ delay: null })
    let sent: unknown = null
    server.use(
      http.put('/api/investment-holdings/:id/snapshots/:snapshotId', async ({ request }) => {
        sent = await request.clone().json()
      }),
    )
    renderDetail()
    await screen.findByRole('heading', { name: 'Bitcoin' })
    const history = screen.getByRole('table', { name: 'Snapshot history' })
    await within(history).findByText('800.00')

    const row = within(history).getByText('2026-07-31').closest('tr')!
    await user.click(within(row).getByRole('button', { name: 'Edit snapshot' }))
    const date = within(row).getByLabelText('Edit snapshot date')
    await user.clear(date)
    await user.type(date, '2026-07-30')
    const balance = within(row).getByLabelText('Edit snapshot balance')
    await user.clear(balance)
    await user.type(balance, '810')
    await user.click(within(row).getByRole('button', { name: 'Save snapshot' }))

    expect(await within(history).findByText('810.00')).toBeInTheDocument()
    expect(within(history).getByText('2026-07-30')).toBeInTheDocument()
    expect(within(history).queryByText('2026-07-31')).not.toBeInTheDocument()
    expect(sent).toEqual({ date: '2026-07-30', balance: 810 })
    expect(within(history).queryByLabelText('Edit snapshot date')).not.toBeInTheDocument()
  })

  it('cancelling an edit leaves the row unchanged', async () => {
    const user = userEvent.setup({ delay: null })
    renderDetail()
    await screen.findByRole('heading', { name: 'Bitcoin' })
    const history = screen.getByRole('table', { name: 'Snapshot history' })
    const row = (await within(history).findByText('2026-07-31')).closest('tr')!

    await user.click(within(row).getByRole('button', { name: 'Edit snapshot' }))
    await user.click(within(row).getByRole('button', { name: 'Cancel' }))

    expect(within(history).getByText('800.00')).toBeInTheDocument()
    expect(within(history).queryByLabelText('Edit snapshot date')).not.toBeInTheDocument()
  })

  it('shows the conflict message when an edit is refused with 409 and keeps the row editable', async () => {
    const user = userEvent.setup({ delay: null })
    server.use(
      http.put('/api/investment-holdings/:id/snapshots/:snapshotId', () =>
        HttpResponse.json(
          { type: 'urn:my-finances:error:investment-snapshot-date-taken' },
          { status: 409 },
        ),
      ),
    )
    renderDetail()
    await screen.findByRole('heading', { name: 'Bitcoin' })
    const history = screen.getByRole('table', { name: 'Snapshot history' })
    const row = (await within(history).findByText('2026-07-31')).closest('tr')!

    await user.click(within(row).getByRole('button', { name: 'Edit snapshot' }))
    await user.click(within(row).getByRole('button', { name: 'Save snapshot' }))

    expect(await screen.findByText(/another snapshot already has that date/)).toBeInTheDocument()
    expect(within(row).getByLabelText('Edit snapshot date')).toBeInTheDocument()
  })

  it('deletes a snapshot after confirming and moves the current value back', async () => {
    const user = userEvent.setup({ delay: null })
    renderDetail()
    await screen.findByRole('heading', { name: 'Bitcoin' })
    const history = screen.getByRole('table', { name: 'Snapshot history' })
    const row = (await within(history).findByText('2026-08-05')).closest('tr')!

    await user.click(within(row).getByRole('button', { name: 'Delete snapshot' }))
    const dialog = await screen.findByRole('dialog', { name: 'Delete snapshot?' })
    await user.click(within(dialog).getByRole('button', { name: 'Delete' }))

    await waitFor(() => expect(within(history).queryByText('2026-08-05')).not.toBeInTheDocument())
    expect(within(history).getByText('2026-07-31')).toBeInTheDocument()
    expect(await screen.findByText('Latest snapshot 2026-07-31')).toBeInTheDocument()
  })

  it('does not delete when the confirmation is cancelled', async () => {
    const user = userEvent.setup({ delay: null })
    renderDetail()
    await screen.findByRole('heading', { name: 'Bitcoin' })
    const history = screen.getByRole('table', { name: 'Snapshot history' })
    const row = (await within(history).findByText('2026-08-05')).closest('tr')!

    await user.click(within(row).getByRole('button', { name: 'Delete snapshot' }))
    const dialog = await screen.findByRole('dialog', { name: 'Delete snapshot?' })
    await user.click(within(dialog).getByRole('button', { name: 'Cancel' }))

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(within(history).getByText('2026-08-05')).toBeInTheDocument()
  })

  it('shows the conflict message when a delete is refused with 409', async () => {
    const user = userEvent.setup({ delay: null })
    server.use(
      http.delete('/api/investment-holdings/:id/snapshots/:snapshotId', () =>
        HttpResponse.json(
          { type: 'urn:my-finances:error:investment-snapshot-closed-holding' },
          { status: 409 },
        ),
      ),
    )
    renderDetail()
    await screen.findByRole('heading', { name: 'Bitcoin' })
    const history = screen.getByRole('table', { name: 'Snapshot history' })
    const row = (await within(history).findByText('2026-08-05')).closest('tr')!

    await user.click(within(row).getByRole('button', { name: 'Delete snapshot' }))
    const dialog = await screen.findByRole('dialog', { name: 'Delete snapshot?' })
    await user.click(within(dialog).getByRole('button', { name: 'Delete' }))

    expect(await screen.findByText(/latest snapshot would no longer be zero/)).toBeInTheDocument()
    expect(within(history).getByText('2026-08-05')).toBeInTheDocument()
  })

  it('opens the transfer form as a Buy for this product and closes it after saving', async () => {
    const user = userEvent.setup({ delay: null })
    renderDetail()
    await screen.findByRole('heading', { name: 'Bitcoin' })

    await user.click(screen.getByRole('button', { name: 'Buy' }))
    const dialog = await screen.findByRole('dialog')
    expect(within(dialog).getByRole('textbox', { name: 'Description' })).toHaveValue('Buy Bitcoin')
    expect(within(dialog).getByRole('combobox', { name: 'Investment Account' })).toHaveTextContent(
      seedInvestmentAccount.name,
    )
    expect(await within(dialog).findByRole('combobox', { name: 'Product' })).toHaveTextContent(
      'Bitcoin',
    )

    await selectOption(user, 'Cash Account', seedAccounts[0].name, within(dialog))
    await user.type(within(dialog).getByRole('spinbutton', { name: 'Quantity' }), '1')
    await user.type(within(dialog).getByRole('spinbutton', { name: 'Unit price' }), '100')
    await user.click(within(dialog).getByRole('button', { name: 'Add' }))

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
  })

  it('opens the Sell form with the investment account on the SELL side and "Close this holding" available', async () => {
    const user = userEvent.setup({ delay: null })
    renderDetail()
    await screen.findByRole('heading', { name: 'Bitcoin' })

    await user.click(screen.getByRole('button', { name: 'Sell' }))

    const dialog = await screen.findByRole('dialog')
    expect(within(dialog).getByRole('combobox', { name: 'Investment Account' })).toHaveTextContent(
      seedInvestmentAccount.name,
    )
    expect(
      await within(dialog).findByRole('checkbox', { name: 'Close this holding' }),
    ).toBeInTheDocument()
  })

  it('disables Buy and Sell on a closed product', async () => {
    renderDetail('iprod-old')

    expect(await screen.findByRole('heading', { name: 'Old CDB' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Buy' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Sell' })).toBeDisabled()
  })

  it('shows an error for an unknown product', async () => {
    renderDetail('iprod-missing')

    expect(await screen.findByText('Investment product not found.')).toBeInTheDocument()
  })

  it('shows a loading skeleton only when the first fetch is slow, then the product', async () => {
    server.use(
      http.get('/api/investment-products/:id', async () => {
        await delay(400)
        return HttpResponse.json(bitcoin)
      }),
    )
    renderDetail()

    expect(await screen.findByRole('status', { name: 'Loading product' })).toBeInTheDocument()
    expect(await screen.findByRole('heading', { name: 'Bitcoin' })).toBeInTheDocument()
    expect(screen.queryByRole('status', { name: 'Loading product' })).not.toBeInTheDocument()
  })

  it('shows the failure message when the product cannot be loaded', async () => {
    server.use(
      http.get('/api/investment-products/:id', () => new HttpResponse(null, { status: 500 })),
    )
    renderDetail()

    expect(await screen.findByText(/Request failed with status 500/)).toBeInTheDocument()
  })
})

describe('InvestmentProductDetailPage responsive layout (F021)', () => {
  afterEach(restoreViewport)

  describe('mobile', () => {
    beforeEach(() => setViewportWidth(VIEWPORT.mobile))

    it('renders the monthly-values and trades tables as cards, but keeps the two-column snapshot history a table', async () => {
      renderDetail()
      await screen.findByRole('heading', { name: 'Bitcoin' })

      expect(screen.queryByRole('table', { name: 'Monthly values' })).not.toBeInTheDocument()
      const monthly = await screen.findByRole('list', { name: 'Monthly values' })
      expect(within(monthly).getByText('2026-08', { exact: false })).toBeInTheDocument()

      expect(screen.queryByRole('table', { name: 'Trades' })).not.toBeInTheDocument()
      const trades = await screen.findByRole('list', { name: 'Trades' })
      expect(within(trades).getByText(seedBitcoinBuyTransfer.date)).toBeInTheDocument()

      // Only two data columns (Date, Balance): stays a table even on mobile (F021's 1-2-column rule).
      expect(await screen.findByRole('table', { name: 'Snapshot history' })).toBeInTheDocument()
    })
  })

  describe('tablet', () => {
    beforeEach(() => setViewportWidth(VIEWPORT.tablet))

    it('keeps the trades table without the record-only columns, reachable through the row expander', async () => {
      const user = userEvent.setup({ delay: null })
      renderDetail()
      const trades = await screen.findByRole('table', { name: 'Trades' })

      expect(within(trades).getByRole('columnheader', { name: 'Quantity' })).toBeInTheDocument()
      expect(
        within(trades).queryByRole('columnheader', { name: 'Unit price' }),
      ).not.toBeInTheDocument()
      expect(
        within(trades).queryByRole('columnheader', { name: 'Resulting balance' }),
      ).not.toBeInTheDocument()

      const row = (await within(trades).findByText(seedBitcoinBuyTransfer.date)).closest('tr')!
      await user.click(within(row).getByRole('button', { name: 'Show details' }))
      // The unit price (100000) only appears in the expanded trade details, unlike the quantity
      // (0.01), which also shows up in the monthly-values table's Units column.
      expect(await screen.findByText('100000')).toBeInTheDocument()
    })
  })
})

describe('InvestmentProductDetailPage actions', () => {
  beforeEach(() => setViewportWidth(VIEWPORT.desktop))
  afterEach(restoreViewport)

  const secondBroker: Account = {
    ...seedInvestmentAccount,
    id: 'acct-inv-2',
    name: 'Second Broker',
  }

  /** The Selic product's only holding has no history, so it can be deleted, then the product. */
  async function deleteSelicHolding(user: ReturnType<typeof userEvent.setup>) {
    await screen.findByRole('heading', { name: 'Tesouro Selic 2029' })
    await user.click(await screen.findByRole('button', { name: 'Delete holding' }))
    const confirm = within(await screen.findByRole('dialog'))
    await user.click(confirm.getByRole('button', { name: 'Delete holding' }))
  }

  describe('product', () => {
    it('edits the name from the dialog and shows it in the heading', async () => {
      const user = userEvent.setup({ delay: null })
      renderDetail()
      await screen.findByRole('heading', { name: 'Bitcoin' })

      await user.click(screen.getByRole('button', { name: 'Edit product' }))
      const dialog = within(await screen.findByRole('dialog', { name: 'Edit product' }))
      const name = dialog.getByRole('textbox', { name: 'Product name' })
      expect(name).toHaveValue('Bitcoin')
      await user.clear(name)
      await user.type(name, 'Bitcoin Core')
      await user.click(dialog.getByRole('button', { name: 'Save' }))

      expect(await screen.findByRole('heading', { name: 'Bitcoin Core' })).toBeInTheDocument()
      await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    })

    it('keeps the edit dialog open and shows the error inside it when the save is rejected', async () => {
      const user = userEvent.setup({ delay: null })
      server.use(investmentProductEditConflictHandler)
      renderDetail()
      await screen.findByRole('heading', { name: 'Bitcoin' })

      await user.click(screen.getByRole('button', { name: 'Edit product' }))
      const dialog = within(await screen.findByRole('dialog', { name: 'Edit product' }))
      await user.click(dialog.getByRole('button', { name: 'Save' }))

      expect(
        await dialog.findByText(
          resolveErrorMessage('urn:my-finances:error:investment-product-name-already-exists'),
        ),
      ).toBeInTheDocument()
      expect(screen.getByRole('dialog', { name: 'Edit product' })).toBeInTheDocument()
    })

    it('hides Delete product while the product has a holding', async () => {
      renderDetail()
      await screen.findByRole('heading', { name: 'Bitcoin' })
      await screen.findByRole('button', { name: 'Close holding' })

      expect(screen.queryByRole('button', { name: 'Delete product' })).not.toBeInTheDocument()
    })

    it('deletes a product with no holdings after confirming, and returns to Accounts', async () => {
      const user = userEvent.setup({ delay: null })
      renderDetail('iprod-selic')
      await deleteSelicHolding(user)

      await user.click(await screen.findByRole('button', { name: 'Delete product' }))
      const confirm = within(await screen.findByRole('dialog'))
      expect(confirm.getByText('Delete Tesouro Selic 2029?')).toBeInTheDocument()
      await user.click(confirm.getByRole('button', { name: 'Delete product' }))

      expect(await screen.findByText('Accounts page')).toBeInTheDocument()
    })

    it('shows the error on the page and stays put when the product delete is rejected', async () => {
      const user = userEvent.setup({ delay: null })
      server.use(investmentProductDeleteConflictHandler)
      renderDetail('iprod-selic')
      await deleteSelicHolding(user)

      await user.click(await screen.findByRole('button', { name: 'Delete product' }))
      const confirm = within(await screen.findByRole('dialog'))
      await user.click(confirm.getByRole('button', { name: 'Delete product' }))

      expect(
        await screen.findByText(
          resolveErrorMessage('urn:my-finances:error:investment-product-has-holdings'),
        ),
      ).toBeInTheDocument()
      // The confirm dialog closes on failure, restoring the page to the accessibility tree.
      await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
      expect(screen.getByRole('heading', { name: 'Tesouro Selic 2029' })).toBeInTheDocument()
      expect(screen.queryByText('Accounts page')).not.toBeInTheDocument()
    })
  })

  describe('holdings', () => {
    it('disables Add holding when every investment account already holds the product', async () => {
      renderDetail()
      await screen.findByRole('button', { name: 'Close holding' })

      expect(screen.getByRole('button', { name: 'Add holding' })).toBeDisabled()
    })

    it('adds a holding in another investment account and lists it', async () => {
      const user = userEvent.setup({ delay: null })
      renderDetail('iprod-btc', [secondBroker])
      await screen.findByRole('button', { name: 'Close holding' })
      const holdings = screen.getByRole('table', { name: 'Holdings' })

      await user.click(screen.getByRole('button', { name: 'Add holding' }))
      const dialog = within(await screen.findByRole('dialog', { name: 'Add holding' }))
      // The submit button stays disabled until an account is picked.
      expect(dialog.getByRole('button', { name: 'Add holding' })).toBeDisabled()
      await selectOption(user, 'Account', 'Second Broker', dialog)
      await user.click(dialog.getByRole('button', { name: 'Add holding' }))

      expect(await within(holdings).findByText('Second Broker')).toBeInTheDocument()
      await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    })

    it('keeps the add dialog open and shows the error inside it when the holding is rejected', async () => {
      const user = userEvent.setup({ delay: null })
      server.use(investmentHoldingCreateConflictHandler)
      renderDetail('iprod-btc', [secondBroker])
      await screen.findByRole('button', { name: 'Close holding' })

      await user.click(screen.getByRole('button', { name: 'Add holding' }))
      const dialog = within(await screen.findByRole('dialog', { name: 'Add holding' }))
      await selectOption(user, 'Account', 'Second Broker', dialog)
      await user.click(dialog.getByRole('button', { name: 'Add holding' }))

      expect(
        await dialog.findByText(
          resolveErrorMessage('urn:my-finances:error:investment-holding-already-exists'),
        ),
      ).toBeInTheDocument()
      expect(screen.getByRole('dialog', { name: 'Add holding' })).toBeInTheDocument()
    })

    it('closes a holding after confirming and marks it Closed', async () => {
      const user = userEvent.setup({ delay: null })
      renderDetail()
      const holdings = await screen.findByRole('table', { name: 'Holdings' })
      expect(await within(holdings).findByText('Open')).toBeInTheDocument()

      await user.click(within(holdings).getByRole('button', { name: 'Close holding' }))
      const confirm = within(await screen.findByRole('dialog'))
      expect(
        confirm.getByText(`Close this holding at ${seedInvestmentAccount.name}?`),
      ).toBeInTheDocument()
      await user.click(confirm.getByRole('button', { name: 'Close holding' }))

      expect(await within(holdings).findByText('Closed')).toBeInTheDocument()
      await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
      expect(within(holdings).getByRole('button', { name: 'Close holding' })).toBeDisabled()
    })

    it('shows the error on the page and leaves the holding open when the close is rejected', async () => {
      const user = userEvent.setup({ delay: null })
      server.use(investmentHoldingCloseConflictHandler)
      renderDetail()
      const holdings = await screen.findByRole('table', { name: 'Holdings' })
      await within(holdings).findByText('Open')

      await user.click(within(holdings).getByRole('button', { name: 'Close holding' }))
      const confirm = within(await screen.findByRole('dialog'))
      await user.click(confirm.getByRole('button', { name: 'Close holding' }))

      expect(
        await screen.findByText(
          resolveErrorMessage('urn:my-finances:error:investment-holding-already-closed'),
        ),
      ).toBeInTheDocument()
      expect(within(holdings).getByText('Open')).toBeInTheDocument()
    })

    it('hides Delete holding for a holding with history', async () => {
      renderDetail()
      await screen.findByRole('button', { name: 'Close holding' })

      expect(screen.queryByRole('button', { name: 'Delete holding' })).not.toBeInTheDocument()
    })

    it('deletes a holding with no history after confirming', async () => {
      const user = userEvent.setup({ delay: null })
      renderDetail('iprod-selic')
      const holdings = await screen.findByRole('table', { name: 'Holdings' })

      await deleteSelicHolding(user)

      expect(await within(holdings).findByText('No holdings yet.')).toBeInTheDocument()
    })

    it('shows the error on the page and keeps the holding when the delete is rejected', async () => {
      const user = userEvent.setup({ delay: null })
      server.use(investmentHoldingDeleteConflictHandler)
      renderDetail('iprod-selic')
      const holdings = await screen.findByRole('table', { name: 'Holdings' })

      await deleteSelicHolding(user)

      expect(
        await screen.findByText(
          resolveErrorMessage('urn:my-finances:error:investment-holding-has-history'),
        ),
      ).toBeInTheDocument()
      expect(within(holdings).getByText('Open')).toBeInTheDocument()
    })

    it('edits a holding note and shows it again when reopened', async () => {
      const user = userEvent.setup({ delay: null })
      renderDetail()
      await screen.findByRole('button', { name: 'Edit notes' })

      await user.click(screen.getByRole('button', { name: 'Edit notes' }))
      const dialog = within(await screen.findByRole('dialog', { name: 'Edit holding notes' }))
      await user.type(dialog.getByRole('textbox', { name: 'Additional notes' }), 'Long term')
      await user.click(dialog.getByRole('button', { name: 'Save' }))
      await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())

      await user.click(screen.getByRole('button', { name: 'Edit notes' }))
      const reopened = within(await screen.findByRole('dialog', { name: 'Edit holding notes' }))
      expect(reopened.getByRole('textbox', { name: 'Additional notes' })).toHaveValue('Long term')
      await user.click(reopened.getByRole('button', { name: 'Cancel' }))
      await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    })
  })

  describe('snapshot dialog', () => {
    it('closes without recording when cancelled', async () => {
      const user = userEvent.setup({ delay: null })
      renderDetail()
      await screen.findByRole('heading', { name: 'Bitcoin' })

      await user.click(screen.getByRole('button', { name: 'Record snapshot' }))
      const dialog = within(await screen.findByRole('dialog', { name: 'Record snapshot' }))
      await user.click(dialog.getByRole('button', { name: 'Cancel' }))

      await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    })

    it('keeps the dialog open and shows the error inside it when recording fails', async () => {
      const user = userEvent.setup({ delay: null })
      server.use(
        http.post(
          '/api/investment-holdings/:id/snapshots',
          () => new HttpResponse(null, { status: 500 }),
        ),
      )
      renderDetail()
      await screen.findByRole('heading', { name: 'Bitcoin' })

      await user.click(screen.getByRole('button', { name: 'Record snapshot' }))
      const dialog = within(await screen.findByRole('dialog', { name: 'Record snapshot' }))
      await user.type(dialog.getByRole('spinbutton', { name: 'Balance' }), '10')
      await user.click(dialog.getByRole('button', { name: 'Record snapshot' }))

      expect(await dialog.findByText(/Request failed with status 500/)).toBeInTheDocument()
      expect(screen.getByRole('dialog', { name: 'Record snapshot' })).toBeInTheDocument()
    })
  })
})
