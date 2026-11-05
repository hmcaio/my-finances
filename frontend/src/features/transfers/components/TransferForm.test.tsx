import { describe, expect, it, vi } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { server } from '../../../mocks/server'
import { seedAccounts, seedInvestmentAccount } from '../../../mocks/handlers/accounts'
import { seedBitcoinBuyTransfer, seedBitcoinSellTransfer } from '../../../mocks/handlers/transfers'
import { resolveErrorMessage } from '../../../test/utils/errorMessages'
import { selectOption } from '../../../test/utils/testUtils'
import { formatMoney } from '../../../i18n/format'
import { TransferForm, type TransferFormPreset } from './TransferForm'
import { renderWithQueryClient } from '../../../test/utils/renderWithQueryClient'

const accounts = [...seedAccounts, seedInvestmentAccount]
const CHECKING = seedAccounts[0].name
const BROKER = seedInvestmentAccount.name

function renderForm(
  props: Partial<Parameters<typeof TransferForm>[0]> = {},
): ReturnType<typeof vi.fn> {
  const onSaved = vi.fn()
  renderWithQueryClient(
    <TransferForm accounts={accounts} onSaved={onSaved} onError={vi.fn()} {...props} />,
  )
  return onSaved
}

/** Captures the body of the next `POST /api/transfers`, answering like the default handler. */
function captureCreate(): { body: () => Record<string, unknown> | null } {
  let sent: Record<string, unknown> | null = null
  server.use(
    http.post('/api/transfers', async ({ request }) => {
      sent = (await request.json()) as Record<string, unknown>
      return HttpResponse.json(seedBitcoinBuyTransfer, { status: 201 })
    }),
  )
  return { body: () => sent }
}

async function openTradeMode(
  user: ReturnType<typeof userEvent.setup>,
  cash = CHECKING,
  investment = BROKER,
) {
  await user.click(screen.getByRole('button', { name: 'Trade confirmation' }))
  await selectOption(user, 'Cash Account', cash)
  await selectOption(user, 'Investment Account', investment)
}

async function pickBuyLine(user: ReturnType<typeof userEvent.setup>, product = 'Bitcoin') {
  await openTradeMode(user)
  await selectOption(user, 'Product', product)
}

describe('TransferForm plain/trade toggle', () => {
  it('starts in Plain transfer mode with no trade fields', () => {
    renderForm()

    expect(screen.getByRole('button', { name: 'Plain transfer' })).toHaveAttribute(
      'aria-pressed',
      'true',
    )
    expect(screen.queryByRole('group', { name: 'Trade lines' })).not.toBeInTheDocument()
    expect(screen.getByRole('combobox', { name: 'From Account' })).toBeInTheDocument()
  })

  it('switching to Trade confirmation shows the cash/investment accounts and one blank line', async () => {
    const user = userEvent.setup({ delay: null })
    renderForm()

    await user.click(screen.getByRole('button', { name: 'Trade confirmation' }))

    expect(screen.getByRole('combobox', { name: 'Cash Account' })).toBeInTheDocument()
    expect(screen.getByRole('combobox', { name: 'Investment Account' })).toBeInTheDocument()
    expect(screen.getByRole('group', { name: 'Trade lines' })).toBeInTheDocument()
    expect(screen.getByRole('combobox', { name: 'Product' })).toBeInTheDocument()
  })

  it('the Cash Account select never offers an INVESTMENT account', async () => {
    const user = userEvent.setup({ delay: null })
    renderForm()
    await user.click(screen.getByRole('button', { name: 'Trade confirmation' }))

    await user.click(screen.getByRole('combobox', { name: 'Cash Account' }))

    expect(await screen.findByRole('option', { name: CHECKING })).toBeInTheDocument()
    expect(screen.queryByRole('option', { name: BROKER })).not.toBeInTheDocument()
  })

  it('the From/To Account selects never offer an INVESTMENT account in plain mode', async () => {
    const user = userEvent.setup({ delay: null })
    renderForm()

    await user.click(screen.getByRole('combobox', { name: 'From Account' }))
    expect(await screen.findByRole('option', { name: CHECKING })).toBeInTheDocument()
    expect(screen.queryByRole('option', { name: BROKER })).not.toBeInTheDocument()
    await user.click(screen.getByRole('option', { name: CHECKING }))

    await user.click(screen.getByRole('combobox', { name: 'To Account' }))
    expect(await screen.findByRole('option', { name: seedAccounts[2].name })).toBeInTheDocument()
    expect(screen.queryByRole('option', { name: BROKER })).not.toBeInTheDocument()
  })

  it('switching to Plain transfer while editing a trade confirmation drops the investment account', async () => {
    const user = userEvent.setup({ delay: null })
    renderForm({ editing: seedBitcoinBuyTransfer })

    await user.click(screen.getByRole('button', { name: 'Plain transfer' }))

    // seedBitcoinBuyTransfer's toAccountId is the INVESTMENT account (the trade's buy side) - it
    // must not survive into the plain shape's To Account, while the unaffected From Account (the
    // cash side, non-investment) keeps its value.
    expect(screen.getByRole('combobox', { name: 'From Account' })).toHaveTextContent(CHECKING)
    expect(screen.getByRole('combobox', { name: 'To Account' })).not.toHaveTextContent(BROKER)
  })
})

