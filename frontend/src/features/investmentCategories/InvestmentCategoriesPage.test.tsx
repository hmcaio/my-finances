import { describe, expect, it } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { delay, http, HttpResponse } from 'msw'
import { server } from '../../mocks/server'
import {
  investmentCategoryCreateConflictHandler,
  investmentCategoryDeleteConflictHandler,
  seedInvestmentCategories,
} from '../../mocks/handlers/investmentCategories'
import {
  investmentSubcategoryCreateConflictHandler,
  investmentSubcategoryDeleteConflictHandler,
} from '../../mocks/handlers/investmentSubcategories'
import {
  CONFLICT_MESSAGE as CATEGORY_CONFLICT_MESSAGE,
  DUPLICATE_NAME_MESSAGE as CATEGORY_DUPLICATE_NAME_MESSAGE,
} from '../../api/investments/investmentCategories'
import {
  CONFLICT_MESSAGE as SUBCATEGORY_CONFLICT_MESSAGE,
  DUPLICATE_NAME_MESSAGE as SUBCATEGORY_DUPLICATE_NAME_MESSAGE,
} from '../../api/investments/investmentSubcategories'
import { findRow } from '../../test/testUtils'
import { InvestmentCategoriesPage } from './InvestmentCategoriesPage'
import { renderWithQueryClient } from '../../test/renderWithQueryClient'

const FIXED = seedInvestmentCategories.find((c) => c.name === 'Fixed Income')!
const CRYPTO = seedInvestmentCategories.find((c) => c.name === 'Crypto')!

async function expand(user: ReturnType<typeof userEvent.setup>, name: string) {
  await user.click(await screen.findByRole('button', { name: `Expand ${name}` }))
}

