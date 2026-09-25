import { describe, expect, it, vi } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { server } from '../../mocks/server'
import { seedAccounts, seedInvestmentAccount } from '../../mocks/handlers/accounts'
import { seedBitcoinBuyTransfer } from '../../mocks/handlers/transfers'
import { TRANSFER_CONFLICT_MESSAGE } from '../../api/transfers'
import { selectOption } from '../../test/testUtils'
import { TransferForm, type TransferFormPreset } from './TransferForm'
import { renderWithQueryClient } from '../../test/renderWithQueryClient'

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

async function pickBuy(user: ReturnType<typeof userEvent.setup>, product = 'Bitcoin') {
  await selectOption(user, 'From Account', CHECKING)
  await selectOption(user, 'To Account', BROKER)
  await selectOption(user, 'Product', product)
}

describe('TransferForm buys and sells', () => {
  it('shows a product select with only the open products once an investment account is picked', async () => {
    const user = userEvent.setup()
    renderForm()
    expect(screen.queryByRole('combobox', { name: 'Product' })).not.toBeInTheDocument()

    await selectOption(user, 'From Account', CHECKING)
    await selectOption(user, 'To Account', BROKER)
    await user.click(screen.getByRole('combobox', { name: 'Product' }))

    expect(await screen.findByRole('option', { name: 'Bitcoin' })).toBeInTheDocument()
    expect(screen.getByRole('option', { name: 'Tesouro Selic 2029' })).toBeInTheDocument()
    // "Old CDB" is closed: a closed product takes no new trades.
    expect(screen.queryByRole('option', { name: 'Old CDB' })).not.toBeInTheDocument()
  })

  it('does not let an investment account sit on both sides', async () => {
    const user = userEvent.setup()
    renderForm({
      accounts: [...accounts, { ...seedInvestmentAccount, id: 'acct-inv-2', name: 'Other broker' }],
    })

    await selectOption(user, 'From Account', BROKER)
    await user.click(screen.getByRole('combobox', { name: 'To Account' }))

    expect(await screen.findByRole('option', { name: CHECKING })).toBeInTheDocument()
    expect(screen.queryByRole('option', { name: 'Other broker' })).not.toBeInTheDocument()
  })

  it('prefills the amount with the live total of a buy and keeps a manual override', async () => {
    const user = userEvent.setup()
    renderForm()
    await pickBuy(user)

    await user.type(screen.getByRole('spinbutton', { name: 'Quantity' }), '10')
    await user.type(screen.getByRole('spinbutton', { name: 'Unit price' }), '100')
    await user.type(screen.getByRole('spinbutton', { name: 'Taxes' }), '5')

    expect(screen.getByRole('spinbutton', { name: 'Amount' })).toHaveValue(1005)
    expect(screen.getByText(/Total 1005\.00/)).toBeInTheDocument()

    const amount = screen.getByRole('spinbutton', { name: 'Amount' })
    await user.clear(amount)
    await user.type(amount, '1010')
    await user.type(screen.getByRole('spinbutton', { name: 'Quantity' }), '0')

    // The user's own figure (brokers round per lot) wins over the recomputed total.
    expect(amount).toHaveValue(1010)
  })

  it('subtracts taxes from the total of a sell', async () => {
    const user = userEvent.setup()
    renderForm()
    await selectOption(user, 'From Account', BROKER)
    await selectOption(user, 'To Account', CHECKING)
    await selectOption(user, 'Product', 'Bitcoin')

    await user.type(screen.getByRole('spinbutton', { name: 'Quantity' }), '10')
    await user.type(screen.getByRole('spinbutton', { name: 'Unit price' }), '100')
    await user.type(screen.getByRole('spinbutton', { name: 'Taxes' }), '5')

    expect(screen.getByRole('spinbutton', { name: 'Amount' })).toHaveValue(995)
  })

  it('suggests a resulting balance from the latest snapshot plus the gross traded value', async () => {
    const user = userEvent.setup()
    renderForm()
    await pickBuy(user)

    await user.type(screen.getByRole('spinbutton', { name: 'Quantity' }), '0.01')
    await user.type(screen.getByRole('spinbutton', { name: 'Unit price' }), '100000')
    await user.type(screen.getByRole('spinbutton', { name: 'Taxes' }), '5')

    // Bitcoin's latest snapshot is 900, the buy's gross value 0.01 x 100000 = 1000.
    expect(screen.getByRole('spinbutton', { name: 'Resulting balance' })).toHaveValue(1900)
  })

  it('creates a buy with the product, trade details and the edited resulting balance', async () => {
    const user = userEvent.setup()
    const capture = captureCreate()
    const onSaved = renderForm()
    await pickBuy(user)
    await user.type(screen.getByRole('spinbutton', { name: 'Quantity' }), '10')
    await user.type(screen.getByRole('spinbutton', { name: 'Unit price' }), '100')
    await user.type(screen.getByRole('spinbutton', { name: 'Taxes' }), '5')
    const resulting = screen.getByRole('spinbutton', { name: 'Resulting balance' })
    await user.clear(resulting)
    await user.type(resulting, '2000')
    await user.type(screen.getByRole('textbox', { name: 'Description' }), ' order')
    await user.click(screen.getByRole('button', { name: 'Add' }))

    await waitFor(() => expect(onSaved).toHaveBeenCalled())
    expect(capture.body()).toMatchObject({
      fromAccountId: seedAccounts[0].id,
      toAccountId: seedInvestmentAccount.id,
      amount: 1005,
      investmentProductId: 'iprod-btc',
      quantity: 10,
      unitPrice: 100,
      taxes: 5,
      resultingBalance: 2000,
    })
  })

  it('sends no resulting balance when the field is cleared', async () => {
    const user = userEvent.setup()
    const capture = captureCreate()
    const onSaved = renderForm()
    await pickBuy(user)
    await user.type(screen.getByRole('spinbutton', { name: 'Amount' }), '300')
    await user.clear(screen.getByRole('spinbutton', { name: 'Resulting balance' }))
    await user.type(screen.getByRole('textbox', { name: 'Description' }), 'Top up')
    await user.click(screen.getByRole('button', { name: 'Add' }))

    await waitFor(() => expect(onSaved).toHaveBeenCalled())
    expect(capture.body()).not.toHaveProperty('resultingBalance')
    expect(capture.body()).not.toHaveProperty('quantity')
    expect(capture.body()).toMatchObject({ amount: 300, investmentProductId: 'iprod-btc' })
  })

  it('"Sold entire position" sends a resulting balance of 0 and locks the field', async () => {
    const user = userEvent.setup()
    const capture = captureCreate()
    const onSaved = renderForm()
    await selectOption(user, 'From Account', BROKER)
    await selectOption(user, 'To Account', CHECKING)
    await selectOption(user, 'Product', 'Bitcoin')
    await user.type(screen.getByRole('spinbutton', { name: 'Amount' }), '900')
    await user.click(screen.getByRole('checkbox', { name: 'Sold entire position' }))

    const resulting = screen.getByRole('spinbutton', { name: 'Resulting balance' })
    expect(resulting).toHaveValue(0)
    expect(resulting).toBeDisabled()

    await user.type(screen.getByRole('textbox', { name: 'Description' }), 'Sell all')
    await user.click(screen.getByRole('button', { name: 'Add' }))

    await waitFor(() => expect(onSaved).toHaveBeenCalled())
    expect(capture.body()).toMatchObject({
      fromAccountId: seedInvestmentAccount.id,
      resultingBalance: 0,
    })
  })

  it('offers "Sold entire position" only on a sell', async () => {
    const user = userEvent.setup()
    renderForm()
    await pickBuy(user)

    expect(screen.queryByRole('checkbox', { name: 'Sold entire position' })).not.toBeInTheDocument()
  })

  it('requires a product for an investment account and quantity with unit price together', async () => {
    const user = userEvent.setup()
    renderForm()
    await selectOption(user, 'From Account', CHECKING)
    await selectOption(user, 'To Account', BROKER)
    await user.type(screen.getByRole('spinbutton', { name: 'Amount' }), '10')
    await user.type(screen.getByRole('textbox', { name: 'Description' }), 'x')

    expect(screen.getByRole('button', { name: 'Add' })).toBeDisabled()

    await selectOption(user, 'Product', 'Bitcoin')
    expect(screen.getByRole('button', { name: 'Add' })).toBeEnabled()

    await user.type(screen.getByRole('spinbutton', { name: 'Quantity' }), '1')
    expect(screen.getByRole('button', { name: 'Add' })).toBeDisabled()
  })

  it('surfaces the conflict message when the backend rejects the trade', async () => {
    const user = userEvent.setup()
    server.use(http.post('/api/transfers', () => HttpResponse.json({}, { status: 409 })))
    const onError = vi.fn()
    renderForm({ onError })
    await pickBuy(user)
    await user.type(screen.getByRole('spinbutton', { name: 'Amount' }), '10')
    await user.type(screen.getByRole('textbox', { name: 'Description' }), 'x')
    await user.click(screen.getByRole('button', { name: 'Add' }))

    await waitFor(() => expect(onError).toHaveBeenCalledWith(TRANSFER_CONFLICT_MESSAGE))
  })

  it('starts from a Buy preset: investment account, product and description filled in', async () => {
    const preset: TransferFormPreset = {
      direction: 'buy',
      investmentAccountId: seedInvestmentAccount.id,
      productId: 'iprod-btc',
      productName: 'Bitcoin',
    }
    renderForm({ preset })

    expect(screen.getByRole('textbox', { name: 'Description' })).toHaveValue('Buy Bitcoin')
    expect(screen.getByRole('combobox', { name: 'To Account' })).toHaveTextContent(BROKER)
    // The product list arrives after the select first renders.
    await waitFor(() =>
      expect(screen.getByRole('combobox', { name: 'Product' })).toHaveTextContent('Bitcoin'),
    )
  })

  it('starts from a Sell preset with the investment account as the source', async () => {
    renderForm({
      preset: {
        direction: 'sell',
        investmentAccountId: seedInvestmentAccount.id,
        productId: 'iprod-btc',
        productName: 'Bitcoin',
      },
    })

    expect(screen.getByRole('combobox', { name: 'From Account' })).toHaveTextContent(BROKER)
    expect(
      await screen.findByRole('checkbox', { name: 'Sold entire position' }),
    ).toBeInTheDocument()
  })

  it('edits a trade without offering the snapshot fields and sends no resulting balance', async () => {
    const user = userEvent.setup()
    let sent: Record<string, unknown> | null = null
    server.use(
      http.patch('/api/transfers/:id', async ({ request }) => {
        sent = (await request.json()) as Record<string, unknown>
        return HttpResponse.json(seedBitcoinBuyTransfer)
      }),
    )
    const onSaved = renderForm({ editing: seedBitcoinBuyTransfer })

    expect(screen.queryByRole('spinbutton', { name: 'Resulting balance' })).not.toBeInTheDocument()
    expect(screen.getByText('Editing a trade never changes snapshots.')).toBeInTheDocument()
    // The product list arrives after the select first renders.
    await waitFor(() =>
      expect(screen.getByRole('combobox', { name: 'Product' })).toHaveTextContent('Bitcoin'),
    )
    expect(screen.getByRole('spinbutton', { name: 'Quantity' })).toHaveValue(0.01)

    await user.click(screen.getByRole('button', { name: 'Save changes' }))

    await waitFor(() => expect(onSaved).toHaveBeenCalled())
    expect(sent).toMatchObject({ investmentProductId: 'iprod-btc', quantity: 0.01, taxes: 5 })
    expect(sent).not.toHaveProperty('resultingBalance')
  })

  it('leaves a plain transfer without any trade fields', async () => {
    const user = userEvent.setup()
    const capture = captureCreate()
    const onSaved = renderForm()
    await selectOption(user, 'From Account', CHECKING)
    await selectOption(user, 'To Account', seedAccounts[2].name)
    await user.type(screen.getByRole('spinbutton', { name: 'Amount' }), '25')
    await user.type(screen.getByRole('textbox', { name: 'Description' }), 'Card payment')

    expect(screen.queryByRole('group', { name: 'Trade details' })).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Add' }))

    await waitFor(() => expect(onSaved).toHaveBeenCalled())
    const sent = capture.body()!
    expect(Object.keys(sent).sort()).toEqual(
      ['amount', 'date', 'description', 'fromAccountId', 'toAccountId'].sort(),
    )
    expect(within(document.body).queryByText(/Trade details/)).not.toBeInTheDocument()
  })
})
