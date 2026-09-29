import { describe, expect, it } from 'vitest'
import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { server } from '../../mocks/server'
import { accountsWithInvestmentHandler, seedInvestmentAccount } from '../../mocks/handlers/accounts'
import { renderWithRouter, selectOption } from '../../test/testUtils'
import { InvestmentProductsListSection } from './InvestmentProductsListSection'

function table() {
  return screen.getByRole('table', { name: 'Investment products' })
}

describe('InvestmentProductsListSection', () => {
  it('defaults to the Open status filter, excluding a product whose only holding is closed', async () => {
    renderWithRouter(<InvestmentProductsListSection />)

    expect(await within(table()).findByText('Bitcoin')).toBeInTheDocument()
    expect(within(table()).getByText('Tesouro Selic 2029')).toBeInTheDocument()
    expect(within(table()).queryByText('Old CDB')).not.toBeInTheDocument()
  })

  it('a row links to the product detail page', async () => {
    renderWithRouter(<InvestmentProductsListSection />)

    const link = await within(table()).findByRole('link', { name: 'Bitcoin' })
    expect(link).toHaveAttribute('href', '/investment-products/iprod-btc')
  })

  it('the status filter set to All shows every product, each with its own status', async () => {
    const user = userEvent.setup()
    renderWithRouter(<InvestmentProductsListSection />)
    await within(table()).findByText('Bitcoin')

    await selectOption(user, 'Status filter', 'All')

    expect(await within(table()).findByText('Old CDB')).toBeInTheDocument()
    expect(within(table()).getAllByText('Closed')).toHaveLength(1)
    expect(within(table()).getAllByText('Open')).toHaveLength(2)
  })

  it('filters by account, has a holding there', async () => {
    // The default `/api/accounts` mock excludes the seeded INVESTMENT account for other pages'
    // isolation (its own doc comment); this page needs it in the Account filter's options.
    server.use(accountsWithInvestmentHandler)
    const user = userEvent.setup()
    renderWithRouter(<InvestmentProductsListSection />)
    await within(table()).findByText('Bitcoin')

    await selectOption(user, 'Account filter', seedInvestmentAccount.name)

    // Every seeded holding lives in the one seeded INVESTMENT account, so the open ones still show.
    expect(await within(table()).findByText('Bitcoin')).toBeInTheDocument()
    expect(within(table()).getByText('Tesouro Selic 2029')).toBeInTheDocument()
  })

  it('filters by name, case-insensitive', async () => {
    const user = userEvent.setup()
    renderWithRouter(<InvestmentProductsListSection />)
    await within(table()).findByText('Bitcoin')

    await user.type(screen.getByRole('textbox', { name: 'Name' }), 'bitcoin')

    expect(await within(table()).findByText('Bitcoin')).toBeInTheDocument()
    expect(within(table()).queryByText('Tesouro Selic 2029')).not.toBeInTheDocument()
  })
})
