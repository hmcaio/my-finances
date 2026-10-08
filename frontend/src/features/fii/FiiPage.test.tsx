import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { server } from '../../mocks/server'
import { seedAccounts, seedInvestmentAccount } from '../../mocks/handlers/accounts'
import { seedInvestmentCategories } from '../../mocks/handlers/investmentCategories'
import { investmentHoldingsStore } from '../../mocks/handlers/investmentHoldings'
import { investmentProductsStore } from '../../mocks/handlers/investmentProducts'
import { seedFiiPortfolio } from '../../mocks/handlers/fiiPortfolio'
import { seedDividends } from '../../mocks/handlers/fiiDividends'
import type { InvestmentCategory } from '../../api/investments/investmentCategories'
import { renderWithQueryClient } from '../../test/renderWithQueryClient'
import { selectOption } from '../../test/testUtils'
import { currentMonth } from '../../utils/localDate'
import { FiiPage } from './FiiPage'

/** The month after `currentMonth()`, `YYYY-MM` (handles the December-to-January rollover). */
function nextMonth(): string {
  const [year, month] = currentMonth().split('-').map(Number)
  const next = month === 12 ? { y: year + 1, m: 1 } : { y: year, m: month + 1 }
  return `${next.y}-${String(next.m).padStart(2, '0')}`
}

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
  investmentHoldingsStore.add({
    id: 'iholding-knri11',
    productId: 'iprod-knri11',
    accountId: seedInvestmentAccount.id,
    closedDate: null,
    closed: false,
    additionalNotes: null,
    hasHistory: false,
    needsSnapshot: false,
    latestSnapshot: null,
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

  it('renders both nested allocation charts with tickers nested under their segment', async () => {
    renderWithQueryClient(<FiiPage />)

    expect(await screen.findByRole('img', { name: 'Actual allocation' })).toBeInTheDocument()
    const actualLegend = screen.getByRole('list', { name: 'Actual allocation legend' })
    expect(within(actualLegend).getByText('Shoppings')).toBeInTheDocument()
    expect(within(actualLegend).getByText('KNRI11')).toBeInTheDocument()

    expect(screen.getByRole('img', { name: 'Planned allocation' })).toBeInTheDocument()
    const plannedLegend = screen.getByRole('list', { name: 'Planned allocation legend' })
    expect(within(plannedLegend).getByText('Logistica')).toBeInTheDocument()
    expect(within(plannedLegend).getByText('HGLG11')).toBeInTheDocument()
  })

  it('sets an allocation plan summing to 100% and shows it saved', async () => {
    const user = userEvent.setup({ delay: null })
    renderWithQueryClient(<FiiPage />)
    await portfolioTable().findByText('KNRI11')
    await user.click(screen.getByRole('tab', { name: 'Allocation Plan' }))

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
    await user.click(screen.getByRole('tab', { name: 'Allocation Plan' }))

    const targetInputs = await screen.findAllByLabelText('Target %')
    await user.type(targetInputs[0], '50')
    await user.type(targetInputs[1], '40')

    expect(screen.getByRole('button', { name: 'Save allocation' })).toBeDisabled()
  })

  it('shows dividend history with totals by ticker and by month', async () => {
    const user = userEvent.setup({ delay: null })
    renderWithQueryClient(<FiiPage />)
    await portfolioTable().findByText('KNRI11')
    await user.click(screen.getByRole('tab', { name: 'Dividends' }))

    expect(await screen.findByText('Totals by ticker')).toBeInTheDocument()
    expect(screen.getByText('Totals by month')).toBeInTheDocument()
    expect(await screen.findByText(seedDividends[0].date)).toBeInTheDocument()
  })

  it('defaults to the Dividends tab, and switching to Allocation Plan hides it', async () => {
    const user = userEvent.setup({ delay: null })
    renderWithQueryClient(<FiiPage />)
    await portfolioTable().findByText('KNRI11')

    expect(await screen.findByText('Totals by ticker')).toBeInTheDocument()

    await user.click(screen.getByRole('tab', { name: 'Allocation Plan' }))

    expect(screen.queryByText('Totals by ticker')).not.toBeInTheDocument()
    expect(await screen.findAllByLabelText('Target %')).toHaveLength(2)
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

  it('shows the account name, not the raw holding id, in the Holding picker', async () => {
    // The default accounts handler excludes the investment account from the list (every other
    // page only ever needs it absent, e.g. the generic transaction form's account picker) - this
    // is the one place that needs it present, to resolve a holding's accountId to a name.
    server.use(
      http.get('/api/accounts', () => HttpResponse.json([...seedAccounts, seedInvestmentAccount])),
    )
    const user = userEvent.setup({ delay: null })
    renderWithQueryClient(<FiiPage />)
    await portfolioTable().findByText('KNRI11')

    await user.click(screen.getByRole('button', { name: 'Register dividend' }))
    const dialog = within(await screen.findByRole('dialog'))
    await selectOption(user, 'Ticker', 'KNRI11', dialog)
    await waitFor(() => expect(screen.queryByRole('listbox')).not.toBeInTheDocument())
    await user.click(dialog.getByRole('combobox', { name: 'Holding' }))

    expect(
      await screen.findByRole('option', { name: seedInvestmentAccount.name }),
    ).toBeInTheDocument()
    expect(screen.queryByRole('option', { name: 'iholding-knri11' })).not.toBeInTheDocument()
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

  it('defaults the month picker to the current month', async () => {
    renderWithQueryClient(<FiiPage />)

    await portfolioTable().findByText('KNRI11')
    expect(screen.getByLabelText('Month')).toHaveValue(currentMonth())
  })

  it('clamps a future month back to the current month', async () => {
    renderWithQueryClient(<FiiPage />)
    await portfolioTable().findByText('KNRI11')

    fireEvent.change(screen.getByLabelText('Month'), { target: { value: nextMonth() } })

    expect(screen.getByLabelText('Month')).toHaveValue(currentMonth())
  })

  it('hides the needsSnapshot badge once a past month is selected, and shows it again for the current month', async () => {
    renderWithQueryClient(<FiiPage />)
    const table = portfolioTable()
    await table.findByText('HGLG11')
    // HGLG11's seed row has needsSnapshot: true, so today's value carries the "*" badge.
    const flaggedValue = `${seedFiiPortfolio[1].currentValue.toFixed(2)} *`
    const plainValue = seedFiiPortfolio[1].currentValue.toFixed(2)
    expect(table.getByText(flaggedValue)).toBeInTheDocument()

    fireEvent.change(screen.getByLabelText('Month'), { target: { value: '2026-01' } })

    // Wait for the refetch under the new month to land (the table goes through a loading
    // skeleton in between, where neither value is present) before asserting on its result.
    await waitFor(() => {
      expect(portfolioTable().getByText(plainValue)).toBeInTheDocument()
    })
    expect(portfolioTable().queryByText(flaggedValue)).not.toBeInTheDocument()

    fireEvent.change(screen.getByLabelText('Month'), { target: { value: currentMonth() } })

    await waitFor(() => {
      expect(portfolioTable().getByText(flaggedValue)).toBeInTheDocument()
    })
  })
})
