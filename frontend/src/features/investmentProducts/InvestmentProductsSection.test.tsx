import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { delay, http, HttpResponse } from 'msw'
import { server } from '../../mocks/server'
import { seedInvestmentAccount } from '../../mocks/handlers/accounts'
import { seedInvestmentHoldings } from '../../mocks/handlers/investmentHoldings'
import { investmentProductCreateConflictHandler } from '../../mocks/handlers/investmentProducts'
import { SAVE_CONFLICT_MESSAGE } from '../../api/investments/investmentProducts'
import { findRow, renderWithRouter, selectOption } from '../../test/testUtils'
import { restoreViewport, setViewportWidth, VIEWPORT } from '../../test/viewport'
import { InvestmentProductsSection } from './InvestmentProductsSection'

function renderSection(accountClosed = false) {
  return renderWithRouter(
    <InvestmentProductsSection
      accountId={seedInvestmentAccount.id}
      accountClosed={accountClosed}
    />,
  )
}

function optionNames() {
  return screen.getAllByRole('option').map((o) => o.textContent)
}

/**
 * Records the body of the next request to `method path`, then falls through to the default
 * (stateful) handler, so the follow-up refetch sees the saved product.
 */
function captureBody(method: 'post' | 'patch', path: string) {
  const sent: { body?: Record<string, unknown> } = {}
  server.use(
    http[method](path, async ({ request }) => {
      sent.body = (await request.clone().json()) as Record<string, unknown>
    }),
  )
  return sent
}

/** The add form only exists inside the header's dialog (F021: no panel below the table). */
async function openAddDialog(user: ReturnType<typeof userEvent.setup>) {
  await user.click(await screen.findByRole('button', { name: 'Add product' }))
  return screen.findByRole('dialog', { name: 'Add product' })
}

/**
 * The section lists holdings in this account (F022/ADR 0020) - read-only (product name, category,
 * sub-category, latest value, status), linking out to the product's own page for management. Only
 * product creation (which creates the first holding too) still happens here.
 */
