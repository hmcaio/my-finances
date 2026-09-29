import { describe, expect, it } from 'vitest'
import { screen, within } from '@testing-library/react'
import { seedAllocationBySubcategory } from '../../mocks/handlers/investmentAllocation'
import { renderWithQueryClient } from '../../test/renderWithQueryClient'
import { InvestmentSubcategoryAllocationChart } from './InvestmentSubcategoryAllocationChart'

function legend() {
  return within(screen.getByRole('list', { name: 'Allocation by sub-category legend' }))
}

describe('InvestmentSubcategoryAllocationChart', () => {
  it('shows one flat slice per sub-category row, with no drill-down control', async () => {
    renderWithQueryClient(<InvestmentSubcategoryAllocationChart />)

    expect(
      await screen.findByRole('img', { name: 'Allocation by sub-category' }),
    ).toBeInTheDocument()
    const items = legend().getAllByRole('listitem')
    // Crypto's null slice, CDB and Tesouro Selic - every SUBCATEGORY row, flat, at once.
    expect(items).toHaveLength(seedAllocationBySubcategory.length)
    expect(items[0]).toHaveTextContent('No sub-category')
    expect(items[1]).toHaveTextContent('CDB*')
    expect(items[2]).toHaveTextContent('Tesouro Selic')
    expect(screen.queryByRole('button')).not.toBeInTheDocument()
  })

  it('its total matches the whole portfolio (same as the category chart)', async () => {
    renderWithQueryClient(<InvestmentSubcategoryAllocationChart />)
    await screen.findByRole('img', { name: 'Allocation by sub-category' })

    const total = seedAllocationBySubcategory.reduce((sum, row) => sum + row.totalValue, 0)
    expect(screen.getByText(total.toFixed(2))).toBeInTheDocument()
  })
})
