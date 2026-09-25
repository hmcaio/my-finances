import { describe, expect, it } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { delay, http, HttpResponse } from 'msw'
import { server } from '../../mocks/server'
import { seedInvestmentAccount } from '../../mocks/handlers/accounts'
import {
  investmentProductCloseConflictHandler,
  investmentProductCreateConflictHandler,
  investmentProductDeleteConflictHandler,
  seedInvestmentProducts,
} from '../../mocks/handlers/investmentProducts'
import {
  CLOSE_CONFLICT_MESSAGE,
  DELETE_CONFLICT_MESSAGE,
  SAVE_CONFLICT_MESSAGE,
} from '../../api/investments/investmentProducts'
import { findRow, renderWithRouter, selectOption } from '../../test/testUtils'
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

describe('InvestmentProductsSection', () => {
  it('lists the products with their category and sub-category names and status', async () => {
    renderSection()

    for (const product of seedInvestmentProducts) {
      expect(await screen.findByText(product.name)).toBeInTheDocument()
    }
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

  it('links each product to its detail page, shows the latest value and flags a stale product', async () => {
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

  it('offers delete only while the product has no history', async () => {
    renderSection()
    await screen.findByText('Bitcoin')

    expect(
      (await findRow('Tesouro Selic 2029')).getByRole('button', { name: 'Delete' }),
    ).toBeInTheDocument()
    // Bitcoin has history: closing is the only way out.
    expect(
      (await findRow('Bitcoin')).queryByRole('button', { name: 'Delete' }),
    ).not.toBeInTheDocument()
    expect((await findRow('Bitcoin')).getByRole('button', { name: 'Close' })).toBeEnabled()
  })

  it('follows the chosen category in the sub-category select and resets it on change', async () => {
    const user = userEvent.setup()
    renderSection()
    await screen.findByText('Bitcoin')
    const form = within(screen.getByRole('group', { name: 'Add product' }))

    // No category yet: the sub-category select is disabled.
    expect(form.getByRole('combobox', { name: 'Sub-category' })).toHaveAttribute(
      'aria-disabled',
      'true',
    )

    await selectOption(user, 'Category', 'Fixed Income', form)
    await user.click(form.getByRole('combobox', { name: 'Sub-category' }))
    expect(optionNames()).toEqual(['No sub-category', 'CDB', 'Tesouro Selic'])
    await user.click(screen.getByRole('option', { name: 'Tesouro Selic' }))
    expect(form.getByRole('combobox', { name: 'Sub-category' })).toHaveTextContent('Tesouro Selic')

    // Changing the category drops the previous sub-category and offers the new one's.
    await selectOption(user, 'Category', 'Variable Income', form)
    expect(form.getByRole('combobox', { name: 'Sub-category' })).toHaveTextContent(
      'No sub-category',
    )
    await user.click(form.getByRole('combobox', { name: 'Sub-category' }))
    expect(optionNames()).toEqual(['No sub-category', 'ETFs'])
  })

  it('adds a product with a sub-category and sends the ids', async () => {
    const user = userEvent.setup()
    const sent = captureBody('post', '/api/investment-products')
    renderSection()
    await screen.findByText('Bitcoin')
    const form = within(screen.getByRole('group', { name: 'Add product' }))

    await user.type(form.getByRole('textbox', { name: 'Product name' }), 'CDB 110% Test')
    await selectOption(user, 'Category', 'Fixed Income', form)
    await selectOption(user, 'Sub-category', 'CDB', form)
    await user.click(form.getByRole('button', { name: 'Add product' }))

    await waitFor(() =>
      expect(sent.body).toEqual({
        accountId: seedInvestmentAccount.id,
        investmentCategoryId: 'icat-fixed',
        investmentSubcategoryId: 'isub-cdb',
        name: 'CDB 110% Test',
      }),
    )
    expect(await screen.findByText('CDB 110% Test')).toBeInTheDocument()
    // The form is empty again for the next product.
    expect(form.getByRole('textbox', { name: 'Product name' })).toHaveValue('')
  })

  it('saves a category-only product (Crypto) without a sub-category', async () => {
    const user = userEvent.setup()
    const sent = captureBody('post', '/api/investment-products')
    renderSection()
    await screen.findByText('Bitcoin')
    const form = within(screen.getByRole('group', { name: 'Add product' }))

    await user.type(form.getByRole('textbox', { name: 'Product name' }), 'Ethereum')
    await selectOption(user, 'Category', 'Crypto', form)
    await user.click(form.getByRole('combobox', { name: 'Sub-category' }))
    // Crypto has no sub-categories: the only option is "none".
    expect(optionNames()).toEqual(['No sub-category'])
    await user.keyboard('{Escape}')
    await user.click(form.getByRole('button', { name: 'Add product' }))

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
    const form = within(screen.getByRole('group', { name: 'Add product' }))
    const add = form.getByRole('button', { name: 'Add product' })

    expect(add).toBeDisabled()
    await user.type(form.getByRole('textbox', { name: 'Product name' }), 'Ethereum')
    expect(add).toBeDisabled()
    await selectOption(user, 'Category', 'Crypto', form)
    expect(add).toBeEnabled()
  })

  it('surfaces the save-conflict message when creating a product is refused', async () => {
    server.use(investmentProductCreateConflictHandler)
    const user = userEvent.setup()
    renderSection()
    await screen.findByText('Bitcoin')
    const form = within(screen.getByRole('group', { name: 'Add product' }))

    await user.type(form.getByRole('textbox', { name: 'Product name' }), 'Bitcoin')
    await selectOption(user, 'Category', 'Crypto', form)
    await user.click(form.getByRole('button', { name: 'Add product' }))

    expect(await screen.findByText(SAVE_CONFLICT_MESSAGE)).toBeInTheDocument()
  })

  it('edits a product in a dialog prefilled with its current values', async () => {
    const user = userEvent.setup()
    const sent = captureBody('patch', '/api/investment-products/:id')
    renderSection()
    await screen.findByText('Bitcoin')

    await user.click((await findRow('Tesouro Selic 2029')).getByRole('button', { name: 'Edit' }))
    const dialog = within(await screen.findByRole('dialog', { name: 'Edit product' }))
    expect(dialog.getByRole('textbox', { name: 'Product name' })).toHaveValue('Tesouro Selic 2029')
    expect(dialog.getByRole('combobox', { name: 'Category' })).toHaveTextContent('Fixed Income')
    expect(dialog.getByRole('combobox', { name: 'Sub-category' })).toHaveTextContent(
      'Tesouro Selic',
    )

    const name = dialog.getByRole('textbox', { name: 'Product name' })
    await user.clear(name)
    await user.type(name, 'Tesouro Selic 2031')
    await selectOption(user, 'Sub-category', 'CDB', dialog)
    await user.click(dialog.getByRole('button', { name: 'Save' }))

    await waitFor(() =>
      expect(sent.body).toMatchObject({
        accountId: seedInvestmentAccount.id,
        investmentCategoryId: 'icat-fixed',
        investmentSubcategoryId: 'isub-cdb',
        name: 'Tesouro Selic 2031',
      }),
    )
    expect(await screen.findByText('Tesouro Selic 2031')).toBeInTheDocument()
    await waitFor(() =>
      expect(screen.queryByRole('dialog', { name: 'Edit product' })).not.toBeInTheDocument(),
    )
  })

  it('closes a product after confirmation', async () => {
    const user = userEvent.setup()
    renderSection()
    await screen.findByText('Bitcoin')

    await user.click((await findRow('Tesouro Selic 2029')).getByRole('button', { name: 'Close' }))
    await user.click(await screen.findByRole('button', { name: 'Close product' }))

    await waitFor(async () =>
      expect((await findRow('Tesouro Selic 2029')).getByText('Closed')).toBeInTheDocument(),
    )
    // The dialog is still fading out (and hiding the page from the accessibility tree) for a moment.
    await waitFor(async () =>
      expect(
        (await findRow('Tesouro Selic 2029')).getByRole('button', { name: 'Close' }),
      ).toBeDisabled(),
    )
  })

  it('surfaces the close-refused message (already closed or still has value) when closing is refused', async () => {
    server.use(investmentProductCloseConflictHandler)
    const user = userEvent.setup()
    renderSection()
    await screen.findByText('Bitcoin')

    await user.click((await findRow('Bitcoin')).getByRole('button', { name: 'Close' }))
    await user.click(await screen.findByRole('button', { name: 'Close product' }))

    expect(await screen.findByText(CLOSE_CONFLICT_MESSAGE)).toBeInTheDocument()
  })

  it('deletes a product without history after confirmation', async () => {
    const user = userEvent.setup()
    renderSection()
    await screen.findByText('Bitcoin')

    await user.click((await findRow('Tesouro Selic 2029')).getByRole('button', { name: 'Delete' }))
    await user.click(await screen.findByRole('button', { name: 'Delete product' }))

    await waitFor(() => expect(screen.queryByText('Tesouro Selic 2029')).not.toBeInTheDocument())
  })

  it('surfaces the close-instead message and keeps the row when delete is refused', async () => {
    server.use(investmentProductDeleteConflictHandler)
    const user = userEvent.setup()
    renderSection()
    await screen.findByText('Bitcoin')

    await user.click((await findRow('Tesouro Selic 2029')).getByRole('button', { name: 'Delete' }))
    await user.click(await screen.findByRole('button', { name: 'Delete product' }))

    expect(await screen.findByText(DELETE_CONFLICT_MESSAGE)).toBeInTheDocument()
    expect(screen.getByText('Tesouro Selic 2029')).toBeInTheDocument()
  })

  it('hides the add form and disables editing on a closed account', async () => {
    renderSection(true)
    await screen.findByText('Bitcoin')

    expect(screen.queryByRole('group', { name: 'Add product' })).not.toBeInTheDocument()
    expect((await findRow('Bitcoin')).getByRole('button', { name: 'Edit' })).toBeDisabled()
  })

  it('shows an empty state for an account without products', async () => {
    server.use(http.get('/api/investment-products', () => HttpResponse.json([])))
    renderSection()

    expect(await screen.findByText('No products yet.')).toBeInTheDocument()
  })

  it('shows a loading skeleton only when the first fetch is slow, then the rows', async () => {
    server.use(
      http.get('/api/investment-products', async () => {
        await delay(400)
        return HttpResponse.json(seedInvestmentProducts)
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