describe('TransferForm trade confirmations', () => {
  it('shows a product select with only the open holdings of the picked investment account', async () => {
    const user = userEvent.setup({ delay: null })
    renderForm()
    await openTradeMode(user)

    await user.click(screen.getByRole('combobox', { name: 'Product' }))

    expect(await screen.findByRole('option', { name: 'Bitcoin' })).toBeInTheDocument()
    expect(screen.getByRole('option', { name: 'Tesouro Selic 2029' })).toBeInTheDocument()
    // "Old CDB" is closed: a closed holding takes no new line.
    expect(screen.queryByRole('option', { name: 'Old CDB' })).not.toBeInTheDocument()
  })

  it('previews the net settlement of a single BUY line, cash into the investment account', async () => {
    const user = userEvent.setup({ delay: null })
    renderForm()
    await pickBuyLine(user)

    await user.type(screen.getByRole('spinbutton', { name: 'Quantity' }), '10')
    await user.type(screen.getByRole('spinbutton', { name: 'Unit price' }), '100')
    await user.clear(screen.getByRole('spinbutton', { name: 'Taxes' }))
    await user.type(screen.getByRole('spinbutton', { name: 'Taxes' }), '5')

    expect(
      screen.getByText(`Net settlement: ${formatMoney(1005, 'en')} (${CHECKING} → ${BROKER})`),
    ).toBeInTheDocument()
  })

  it('previews the net settlement of a single SELL line, the investment account into cash', async () => {
    const user = userEvent.setup({ delay: null })
    renderForm()
    await pickBuyLine(user)
    await selectOption(user, 'Side', 'Sell')

    await user.type(screen.getByRole('spinbutton', { name: 'Quantity' }), '10')
    await user.type(screen.getByRole('spinbutton', { name: 'Unit price' }), '100')
    await user.clear(screen.getByRole('spinbutton', { name: 'Taxes' }))
    await user.type(screen.getByRole('spinbutton', { name: 'Taxes' }), '5')

    expect(screen.getByText(`Net settlement: 995.00 (${BROKER} → ${CHECKING})`)).toBeInTheDocument()
  })

  it('offers "Close this holding" only on a SELL line', async () => {
    const user = userEvent.setup({ delay: null })
    renderForm()
    await pickBuyLine(user)

    expect(screen.queryByRole('checkbox', { name: 'Close this holding' })).not.toBeInTheDocument()

    await selectOption(user, 'Side', 'Sell')

    expect(screen.getByRole('checkbox', { name: 'Close this holding' })).toBeInTheDocument()
  })

  it('adds and removes lines', async () => {
    const user = userEvent.setup({ delay: null })
    renderForm()
    await openTradeMode(user)

    await user.click(screen.getByRole('button', { name: 'Add line' }))

    expect(screen.getAllByRole('combobox', { name: 'Product' })).toHaveLength(2)

    await user.click(screen.getAllByRole('button', { name: 'Remove line' })[0])

    expect(screen.getAllByRole('combobox', { name: 'Product' })).toHaveLength(1)
  })

  it('creates a single-line buy with the derived amount and direction', async () => {
    const user = userEvent.setup({ delay: null })
    const capture = captureCreate()
    const onSaved = renderForm()
    await pickBuyLine(user)
    await user.type(screen.getByRole('spinbutton', { name: 'Quantity' }), '10')
    await user.type(screen.getByRole('spinbutton', { name: 'Unit price' }), '100')
    await user.clear(screen.getByRole('spinbutton', { name: 'Taxes' }))
    await user.type(screen.getByRole('spinbutton', { name: 'Taxes' }), '5')
    await user.type(screen.getByRole('spinbutton', { name: 'Resulting balance' }), '2000')
    await user.type(screen.getByRole('textbox', { name: 'Description' }), 'Buy order')
    await user.click(screen.getByRole('button', { name: 'Add' }))

    await waitFor(() => expect(onSaved).toHaveBeenCalled())
    expect(capture.body()).toMatchObject({
      cashAccountId: seedAccounts[0].id,
      investmentAccountId: seedInvestmentAccount.id,
      tradeConfirmation: {
        taxes: 5,
        lines: [
          {
            productId: 'iprod-btc',
            side: 'BUY',
            quantity: 10,
            unitPrice: 100,
            resultingBalance: 2000,
            closeHolding: false,
          },
        ],
      },
    })
    expect(capture.body()).not.toHaveProperty('amount')
    expect(capture.body()).not.toHaveProperty('fromAccountId')
  })

  it('sends no resultingBalance when the field is left blank', async () => {
    const user = userEvent.setup({ delay: null })
    const capture = captureCreate()
    const onSaved = renderForm()
    await pickBuyLine(user)
    await user.type(screen.getByRole('spinbutton', { name: 'Quantity' }), '10')
    await user.type(screen.getByRole('spinbutton', { name: 'Unit price' }), '100')
    await user.type(screen.getByRole('textbox', { name: 'Description' }), 'Top up')
    await user.click(screen.getByRole('button', { name: 'Add' }))

    await waitFor(() => expect(onSaved).toHaveBeenCalled())
    const lines = capture.body()!.tradeConfirmation as { lines: Record<string, unknown>[] }
    expect(lines.lines[0]).not.toHaveProperty('resultingBalance')
  })

  it('requires every line to have a product, positive quantity and unit price', async () => {
    const user = userEvent.setup({ delay: null })
    renderForm()
    await openTradeMode(user)
    await user.type(screen.getByRole('textbox', { name: 'Description' }), 'x')

    expect(screen.getByRole('button', { name: 'Add' })).toBeDisabled()

    await selectOption(user, 'Product', 'Bitcoin')
    await user.type(screen.getByRole('spinbutton', { name: 'Quantity' }), '1')
    expect(screen.getByRole('button', { name: 'Add' })).toBeDisabled()

    await user.type(screen.getByRole('spinbutton', { name: 'Unit price' }), '1')
    expect(screen.getByRole('button', { name: 'Add' })).toBeEnabled()
  })

  it('rejects a net-zero settlement client-side (Add stays disabled)', async () => {
    const user = userEvent.setup({ delay: null })
    renderForm()
    await openTradeMode(user)
    await user.click(screen.getByRole('button', { name: 'Add line' }))
    await user.type(screen.getByRole('textbox', { name: 'Description' }), 'Wash')

    const productSelects = screen.getAllByRole('combobox', { name: 'Product' })
    await user.click(productSelects[0])
    await user.click(await screen.findByRole('option', { name: 'Bitcoin' }))
    await user.click(productSelects[1])
    await user.click(await screen.findByRole('option', { name: 'Tesouro Selic 2029' }))
    const sideSelects = screen.getAllByRole('combobox', { name: 'Side' })
    await user.click(sideSelects[1])
    await user.click(await screen.findByRole('option', { name: 'Sell' }))

    const quantities = screen.getAllByRole('spinbutton', { name: 'Quantity' })
    const unitPrices = screen.getAllByRole('spinbutton', { name: 'Unit price' })
    await user.type(quantities[0], '10')
    await user.type(unitPrices[0], '100')
    await user.type(quantities[1], '10')
    await user.type(unitPrices[1], '100')
    await user.clear(screen.getByRole('spinbutton', { name: 'Taxes' }))
    await user.type(screen.getByRole('spinbutton', { name: 'Taxes' }), '0')

    expect(
      screen.getByText(
        'This settlement nets to exactly zero and will be rejected - check the lines.',
      ),
    ).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Add' })).toBeDisabled()
  })

  it('surfaces the conflict message when the backend rejects the trade', async () => {
    const user = userEvent.setup({ delay: null })
    server.use(
      http.post('/api/transfers', () =>
        HttpResponse.json({ type: 'urn:my-finances:error:account-closed' }, { status: 409 }),
      ),
    )
    const onError = vi.fn()
    renderForm({ onError })
    await pickBuyLine(user)
    await user.type(screen.getByRole('spinbutton', { name: 'Quantity' }), '1')
    await user.type(screen.getByRole('spinbutton', { name: 'Unit price' }), '10')
    await user.type(screen.getByRole('textbox', { name: 'Description' }), 'x')
    await user.click(screen.getByRole('button', { name: 'Add' }))

    await waitFor(() =>
      expect(onError).toHaveBeenCalledWith(
        resolveErrorMessage('urn:my-finances:error:account-closed'),
      ),
    )
  })

  it('starts from a Buy preset: trade mode, investment account, product and description filled in', async () => {
    const preset: TransferFormPreset = {
      direction: 'buy',
      investmentAccountId: seedInvestmentAccount.id,
      productId: 'iprod-btc',
      productName: 'Bitcoin',
    }
    renderForm({ preset })

    expect(screen.getByRole('textbox', { name: 'Description' })).toHaveValue('Buy Bitcoin')
    expect(screen.getByRole('combobox', { name: 'Investment Account' })).toHaveTextContent(BROKER)
    // The product list arrives after the select first renders.
    await waitFor(() =>
      expect(screen.getByRole('combobox', { name: 'Product' })).toHaveTextContent('Bitcoin'),
    )
  })

  it('starts from a Sell preset with "Close this holding" available', async () => {
    renderForm({
      preset: {
        direction: 'sell',
        investmentAccountId: seedInvestmentAccount.id,
        productId: 'iprod-btc',
        productName: 'Bitcoin',
      },
    })

    expect(screen.getByRole('combobox', { name: 'Investment Account' })).toHaveTextContent(BROKER)
    expect(await screen.findByRole('checkbox', { name: 'Close this holding' })).toBeInTheDocument()
  })

  it('edits a trade confirmation, prefilling its line and sending the new shape', async () => {
    const user = userEvent.setup({ delay: null })
    let sent: Record<string, unknown> | null = null
    server.use(
      http.patch('/api/transfers/:id', async ({ request }) => {
        sent = (await request.json()) as Record<string, unknown>
        return HttpResponse.json(seedBitcoinBuyTransfer)
      }),
    )
    const onSaved = renderForm({ editing: seedBitcoinBuyTransfer })

    // The product list arrives after the select first renders.
    await waitFor(() =>
      expect(screen.getByRole('combobox', { name: 'Product' })).toHaveTextContent('Bitcoin'),
    )
    expect(screen.getByRole('spinbutton', { name: 'Quantity' })).toHaveValue(0.01)
    expect(screen.getByRole('spinbutton', { name: 'Unit price' })).toHaveValue(100000)

    await user.click(screen.getByRole('button', { name: 'Save changes' }))

    await waitFor(() => expect(onSaved).toHaveBeenCalled())
    expect(sent).toMatchObject({
      cashAccountId: seedBitcoinBuyTransfer.fromAccountId,
      investmentAccountId: seedBitcoinBuyTransfer.toAccountId,
      tradeConfirmation: { taxes: 5, lines: [{ productId: 'iprod-btc', quantity: 0.01 }] },
    })
  })
})

