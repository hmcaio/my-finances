import { describe, expect, it, vi } from 'vitest'
import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { server } from '../../mocks/server'
import { seedAccounts, seedInvestmentAccount } from '../../mocks/handlers/accounts'
import { seedCategories, seedDividendCategory } from '../../mocks/handlers/categories'
import { seedInvestmentProducts } from '../../mocks/handlers/investmentProducts'
import { renderWithQueryClient } from '../../test/renderWithQueryClient'
import { selectOption } from '../../test/testUtils'
import { RegisterDividendDialog } from './RegisterDividendDialog'

// Two products that each already have an open holding in the seeded investment account, relabelled
// as FIIs: the dialog only cares about the `fiiProducts` prop and the product's holdings.
const selic = seedInvestmentProducts.find((p) => p.id === 'iprod-selic')!
const bitcoin = seedInvestmentProducts.find((p) => p.id === 'iprod-btc')!
const fiiProducts = [
  { ...selic, ticker: 'TSEL11' },
  { ...bitcoin, ticker: 'BTCX11' },
]

function renderDialog() {
  // The dividend category is opt-in (not in the default seed); the holding's account is an
  // INVESTMENT one, which the default accounts handler leaves out.
  server.use(
    http.get('/api/categories', () => HttpResponse.json([...seedCategories, seedDividendCategory])),
    http.get('/api/accounts', () => HttpResponse.json([...seedAccounts, seedInvestmentAccount])),
  )
  const onClose = vi.fn()
  renderWithQueryClient(<RegisterDividendDialog open onClose={onClose} fiiProducts={fiiProducts} />)
  return { onClose }
}

async function fillForm(
  user: ReturnType<typeof userEvent.setup>,
  dialog: ReturnType<typeof within>,
) {
  await selectOption(user, 'Ticker', 'TSEL11', dialog)
  await waitFor(() => expect(screen.queryByRole('listbox')).not.toBeInTheDocument())
  await selectOption(user, 'Holding', seedInvestmentAccount.name, dialog)
  await user.type(dialog.getByLabelText('Amount'), '50')
  fireEvent.change(dialog.getByLabelText('Date'), { target: { value: '2026-03-15' } })
  await selectOption(user, 'Deposit into account', 'Itau Checking', dialog)
  await selectOption(user, 'Payment method', 'Debit Card', dialog)
}

describe('RegisterDividendDialog', () => {
  it('keeps Register disabled until every required field is filled', async () => {
    const user = userEvent.setup({ delay: null })
    renderDialog()
    const dialog = within(await screen.findByRole('dialog', { name: 'Register dividend' }))
    expect(dialog.getByRole('button', { name: 'Register' })).toBeDisabled()
    // Nothing to pick a holding from until a ticker is chosen.
    expect(dialog.getByRole('combobox', { name: 'Holding' })).toHaveAttribute(
      'aria-disabled',
      'true',
    )

    await fillForm(user, dialog)

    expect(dialog.getByRole('button', { name: 'Register' })).toBeEnabled()
  })

  it('posts the dividend as an income transaction in the dividend category and closes', async () => {
    const user = userEvent.setup({ delay: null })
    let sent: unknown = null
    server.use(
      http.post('/api/transactions', async ({ request }) => {
        sent = await request.clone().json()
        // Record the body, then fall through to the default handler.
      }),
    )
    const { onClose } = renderDialog()
    const dialog = within(await screen.findByRole('dialog', { name: 'Register dividend' }))

    await fillForm(user, dialog)
    await user.click(dialog.getByRole('button', { name: 'Register' }))

    await waitFor(() => expect(onClose).toHaveBeenCalledTimes(1))
    expect(sent).toEqual({
      date: '2026-03-15',
      amount: 50,
      categoryId: seedDividendCategory.id,
      accountId: seedAccounts[0].id,
      paymentMethodId: 'pm-1',
      description: 'Dividend - TSEL11',
      investmentHoldingId: 'iholding-selic',
    })
  })

  it('sends a typed description, trimmed, instead of the default one', async () => {
    const user = userEvent.setup({ delay: null })
    let sent: { description?: string } | null = null
    server.use(
      http.post('/api/transactions', async ({ request }) => {
        sent = (await request.clone().json()) as { description?: string }
      }),
    )
    renderDialog()
    const dialog = within(await screen.findByRole('dialog', { name: 'Register dividend' }))

    await fillForm(user, dialog)
    await user.type(dialog.getByLabelText('Description (optional)'), '  March payout  ')
    await user.click(dialog.getByRole('button', { name: 'Register' }))

    await waitFor(() => expect(sent).not.toBeNull())
    expect(sent!.description).toBe('March payout')
  })

  it('keeps the dialog open and shows the error when the save fails', async () => {
    const user = userEvent.setup({ delay: null })
    server.use(http.post('/api/transactions', () => new HttpResponse(null, { status: 500 })))
    const { onClose } = renderDialog()
    const dialog = within(await screen.findByRole('dialog', { name: 'Register dividend' }))

    await fillForm(user, dialog)
    await user.click(dialog.getByRole('button', { name: 'Register' }))

    expect(await dialog.findByText(/Request failed with status 500/)).toBeInTheDocument()
    expect(onClose).not.toHaveBeenCalled()
    // The form keeps what was typed so the user can retry.
    expect(dialog.getByLabelText('Amount')).toHaveValue(50)
  })

  it('clears the holding when the ticker changes', async () => {
    const user = userEvent.setup({ delay: null })
    renderDialog()
    const dialog = within(await screen.findByRole('dialog', { name: 'Register dividend' }))
    await selectOption(user, 'Ticker', 'TSEL11', dialog)
    await waitFor(() => expect(screen.queryByRole('listbox')).not.toBeInTheDocument())
    await selectOption(user, 'Holding', seedInvestmentAccount.name, dialog)
    expect(dialog.getByRole('combobox', { name: 'Holding' })).toHaveTextContent(
      seedInvestmentAccount.name,
    )

    await selectOption(user, 'Ticker', 'BTCX11', dialog)

    await waitFor(() =>
      expect(dialog.getByRole('combobox', { name: 'Holding' })).not.toHaveTextContent(
        seedInvestmentAccount.name,
      ),
    )
  })

  it('resets the form and closes on Cancel', async () => {
    const user = userEvent.setup({ delay: null })
    const { onClose } = renderDialog()
    const dialog = within(await screen.findByRole('dialog', { name: 'Register dividend' }))
    await user.type(dialog.getByLabelText('Amount'), '12')

    await user.click(dialog.getByRole('button', { name: 'Cancel' }))

    expect(onClose).toHaveBeenCalledTimes(1)
    expect(dialog.getByLabelText('Amount')).toHaveValue(null)
  })
})