describe('InvestmentCategoriesPage', () => {
  it('renders the seeded categories collapsed, with their sub-category counts', async () => {
    renderWithQueryClient(<InvestmentCategoriesPage />)

    for (const category of seedInvestmentCategories) {
      expect(await screen.findByText(category.name)).toBeInTheDocument()
    }
    expect((await findRow('Fixed Income')).getByText('2 sub-categories')).toBeInTheDocument()
    expect((await findRow('Crypto')).getByText('0 sub-categories')).toBeInTheDocument()
    expect(screen.queryByText('CDB')).not.toBeInTheDocument()
  })

  it('expands a category to show its sub-categories and collapses it again', async () => {
    const user = userEvent.setup()
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

  it('adds a category', async () => {
    const user = userEvent.setup()
    renderWithQueryClient(<InvestmentCategoriesPage />)
    await screen.findByText(CRYPTO.name)

    await user.type(screen.getByRole('textbox', { name: 'Category name' }), 'Real Estate')
    await user.click(screen.getByRole('button', { name: 'Add category' }))

    expect(await screen.findByText('Real Estate')).toBeInTheDocument()
  })

  it('adds a sub-category under its category', async () => {
    const user = userEvent.setup()
    renderWithQueryClient(<InvestmentCategoriesPage />)
    await expand(user, 'Fixed Income')

    await user.type(screen.getByLabelText('New sub-category in Fixed Income'), 'LCI')
    await user.click(screen.getByRole('button', { name: 'Add sub-category to Fixed Income' }))

    expect(await screen.findByText('LCI')).toBeInTheDocument()
    // The category's counter follows.
    expect((await findRow('Fixed Income')).getByText('3 sub-categories')).toBeInTheDocument()
  })

  it('renames a category inline', async () => {
    const user = userEvent.setup()
    renderWithQueryClient(<InvestmentCategoriesPage />)
    await screen.findByText(CRYPTO.name)

    const row = await findRow('Crypto')
    await user.click(row.getByRole('button', { name: 'Rename' }))
    const input = row.getByRole('textbox', { name: 'Category name' })
    await user.clear(input)
    await user.type(input, 'Digital Assets')
    await user.click(row.getByRole('button', { name: 'Save' }))

    expect(await screen.findByText('Digital Assets')).toBeInTheDocument()
    expect(screen.queryByText('Crypto')).not.toBeInTheDocument()
  })

  it('renames a sub-category inline', async () => {
    const user = userEvent.setup()
    renderWithQueryClient(<InvestmentCategoriesPage />)
    await expand(user, 'Fixed Income')

    const row = await findRow('CDB')
    await user.click(row.getByRole('button', { name: 'Rename' }))
    const input = row.getByRole('textbox', { name: 'Sub-category name' })
    await user.clear(input)
    await user.type(input, 'CDB / RDB')
    await user.click(row.getByRole('button', { name: 'Save' }))

    expect(await screen.findByText('CDB / RDB')).toBeInTheDocument()
    expect(screen.queryByText('CDB')).not.toBeInTheDocument()
  })

  it('deletes a category', async () => {
    const user = userEvent.setup()
    renderWithQueryClient(<InvestmentCategoriesPage />)
    await screen.findByText(CRYPTO.name)

    await user.click((await findRow('Crypto')).getByRole('button', { name: 'Delete' }))

    await waitFor(() => expect(screen.queryByText('Crypto')).not.toBeInTheDocument())
  })

  it('deletes a sub-category', async () => {
    const user = userEvent.setup()
    renderWithQueryClient(<InvestmentCategoriesPage />)
    await expand(user, 'Fixed Income')

    await user.click((await findRow('CDB')).getByRole('button', { name: 'Delete' }))

    await waitFor(() => expect(screen.queryByText('CDB')).not.toBeInTheDocument())
    expect(screen.getByText('Tesouro Selic')).toBeInTheDocument()
  })

  it('surfaces the category 409 message and keeps the row when delete is refused', async () => {
    server.use(investmentCategoryDeleteConflictHandler)
    const user = userEvent.setup()
    renderWithQueryClient(<InvestmentCategoriesPage />)
    await screen.findByText(FIXED.name)

    await user.click((await findRow('Fixed Income')).getByRole('button', { name: 'Delete' }))

    expect(await screen.findByText(CATEGORY_CONFLICT_MESSAGE)).toBeInTheDocument()
    expect(screen.getByText('Fixed Income')).toBeInTheDocument()
  })

  it('surfaces the sub-category 409 message and keeps the row when delete is refused', async () => {
    server.use(investmentSubcategoryDeleteConflictHandler)
    const user = userEvent.setup()
    renderWithQueryClient(<InvestmentCategoriesPage />)
    await expand(user, 'Fixed Income')

    await user.click((await findRow('CDB')).getByRole('button', { name: 'Delete' }))

    expect(await screen.findByText(SUBCATEGORY_CONFLICT_MESSAGE)).toBeInTheDocument()
    expect(screen.getByText('CDB')).toBeInTheDocument()
  })

  it('surfaces the duplicate-name 409 on add for both levels', async () => {
    server.use(investmentCategoryCreateConflictHandler, investmentSubcategoryCreateConflictHandler)
    const user = userEvent.setup()
    renderWithQueryClient(<InvestmentCategoriesPage />)
    await expand(user, 'Fixed Income')

    await user.type(screen.getByLabelText('New sub-category in Fixed Income'), 'CDB')
    await user.click(screen.getByRole('button', { name: 'Add sub-category to Fixed Income' }))
    expect(await screen.findByText(SUBCATEGORY_DUPLICATE_NAME_MESSAGE)).toBeInTheDocument()

    await user.type(screen.getByRole('textbox', { name: 'Category name' }), 'Crypto')
    await user.click(screen.getByRole('button', { name: 'Add category' }))
    expect(await screen.findByText(CATEGORY_DUPLICATE_NAME_MESSAGE)).toBeInTheDocument()
  })

  it('keeps a sub-category draft per category', async () => {
    const user = userEvent.setup()
    renderWithQueryClient(<InvestmentCategoriesPage />)
    await expand(user, 'Fixed Income')
    await expand(user, 'Variable Income')

    await user.type(screen.getByLabelText('New sub-category in Fixed Income'), 'LCA')

    expect(screen.getByLabelText('New sub-category in Variable Income')).toHaveValue('')
  })

  it('shows a loading skeleton only when the first fetch is slow, then the rows', async () => {
    server.use(
      http.get('/api/investment-categories', async () => {
        await delay(400)
        return HttpResponse.json(seedInvestmentCategories)
      }),
    )
    renderWithQueryClient(<InvestmentCategoriesPage />)

    expect(await screen.findByText('Loading…')).toBeInTheDocument()
    expect(await screen.findByText(CRYPTO.name)).toBeInTheDocument()
    expect(screen.queryByText('Loading…')).not.toBeInTheDocument()
  })

  it('shows a retryable notice when the list cannot be loaded', async () => {
    server.use(
      http.get('/api/investment-categories', () => new HttpResponse(null, { status: 500 })),
    )
    const user = userEvent.setup()
    renderWithQueryClient(<InvestmentCategoriesPage />)

    expect(await screen.findByText(/Could not load data/)).toBeInTheDocument()
    server.resetHandlers()
    await user.click(screen.getByRole('button', { name: 'Retry' }))

    expect(await screen.findByText(CRYPTO.name)).toBeInTheDocument()
  })
})
