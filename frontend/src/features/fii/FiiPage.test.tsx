import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { server } from '../../mocks/server'
import { seedInvestmentCategories } from '../../mocks/handlers/investmentCategories'
import { investmentProductsStore } from '../../mocks/handlers/investmentProducts'
import { seedFiiPortfolio } from '../../mocks/handlers/fiiPortfolio'
import { seedDividends } from '../../mocks/handlers/fiiDividends'
import type { InvestmentCategory } from '../../api/investments/investmentCategories'
import { renderWithQueryClient } from '../../test/renderWithQueryClient'
import { selectOption } from '../../test/testUtils'
import { FiiPage } from './FiiPage'

/**
 * The default seeds (`investmentCategories.ts`/`investmentProducts.ts`) have no "REITs (FIIs)"
 * sub-category or FII products - every other page's tests rely on that not being there. This
 * test's own FII fixtures are layered on top via `server.use`, following the same
 * "a row only a few tests need stays out of the default seed" convention as `seedFuelCategory`.
 */
const FII_SUBCATEGORY_ID = 'isub-fii-test'

function withFiiTaxonomy() {
  const categoriesWithFii: InvestmentCategory[] = seedInvestmentCategories.map((c) =>
    c.id === 'icat-variable'
      ? {
          ...c,
          subcategories: [...c.subcategories, { id: FII_SUBCATEGORY_ID, name: 'REITs (FIIs)' }],
        }
      : c,
  )
  server.use(http.get('/api/investment-categories', () => HttpResponse.json(categoriesWithFii)))
  investmentProductsStore.add({
    id: 'iprod-knri11',
    investmentCategoryId: 'icat-variable',
    investmentSubcategoryId: FII_SUBCATEGORY_ID,
    name: 'Kinea Renda Imobiliaria',
    additionalNotes: null,
    closed: false,
    ticker: 'KNRI11',
    segmentId: 'iseg-shoppings',
  })
  investmentProductsStore.add({
    id: 'iprod-hglg11',
    investmentCategoryId: 'icat-variable',
    investmentSubcategoryId: FII_SUBCATEGORY_ID,
    name: 'CSHG Logistica',
    additionalNotes: null,
    closed: false,
    ticker: 'HGLG11',
    segmentId: 'iseg-logistica',
  })
}

/**
 * Scopes queries to the portfolio table - "KNRI11"/"HGLG11" also appear in the allocation plan
 * editor, the chart legends and (once opened) the Register Dividend select, so an unscoped text
 * query on this page is ambiguous.
 */
function portfolioTable() {
  return within(screen.getByRole('table', { name: 'FII portfolio' }))
}

describe('FiiPage', () => {
  beforeEach(() => withFiiTaxonomy())
  afterEach(() => server.resetHandlers())

  it('lists the FII portfolio with ticker, cotas held and current value', async () => {
    renderWithQueryClient(<FiiPage />)

    const table = portfolioTable()
    expect(await table.findByText('KNRI11')).toBeInTheDocument()
    expect(table.getByText('HGLG11')).toBeInTheDocument()
    // "Contributed" is a low tablet-priority column (hidden behind the row's chevron in jsdom's
    // default tablet band) - "Current value" is always visible.
    expect(table.getByText(seedFiiPortfolio[0].currentValue.toFixed(2))).toBeInTheDocument()
  })

  it('renders all four allocation charts', async () => {
    renderWithQueryClient(<FiiPage />)

    expect(
      await screen.findByRole('img', { name: 'Actual allocation by ticker' }),
    ).toBeInTheDocument()
    expect(screen.getByRole('img', { name: 'Actual allocation by segment' })).toBeInTheDocument()
    expect(screen.getByRole('img', { name: 'Planned allocation by ticker' })).toBeInTheDocument()
    expect(screen.getByRole('img', { name: 'Planned allocation by segment' })).toBeInTheDocument()
  })

  it('sets an allocation plan summing to 100% and shows it saved', async () => {
    const user = userEvent.setup({ delay: null })
    renderWithQueryClient(<FiiPage />)
    await portfolioTable().findByText('KNRI11')

    const targetInputs = await screen.findAllByLabelText('Target %')
    await user.type(targetInputs[0], '60')
    await user.type(targetInputs[1], '40')
    await user.click(screen.getByRole('button', { name: 'Save allocation' }))

    expect(await screen.findByText('Saved.')).toBeInTheDocument()
  })

  it('rejects an allocation plan that does not sum to 100%', async () => {
    const user = userEvent.setup({ delay: null })
    renderWithQueryClient(<FiiPage />)
    await portfolioTable().findByText('KNRI11')

    const targetInputs = await screen.findAllByLabelText('Target %')
    await user.type(targetInputs[0], '50')
    await user.type(targetInputs[1], '40')

    expect(screen.getByRole('button', { name: 'Save allocation' })).toBeDisabled()
  })

  it('shows dividend history with totals by ticker and by month', async () => {
    renderWithQueryClient(<FiiPage />)

    expect(await screen.findByText('Totals by ticker')).toBeInTheDocument()
    expect(screen.getByText('Totals by month')).toBeInTheDocument()
    expect(await screen.findByText(seedDividends[0].date)).toBeInTheDocument()
  })

  it('opens the Register Dividend dialog with only FII products in the ticker picker', async () => {
    const user = userEvent.setup({ delay: null })
    renderWithQueryClient(<FiiPage />)
    await portfolioTable().findByText('KNRI11')

    await user.click(screen.getByRole('button', { name: 'Register dividend' }))
    const dialog = within(await screen.findByRole('dialog'))
    await user.click(dialog.getByRole('combobox', { name: 'Ticker' }))

    expect(await screen.findByRole('option', { name: 'KNRI11' })).toBeInTheDocument()
    expect(screen.getByRole('option', { name: 'HGLG11' })).toBeInTheDocument()
    expect(screen.queryByRole('option', { name: 'Bitcoin' })).not.toBeInTheDocument()
  })

  it('filters the status select to OPEN by default', async () => {
    renderWithQueryClient(<FiiPage />)

    await portfolioTable().findByText('KNRI11')
    expect(screen.getByRole('combobox', { name: 'Status' })).toHaveTextContent('Open')
  })

  it('switches to the ALL status and still shows both tickers', async () => {
    const user = userEvent.setup({ delay: null })
    renderWithQueryClient(<FiiPage />)
    await portfolioTable().findByText('KNRI11')

    await selectOption(user, 'Status', 'All')

    const table = portfolioTable()
    expect(await table.findByText('KNRI11')).toBeInTheDocument()
    expect(table.getByText('HGLG11')).toBeInTheDocument()
  })
})