describe('TransferForm plain transfers', () => {
  it('leaves a plain transfer without any trade fields', async () => {
    const user = userEvent.setup({ delay: null })
    const capture = captureCreate()
    const onSaved = renderForm()
    await selectOption(user, 'From Account', CHECKING)
    await selectOption(user, 'To Account', seedAccounts[2].name)
    await user.type(screen.getByRole('spinbutton', { name: 'Amount' }), '25')
    await user.type(screen.getByRole('textbox', { name: 'Description' }), 'Card payment')

    expect(screen.queryByRole('group', { name: 'Trade lines' })).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Add' }))

    await waitFor(() => expect(onSaved).toHaveBeenCalled())
    const sent = capture.body()!
    expect(Object.keys(sent).sort()).toEqual(
      ['amount', 'date', 'description', 'fromAccountId', 'toAccountId'].sort(),
    )
    expect(within(document.body).queryByText(/Trade lines/)).not.toBeInTheDocument()
  })

  it('editing a plain transfer prefills its fields and mode', () => {
    renderForm({
      editing: {
        id: 'trf-1',
        date: '2026-01-10',
        fromAccountId: seedAccounts[0].id,
        toAccountId: seedAccounts[2].id,
        amount: 200,
        description: 'Credit card payment',
        additionalNotes: null,
        taxes: null,
        tradeConfirmation: null,
      },
    })

    expect(screen.getByRole('button', { name: 'Plain transfer' })).toHaveAttribute(
      'aria-pressed',
      'true',
    )
    expect(screen.getByRole('spinbutton', { name: 'Amount' })).toHaveValue(200)
  })
})

