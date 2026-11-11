import { afterEach, describe, expect, it } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { Route, Routes } from 'react-router-dom'
import { server } from '../../../mocks/server'
import { accountsStore, seedInvestmentAccount } from '../../../mocks/handlers/accounts'
import { investmentHoldingsStore } from '../../../mocks/handlers/investmentHoldings'
import { investmentProductsStore } from '../../../mocks/handlers/investmentProducts'
import { getSnapshot } from '../../../providers/notifications/notificationStore'
import { renderWithRouter } from '../../../test/utils/testUtils'
import { restoreViewport, setViewportWidth, VIEWPORT } from '../../../test/utils/viewport'
import { InvestmentSnapshotSheetPage } from './InvestmentSnapshotSheetPage'

const SHEET_URL = '/api/investment-snapshot-sheet'

/**
 * Adds a second account with two more open holdings to the seeded two (Bitcoin, which has a
 * snapshot, and Tesouro Selic 2029, which has none), so the sheet has four rows:
 * XP Investimentos (Bitcoin, Tesouro Selic 2029) then Binance (Ethereum, Solana).
 */
function addBinanceHoldings() {
  accountsStore.add({ ...seedInvestmentAccount, id: 'acct-bin', name: 'Binance' })
  for (const [id, name] of [
    ['iprod-eth', 'Ethereum'],
    ['iprod-sol', 'Solana'],
  ]) {
    investmentProductsStore.add({
      id,
      investmentCategoryId: 'icat-crypto',
      investmentSubcategoryId: null,
      name,
      additionalNotes: null,
      closed: false,
      ticker: null,
      segmentId: null,
    })
    investmentHoldingsStore.add({
      id: `iholding-${id}`,
      productId: id,
      accountId: 'acct-bin',
      closedDate: null,
      closed: false,
      additionalNotes: null,
      hasHistory: false,
      needsSnapshot: false,
      latestSnapshot: null,
    })
  }
}

function renderSheet() {
  return renderWithRouter(
    <Routes>
      <Route path="/investments/snapshots" element={<InvestmentSnapshotSheetPage />} />
      <Route path="/investments" element={<div>Investments page</div>} />
    </Routes>,
    { initialEntries: ['/investments/snapshots'] },
  )
}

const balanceInput = (product: string, account: string) =>
  screen.findByRole('spinbutton', { name: `Balance of ${product} in ${account}` })

const saveButton = () => screen.getByRole('button', { name: 'Save snapshots' })

/** The sheet's POST bodies, in order. */
function recordPosts() {
  const bodies: unknown[] = []
  server.events.on('request:start', async ({ request }) => {
    if (request.method === 'POST' && new URL(request.url).pathname === SHEET_URL) {
      bodies.push(await request.clone().json())
    }
  })
  return bodies
}

afterEach(() => {
  restoreViewport()
  server.events.removeAllListeners()
})

