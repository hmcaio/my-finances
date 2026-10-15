import { describe, expect, it } from 'vitest'
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { seedInvestmentCategories } from '../../mocks/handlers/investmentCategories'
import { expectLoadStates } from '../../test/loadStates'
import { findRow } from '../../test/testUtils'
import { InvestmentCategoriesPage } from './InvestmentCategoriesPage'
import { renderWithQueryClient } from '../../test/renderWithQueryClient'

const FIXED = seedInvestmentCategories.find((c) => c.name === 'Fixed Income')!
const CRYPTO = seedInvestmentCategories.find((c) => c.name === 'Crypto')!

async function expand(user: ReturnType<typeof userEvent.setup>, name: string) {
  await user.click(await screen.findByRole('button', { name: `Expand ${name}` }))
}

/**
 * F034/ADR 0028: InvestmentCategoriesPage (and its nested sub-categories) lost every mutation
 * affordance - every row at both levels is fixed, migration-seeded content. Expand/collapse per
 * category is the only interaction left.
 */
describe('InvestmentCategoriesPage', () => {
  it('renders the seeded categories collapsed, with their sub-category counts, and no mutation affordance', async () => {
    renderWithQueryClient(<InvestmentCategoriesPage />)

    for (const category of seedInvestmentCategories) {
      expect(await screen.findByText(category.name)).toBeInTheDocument()
    }
    expect((await findRow('Fixed Income')).getByText('2 sub-categories')).toBeInTheDocument()
    expect((await findRow('Crypto')).getByText('0 sub-categories')).toBeInTheDocument()
    expect(screen.queryByText('CDB')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /add/i })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Rename' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Delete' })).not.toBeInTheDocument()
  })

  it('expands a category to show its sub-categories and collapses it again', async () => {
    const user = userEvent.setup({ delay: null })
    renderWithQueryClient(<InvestmentCategoriesPage />)

    await expand(user, 'Fixed Income')

    for (const subcategory of FIXED.subcategories) {
      expect(screen.getByText(subcategory.name)).toBeInTheDocument()
    }
    // Only the expanded category shows its children.
    expect(screen.queryByText('ETFs')).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Collapse Fixed Income' }))
    expect(screen.queryByText('CDB')).not.toBeInTheDocument()
  })

  it('expanding one category does not affect another', async () => {
    const user = userEvent.setup({ delay: null })
    renderWithQueryClient(<InvestmentCategoriesPage />)

    await expand(user, 'Fixed Income')
    expect(screen.getByText('CDB')).toBeInTheDocument()
    expect(screen.queryByText('ETFs')).not.toBeInTheDocument()

    await expand(user, 'Variable Income')
    expect(screen.getByText('ETFs')).toBeInTheDocument()
    expect(screen.getByText('CDB')).toBeInTheDocument()
  })

  expectLoadStates({
    render: () => renderWithQueryClient(<InvestmentCategoriesPage />),
    url: '/api/investment-categories',
    successBody: seedInvestmentCategories,
    loadedText: CRYPTO.name,
  })
})