describe('TransferForm characterization', () => {
  const NUBANK = seedAccounts[2].name

  async function fillBuyLine(
    user: ReturnType<typeof userEvent.setup>,
    index: number,
    resultingBalance?: string,
  ) {
    await user.type(screen.getAllByRole('spinbutton', { name: 'Quantity' })[index], '1')
    await user.type(screen.getAllByRole('spinbutton', { name: 'Unit price' })[index], '10')
    if (resultingBalance !== undefined) {
      await user.type(
        screen.getAllByRole('spinbutton', { name: 'Resulting balance' })[index],
        resultingBalance,
      )
    }
  }

  it('blocks a plain transfer whose From and To accounts are the same', async () => {
    const user = userEvent.setup({ delay: null })
    renderForm()
    await selectOption(user, 'From Account', CHECKING)

    // The To picker never offers the picked From account.
    await user.click(screen.getByRole('combobox', { name: 'To Account' }))
    expect(await screen.findByRole('option', { name: NUBANK })).toBeInTheDocument()
    expect(screen.queryByRole('option', { name: CHECKING })).not.toBeInTheDocument()
    await user.click(screen.getByRole('option', { name: NUBANK }))
    await user.type(screen.getByRole('spinbutton', { name: 'Amount' }), '25')
    await user.type(screen.getByRole('textbox', { name: 'Description' }), 'Pay')
    expect(screen.getByRole('button', { name: 'Add' })).toBeEnabled()

    // Changing From to the account already picked as To leaves both equal: Add is blocked.
    await selectOption(user, 'From Account', NUBANK)

    expect(screen.getByRole('button', { name: 'Add' })).toBeDisabled()
  })

  it('blocks two lines of the same product that both set a resulting balance', async () => {
    const user = userEvent.setup({ delay: null })
    renderForm()
    await openTradeMode(user)
    await user.click(screen.getByRole('button', { name: 'Add line' }))
    await user.type(screen.getByRole('textbox', { name: 'Description' }), 'Twice')
    for (const combobox of screen.getAllByRole('combobox', { name: 'Product' })) {
      await user.click(combobox)
      await user.click(await screen.findByRole('option', { name: 'Bitcoin' }))
    }
    await fillBuyLine(user, 0, '100')
    await fillBuyLine(user, 1)
    expect(screen.getByRole('button', { name: 'Add' })).toBeEnabled()

    await user.type(screen.getAllByRole('spinbutton', { name: 'Resulting balance' })[1], '200')

    const helper = 'Only one line per product can set a resulting balance - leave it blank here.'
    expect(screen.getAllByText(helper)).toHaveLength(2)
    expect(screen.getByRole('button', { name: 'Add' })).toBeDisabled()

    await user.clear(screen.getAllByRole('spinbutton', { name: 'Resulting balance' })[1])

    expect(screen.queryByText(helper)).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Add' })).toBeEnabled()
  })

  it('drops every line product when the investment account changes', async () => {
    const user = userEvent.setup({ delay: null })
    const otherBroker = { ...seedInvestmentAccount, id: 'acct-inv-2', name: 'Other Broker' }
    renderForm({ accounts: [...accounts, otherBroker] })
    await pickBuyLine(user)
    expect(screen.getByRole('combobox', { name: 'Product' })).toHaveTextContent('Bitcoin')

    await selectOption(user, 'Investment Account', 'Other Broker')
    expect(screen.getByRole('combobox', { name: 'Product' })).not.toHaveTextContent('Bitcoin')

    // Back to the first account the pick is gone, not restored.
    await selectOption(user, 'Investment Account', BROKER)
    expect(screen.getByRole('combobox', { name: 'Product' })).not.toHaveTextContent('Bitcoin')
  })

  it('keeps a closed holding selectable on the line that already holds it', async () => {
    const closedHoldingTrade = {
      ...seedBitcoinBuyTransfer,
      tradeConfirmation: {
        lines: [{ ...seedBitcoinBuyTransfer.tradeConfirmation!.lines[0], productId: 'iprod-old' }],
      },
    }
    const user = userEvent.setup({ delay: null })
    renderForm({ editing: closedHoldingTrade })

    await waitFor(() =>
      expect(screen.getByRole('combobox', { name: 'Product' })).toHaveTextContent('Old CDB'),
    )
    await user.click(screen.getByRole('combobox', { name: 'Product' }))
    expect(await screen.findByRole('option', { name: 'Old CDB' })).toBeInTheDocument()
    expect(screen.getByRole('option', { name: 'Bitcoin' })).toBeInTheDocument()
  })

  it('asks for every line quantity and unit price before showing a preview', async () => {
    const user = userEvent.setup({ delay: null })
    renderForm()
    await openTradeMode(user)

    expect(
      screen.getByText('Enter every line’s quantity and unit price for a settlement preview.'),
    ).toBeInTheDocument()
  })

  it('clears "Close this holding" when the side goes back to Buy', async () => {
    const user = userEvent.setup({ delay: null })
    renderForm()
    await pickBuyLine(user)
    await selectOption(user, 'Side', 'Sell')
    await user.click(screen.getByRole('checkbox', { name: 'Close this holding' }))
    expect(screen.getByRole('checkbox', { name: 'Close this holding' })).toBeChecked()

    await selectOption(user, 'Side', 'Buy')
    await selectOption(user, 'Side', 'Sell')

    expect(screen.getByRole('checkbox', { name: 'Close this holding' })).not.toBeChecked()
  })

  it('derives the cash and investment accounts of an edited sell (investment on the from side)', async () => {
    const user = userEvent.setup({ delay: null })
    let sent: Record<string, unknown> | null = null
    server.use(
      http.patch('/api/transfers/:id', async ({ request }) => {
        sent = (await request.json()) as Record<string, unknown>
        return HttpResponse.json(seedBitcoinSellTransfer)
      }),
    )
    const onSaved = renderForm({ editing: seedBitcoinSellTransfer })

    expect(screen.getByRole('combobox', { name: 'Cash Account' })).toHaveTextContent(CHECKING)
    expect(screen.getByRole('combobox', { name: 'Investment Account' })).toHaveTextContent(BROKER)
    await waitFor(() =>
      expect(screen.getByRole('combobox', { name: 'Product' })).toHaveTextContent('Bitcoin'),
    )
    await user.click(screen.getByRole('button', { name: 'Save changes' }))

    await waitFor(() => expect(onSaved).toHaveBeenCalled())
    expect(sent).toMatchObject({
      cashAccountId: seedBitcoinSellTransfer.toAccountId,
      investmentAccountId: seedBitcoinSellTransfer.fromAccountId,
    })
  })

  it('edits a plain transfer through the edit endpoint with the plain shape', async () => {
    const user = userEvent.setup({ delay: null })
    let sent: Record<string, unknown> | null = null
    const editing = {
      id: 'trf-1',
      date: '2026-01-10',
      fromAccountId: seedAccounts[0].id,
      toAccountId: seedAccounts[2].id,
      amount: 200,
      description: 'Credit card payment',
      additionalNotes: 'note',
      taxes: null,
      tradeConfirmation: null,
    }
    server.use(
      http.patch('/api/transfers/:id', async ({ request }) => {
        sent = (await request.json()) as Record<string, unknown>
        return HttpResponse.json(editing)
      }),
    )
    const onSaved = renderForm({ editing })

    await user.clear(screen.getByRole('spinbutton', { name: 'Amount' }))
    await user.type(screen.getByRole('spinbutton', { name: 'Amount' }), '250')
    await user.click(screen.getByRole('button', { name: 'Save changes' }))

    await waitFor(() => expect(onSaved).toHaveBeenCalled())
    expect(sent).toEqual({
      date: '2026-01-10',
      fromAccountId: seedAccounts[0].id,
      toAccountId: seedAccounts[2].id,
      amount: 250,
      description: 'Credit card payment',
      additionalNotes: 'note',
    })
  })

  it('reports null through onError when a save attempt starts', async () => {
    const user = userEvent.setup({ delay: null })
    const onError = vi.fn()
    captureCreate()
    const onSaved = renderForm({ onError })
    await selectOption(user, 'From Account', CHECKING)
    await selectOption(user, 'To Account', NUBANK)
    await user.type(screen.getByRole('spinbutton', { name: 'Amount' }), '25')
    await user.type(screen.getByRole('textbox', { name: 'Description' }), 'Pay')
    await user.click(screen.getByRole('button', { name: 'Add' }))

    await waitFor(() => expect(onSaved).toHaveBeenCalled())
    expect(onError).toHaveBeenCalledTimes(1)
    expect(onError).toHaveBeenCalledWith(null)
  })

  it('shows Cancel only with onCancel and wires it', async () => {
    const user = userEvent.setup({ delay: null })
    const onCancel = vi.fn()
    renderForm()
    expect(screen.queryByRole('button', { name: 'Cancel' })).not.toBeInTheDocument()
    renderForm({ onCancel })

    await user.click(screen.getByRole('button', { name: 'Cancel' }))

    expect(onCancel).toHaveBeenCalledTimes(1)
  })

  it('renders the caller banner in dialog mode', () => {
    renderForm({ dialog: true, banner: <p>Banner from the caller</p>, onCancel: vi.fn() })

    expect(screen.getByText('Banner from the caller')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Cancel' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Add' })).toBeDisabled()
  })
})