describe('InvestmentSnapshotSheetPage', () => {
  it('lists one row per open holding, ordered by account then product, with the previous balance', async () => {
    addBinanceHoldings()
    renderSheet()

    await balanceInput('Solana', 'Binance')
    const table = screen.getByRole('table', { name: 'Open holdings' })
    const rows = within(table).getAllByRole('row').slice(1)
    expect(rows.map((row) => within(row).getAllByRole('cell')[0].textContent)).toEqual([
      'Ethereum',
      'Solana',
      'Bitcoin',
      'Tesouro Selic 2029',
    ])
    // Bitcoin's latest snapshot (900.00 on 5 Aug 2026) is its Previous cell and its placeholder.
    const bitcoin = within(rows[2])
    expect(bitcoin.getAllByRole('cell')[1].textContent).toBe('XP Investimentos')
    expect(bitcoin.getAllByRole('cell')[2].textContent).toContain('900.00')
    expect(bitcoin.getAllByRole('cell')[2].textContent).toContain('Aug 5, 2026')
    expect(bitcoin.getByRole('spinbutton')).toHaveAttribute('placeholder', '900.00')
    expect(within(rows[0]).getAllByRole('cell')[2].textContent).toBe('None yet')
  })

  it('marks the date as required, with the hint', async () => {
    renderSheet()

    expect(await screen.findByLabelText(/^Date/)).toBeRequired()
    expect(screen.getByText('Fields marked * are required')).toBeInTheDocument()
  })

  it('moves focus down the balance inputs on Enter and from the last one to Save', async () => {
    const user = userEvent.setup()
    renderSheet()
    const bitcoin = await balanceInput('Bitcoin', 'XP Investimentos')
    const selic = await balanceInput('Tesouro Selic 2029', 'XP Investimentos')
    await user.type(bitcoin, '10')

    await user.keyboard('{Enter}')
    expect(selic).toHaveFocus()

    await user.type(selic, '20')
    await user.keyboard('{Enter}')
    expect(saveButton()).toHaveFocus()
  })

  it('keeps Save disabled until a valid filled row exists', async () => {
    const user = userEvent.setup()
    renderSheet()
    const bitcoin = await balanceInput('Bitcoin', 'XP Investimentos')
    expect(saveButton()).toBeDisabled()

    await user.type(bitcoin, '10')

    expect(saveButton()).toBeEnabled()
  })

  it('shows the signed difference to the previous balance as it is typed', async () => {
    const user = userEvent.setup()
    renderSheet()
    const bitcoin = await balanceInput('Bitcoin', 'XP Investimentos')

    await user.type(bitcoin, '950.5')
    expect(screen.getByText('+50.50')).toBeInTheDocument()

    await user.clear(bitcoin)
    await user.type(bitcoin, '800')
    expect(screen.getByText('-100.00')).toBeInTheDocument()
  })

  it('flags a negative balance on its row and sends nothing', async () => {
    const user = userEvent.setup()
    const posts = recordPosts()
    renderSheet()
    const bitcoin = await balanceInput('Bitcoin', 'XP Investimentos')

    await user.type(bitcoin, '-5')

    expect(bitcoin).toBeInvalid()
    expect(screen.getByText("This value can't be negative.")).toBeInTheDocument()
    expect(saveButton()).toBeDisabled()
    await user.keyboard('{Enter}')
    expect(posts).toEqual([])
  })

  it('flags more than two decimals', async () => {
    const user = userEvent.setup()
    renderSheet()

    await user.type(await balanceInput('Bitcoin', 'XP Investimentos'), '1.234')

    expect(screen.getByText('This value has too many digits.')).toBeInTheDocument()
  })

  it('saves only the filled rows, toasts the plural count, clears the inputs and refreshes Previous', async () => {
    const user = userEvent.setup()
    addBinanceHoldings()
    const posts = recordPosts()
    renderSheet()
    await user.type(await balanceInput('Ethereum', 'Binance'), '300')
    await user.type(await balanceInput('Bitcoin', 'XP Investimentos'), '950')
    await user.type(await balanceInput('Tesouro Selic 2029', 'XP Investimentos'), '0')

    await user.click(saveButton())

    await waitFor(() => expect(getSnapshot()?.message).toBe('3 snapshots saved'))
    expect(posts).toHaveLength(1)
    expect(posts[0]).toEqual({
      date: expect.stringMatching(/^\d{4}-\d{2}-\d{2}$/),
      entries: [
        { holdingId: 'iholding-iprod-eth', balance: 300 },
        { holdingId: 'iholding-btc', balance: 950 },
        { holdingId: 'iholding-selic', balance: 0 },
      ],
    })
    expect(await balanceInput('Ethereum', 'Binance')).toHaveValue(null)
    expect(await balanceInput('Bitcoin', 'XP Investimentos')).toHaveValue(null)
    const table = screen.getByRole('table', { name: 'Open holdings' })
    await waitFor(() => expect(within(table).getAllByText(/300\.00/).length).toBeGreaterThan(0))
    // The blank row (Solana) was not recorded.
    const solana = (await balanceInput('Solana', 'Binance')).closest('tr') as HTMLElement
    expect(within(solana).getAllByRole('cell')[2].textContent).toBe('None yet')
  })

  it('toasts the singular for one snapshot', async () => {
    const user = userEvent.setup()
    renderSheet()
    await user.type(await balanceInput('Bitcoin', 'XP Investimentos'), '950')

    await user.click(saveButton())

    await waitFor(() => expect(getSnapshot()?.message).toBe('1 snapshot saved'))
  })

  it('highlights exactly the rows of a 404, keeps every input and shows one banner', async () => {
    const user = userEvent.setup()
    addBinanceHoldings()
    // A blank row (Solana) sits between the two sent ones, so entries[] indexes must be mapped
    // through the sent entries, not through the sheet's rows.
    server.use(
      http.post(SHEET_URL, () =>
        HttpResponse.json(
          {
            type: 'urn:my-finances:error:investment-snapshot-sheet-holdings-not-found',
            errors: [{ field: 'entries[1].holdingId', code: 'investment-holding-not-found' }],
          },
          { status: 404 },
        ),
      ),
    )
    renderSheet()
    await user.type(await balanceInput('Ethereum', 'Binance'), '300')
    await user.type(await balanceInput('Bitcoin', 'XP Investimentos'), '950')
    await user.type(await balanceInput('Tesouro Selic 2029', 'XP Investimentos'), '20')

    await user.click(saveButton())

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'The holdings highlighted below no longer exist, so nothing was saved.',
    )
    expect(await balanceInput('Bitcoin', 'XP Investimentos')).toBeInvalid()
    expect(screen.getByText(/This holding no longer exists/)).toBeInTheDocument()
    expect(await balanceInput('Ethereum', 'Binance')).not.toBeInvalid()
    expect(await balanceInput('Tesouro Selic 2029', 'XP Investimentos')).not.toBeInvalid()
    expect(await balanceInput('Ethereum', 'Binance')).toHaveValue(300)
    expect(await balanceInput('Bitcoin', 'XP Investimentos')).toHaveValue(950)
    expect(await balanceInput('Tesouro Selic 2029', 'XP Investimentos')).toHaveValue(20)
    expect(getSnapshot()).toBeNull()
  })

  it('lets the user clear a rejected row and save again', async () => {
    const user = userEvent.setup()
    server.use(
      http.post(
        SHEET_URL,
        () =>
          HttpResponse.json(
            {
              type: 'urn:my-finances:error:investment-snapshot-sheet-holdings-not-found',
              errors: [{ field: 'entries[0].holdingId', code: 'investment-holding-not-found' }],
            },
            { status: 404 },
          ),
        { once: true },
      ),
    )
    renderSheet()
    const bitcoin = await balanceInput('Bitcoin', 'XP Investimentos')
    await user.type(bitcoin, '950')
    await user.type(await balanceInput('Tesouro Selic 2029', 'XP Investimentos'), '20')
    await user.click(saveButton())
    await screen.findByRole('alert')

    await user.clear(bitcoin)
    expect(bitcoin).not.toBeInvalid()
    await user.click(saveButton())

    await waitFor(() => expect(getSnapshot()?.message).toBe('1 snapshot saved'))
  })

  it('keeps the inputs on a 409 and offers to try again', async () => {
    const user = userEvent.setup()
    server.use(
      http.post(
        SHEET_URL,
        () =>
          HttpResponse.json(
            { type: 'urn:my-finances:error:investment-snapshot-date-taken' },
            { status: 409 },
          ),
        { once: true },
      ),
    )
    renderSheet()
    const bitcoin = await balanceInput('Bitcoin', 'XP Investimentos')
    await user.type(bitcoin, '950')

    await user.click(saveButton())

    expect(await screen.findByRole('alert')).toHaveTextContent('try saving again')
    expect(bitcoin).toHaveValue(950)
    expect(bitcoin).not.toBeInvalid()
    expect(getSnapshot()).toBeNull()

    await user.click(saveButton())

    await waitFor(() => expect(getSnapshot()?.message).toBe('1 snapshot saved'))
  })

  it('counts a same-day replacement in the toast', async () => {
    const user = userEvent.setup()
    server.use(http.post(SHEET_URL, () => HttpResponse.json({ created: 1, replaced: 2 })))
    renderSheet()
    await user.type(await balanceInput('Bitcoin', 'XP Investimentos'), '950')

    await user.click(saveButton())

    await waitFor(() => expect(getSnapshot()?.message).toBe('3 snapshots saved'))
  })

  it('shows the empty state with a link to the Products tab when no holding is open', async () => {
    server.use(http.get(SHEET_URL, () => HttpResponse.json([])))
    renderSheet()

    expect(await screen.findByText(/No open holdings to record a snapshot for/)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Go to Products' })).toHaveAttribute(
      'href',
      '/investments?tab=products',
    )
    expect(screen.queryByRole('button', { name: 'Save snapshots' })).not.toBeInTheDocument()
  })

  it('renders the rows as cards on a phone, each balance input still named by its product and account', async () => {
    setViewportWidth(VIEWPORT.mobile)
    renderSheet()

    const bitcoin = await balanceInput('Bitcoin', 'XP Investimentos')

    expect(screen.queryByRole('table')).not.toBeInTheDocument()
    expect(screen.getByRole('list', { name: 'Open holdings' })).toBeInTheDocument()
    expect(bitcoin.closest('li')).toHaveTextContent('Bitcoin')
    expect(bitcoin.closest('li')).toHaveTextContent('900.00')
  })

  it('uses the Brazilian Portuguese labels when the locale is pt-BR', async () => {
    window.localStorage.setItem('my-finances:locale', 'pt-BR')
    renderSheet()

    expect(await screen.findByRole('button', { name: 'Salvar instantâneos' })).toBeDisabled()
    expect(screen.getByRole('heading', { name: 'Registrar instantâneos' })).toBeInTheDocument()
    window.localStorage.removeItem('my-finances:locale')
  })
})