describe('InvestmentProductsSection', () => {
  // jsdom has no viewport, which MUI treats as the tablet band: pin the desktop layout so every
  // column (Sub-category included, hidden on tablet) is visible for the assertions below.
  beforeEach(() => setViewportWidth(VIEWPORT.desktop))
  afterEach(restoreViewport)

  it('lists the holdings with their category and sub-category names and status', async () => {
    renderSection()

    expect(await screen.findByText('Tesouro Selic 2029')).toBeInTheDocument()
    expect(screen.getByText('Bitcoin')).toBeInTheDocument()
    expect(screen.getByText('Old CDB')).toBeInTheDocument()
    const selic = await findRow('Tesouro Selic 2029')
    expect(selic.getByText('Fixed Income')).toBeInTheDocument()
    expect(selic.getByText('Tesouro Selic')).toBeInTheDocument()
    expect(selic.getByText('Open')).toBeInTheDocument()
    // A category-only product shows no sub-category.
    const bitcoin = await findRow('Bitcoin')
    expect(bitcoin.getByText('Crypto')).toBeInTheDocument()
    expect(bitcoin.getByText('-')).toBeInTheDocument()
    expect((await findRow('Old CDB')).getByText('Closed')).toBeInTheDocument()
  })

  it('links each product to its detail page, shows the latest value and flags a stale holding', async () => {
    renderSection()
    await screen.findByText('Bitcoin')

    expect(screen.getByRole('link', { name: 'Bitcoin' })).toHaveAttribute(
      'href',
      '/investment-products/iprod-btc',
    )
    const bitcoin = await findRow('Bitcoin')
    // Seeded: latest snapshot 900 on 2026-08-05, and a buy newer than it.
    expect(bitcoin.getByText('900.00')).toBeInTheDocument()
    expect(bitcoin.getByText('Needs snapshot')).toBeInTheDocument()
    const selic = await findRow('Tesouro Selic 2029')
    expect(selic.queryByText('Needs snapshot')).not.toBeInTheDocument()
  })

  it('follows the chosen category in the sub-category select and resets it on change', async () => {
    const user = userEvent.setup()
    renderSection()
    await screen.findByText('Bitcoin')
    const dialog = within(await openAddDialog(user))

    // No category yet: the sub-category select is disabled.
    expect(dialog.getByRole('combobox', { name: 'Sub-category' })).toHaveAttribute(
      'aria-disabled',
      'true',
    )

    await selectOption(user, 'Category', 'Fixed Income', dialog)
    await user.click(dialog.getByRole('combobox', { name: 'Sub-category' }))
    expect(optionNames()).toEqual(['No sub-category', 'CDB', 'Tesouro Selic'])
    await user.click(screen.getByRole('option', { name: 'Tesouro Selic' }))
    expect(dialog.getByRole('combobox', { name: 'Sub-category' })).toHaveTextContent(
      'Tesouro Selic',
    )

    // Changing the category drops the previous sub-category and offers the new one's.
    await selectOption(user, 'Category', 'Variable Income', dialog)
    expect(dialog.getByRole('combobox', { name: 'Sub-category' })).toHaveTextContent(
      'No sub-category',
    )
    await user.click(dialog.getByRole('combobox', { name: 'Sub-category' }))
    expect(optionNames()).toEqual(['No sub-category', 'ETFs'])
  })

  it('adds a product with a sub-category, creating its first holding in this account', async () => {
    const user = userEvent.setup()
    const sent = captureBody('post', '/api/investment-products')
    renderSection()
    await screen.findByText('Bitcoin')
    const dialog = within(await openAddDialog(user))

    await user.type(dialog.getByRole('textbox', { name: 'Product name' }), 'CDB 110% Test')
    await selectOption(user, 'Category', 'Fixed Income', dialog)
    await selectOption(user, 'Sub-category', 'CDB', dialog)
    await user.click(dialog.getByRole('button', { name: 'Add product' }))

    await waitFor(() =>
      expect(sent.body).toEqual({
        accountId: seedInvestmentAccount.id,
        investmentCategoryId: 'icat-fixed',
        investmentSubcategoryId: 'isub-cdb',
        name: 'CDB 110% Test',
      }),
    )
    expect(await screen.findByText('CDB 110% Test')).toBeInTheDocument()
    // The dialog closes on success.
    await waitFor(() =>
      expect(screen.queryByRole('dialog', { name: 'Add product' })).not.toBeInTheDocument(),
    )
  })

  it('saves a category-only product (Crypto) without a sub-category', async () => {
    const user = userEvent.setup()
    const sent = captureBody('post', '/api/investment-products')
    renderSection()
    await screen.findByText('Bitcoin')
    const dialog = within(await openAddDialog(user))

    await user.type(dialog.getByRole('textbox', { name: 'Product name' }), 'Ethereum')
    await selectOption(user, 'Category', 'Crypto', dialog)
    await user.click(dialog.getByRole('combobox', { name: 'Sub-category' }))
    // Crypto has no sub-categories: the only option is "none".
    expect(optionNames()).toEqual(['No sub-category'])
    await user.keyboard('{Escape}')
    await user.click(dialog.getByRole('button', { name: 'Add product' }))

    expect(await screen.findByText('Ethereum')).toBeInTheDocument()
    expect(sent.body).toEqual({
      accountId: seedInvestmentAccount.id,
      investmentCategoryId: 'icat-crypto',
      name: 'Ethereum',
    })
  })

  it('keeps Add disabled until a name and a category are given', async () => {
    const user = userEvent.setup()
    renderSection()
    await screen.findByText('Bitcoin')
    const dialog = within(await openAddDialog(user))
    const add = dialog.getByRole('button', { name: 'Add product' })

    expect(add).toBeDisabled()
    await user.type(dialog.getByRole('textbox', { name: 'Product name' }), 'Ethereum')
    expect(add).toBeDisabled()
    await selectOption(user, 'Category', 'Crypto', dialog)
    expect(add).toBeEnabled()
  })

  it('surfaces the save-conflict message when creating a product is refused', async () => {
    server.use(investmentProductCreateConflictHandler)
    const user = userEvent.setup()
    renderSection()
    await screen.findByText('Bitcoin')
    const dialog = within(await openAddDialog(user))

    await user.type(dialog.getByRole('textbox', { name: 'Product name' }), 'Bitcoin')
    await selectOption(user, 'Category', 'Crypto', dialog)
    await user.click(dialog.getByRole('button', { name: 'Add product' }))

    expect(await screen.findByText(SAVE_CONFLICT_MESSAGE)).toBeInTheDocument()
  })

  it('hides the add button on a closed account', async () => {
    renderSection(true)
    await screen.findByText('Bitcoin')

    expect(screen.queryByRole('button', { name: 'Add product' })).not.toBeInTheDocument()
  })

  it('shows an empty state for an account without holdings', async () => {
    server.use(http.get('/api/investment-holdings', () => HttpResponse.json([])))
    renderSection()

    expect(await screen.findByText('No products yet.')).toBeInTheDocument()
  })

  it('shows a loading skeleton only when the first fetch is slow, then the rows', async () => {
    server.use(
      http.get('/api/investment-holdings', async () => {
        await delay(400)
        return HttpResponse.json(seedInvestmentHoldings)
      }),
    )
    renderSection()

    expect(await screen.findByText('Loading…')).toBeInTheDocument()
    expect(await screen.findByText('Bitcoin')).toBeInTheDocument()
    expect(screen.queryByText('Loading…')).not.toBeInTheDocument()
  })

  it('shows a retryable notice instead of raw ids when the categories cannot be loaded', async () => {
    server.use(
      http.get('/api/investment-categories', () => new HttpResponse(null, { status: 500 })),
    )
    renderSection()

    expect((await screen.findAllByText(/Could not load data/)).length).toBeGreaterThan(0)
    expect(screen.queryByText('Bitcoin')).not.toBeInTheDocument()
  })
})

