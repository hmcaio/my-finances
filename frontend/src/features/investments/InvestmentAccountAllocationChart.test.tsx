import { describe, expect, it } from 'vitest'
import { screen, within } from '@testing-library/react'
import { seedInvestmentAccount } from '../../mocks/handlers/accounts'
import { seedAllocationByAccount } from '../../mocks/handlers/investmentAllocation'
import { renderWithQueryClient } from '../../test/renderWithQueryClient'
import { InvestmentAccountAllocationChart } from './InvestmentAccountAllocationChart'

function legend() {
  return within(screen.getByRole('list', { name: 'Allocation by account legend' }))
}

describe('InvestmentAccountAllocationChart', () => {
  it('shows one slice per account, summing every holding there', async () => {
    renderWithQueryClient(<InvestmentAccountAllocationChart />)

    expect(await screen.findByRole('img', { name: 'Allocation by account' })).toBeInTheDocument()
    const items = legend().getAllByRole('listitem')
    expect(items).toHaveLength(1)
    expect(items[0]).toHaveTextContent(seedInvestmentAccount.name)
    expect(items[0]).toHaveTextContent(seedAllocationByAccount[0].totalValue.toFixed(2))
  })

  it('has no clickable slices (flat, no drill-down)', async () => {
    renderWithQueryClient(<InvestmentAccountAllocationChart />)
    await screen.findByRole('img', { name: 'Allocation by account' })

    expect(screen.queryByRole('button')).not.toBeInTheDocument()
  })
})