describe('InvestmentProductsSection responsive layout (F021)', () => {
  afterEach(restoreViewport)

  async function findCard(name: string) {
    const list = await screen.findByRole('list', { name: 'Investment holdings' })
    await within(list).findByText(name)
    const items = within(list).getAllByRole('listitem')
    return within(items.find((item) => within(item).queryByText(name))!)
  }

  describe('mobile', () => {
    beforeEach(() => setViewportWidth(VIEWPORT.mobile))

    it('renders cards instead of a table, with category, sub-category and status', async () => {
      renderSection()

      const card = await findCard('Tesouro Selic 2029')
      expect(screen.queryByRole('table')).not.toBeInTheDocument()
      // "Tesouro Selic" alone would also match the product name (title), which starts with it.
      expect(card.getByText('Fixed Income · Tesouro Selic')).toBeInTheDocument()
      expect(card.getByText('Open')).toBeInTheDocument()
    })

    it('Add opens a full-screen dialog', async () => {
      const user = userEvent.setup()
      renderSection()
      await findCard('Tesouro Selic 2029')

      const dialog = await openAddDialog(user)

      expect(dialog).toHaveClass('MuiDialog-paperFullScreen')
    })
  })

  describe('tablet', () => {
    beforeEach(() => setViewportWidth(VIEWPORT.tablet))

    it('keeps the table without the Sub-category column, reachable through the row expander', async () => {
      const user = userEvent.setup()
      renderSection()

      const row = await findRow('Tesouro Selic 2029')
      expect(screen.getByRole('columnheader', { name: 'Category' })).toBeInTheDocument()
      expect(screen.queryByRole('columnheader', { name: 'Sub-category' })).not.toBeInTheDocument()

      await user.click(row.getByRole('button', { name: 'Show details' }))
      expect(await screen.findByText('Tesouro Selic')).toBeInTheDocument()
    })
  })
})
