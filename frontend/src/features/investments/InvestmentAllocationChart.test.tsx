import { describe, expect, it } from 'vitest'
import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { server } from '../../mocks/server'
import {
  seedAllocationByCategory,
  seedAllocationBySubcategory,
} from '../../mocks/handlers/investmentAllocation'
import { expectLoadStates } from '../../test/loadStates'
import { InvestmentAllocationChart } from './InvestmentAllocationChart'
import { renderWithQueryClient } from '../../test/renderWithQueryClient'

function legend() {
  return within(screen.getByRole('list', { name: 'Allocation legend' }))
}

describe('InvestmentAllocationChart', () => {
  it('shows a slice per category with its value and share, and the total in the middle', async () => {
    renderWithQueryClient(<InvestmentAllocationChart />)

    expect(await screen.findByRole('img', { name: 'Allocation by category' })).toBeInTheDocument()
    const items = legend().getAllByRole('listitem')
    expect(items).toHaveLength(2)
    expect(items[0]).toHaveTextContent('Crypto900.00 (23.1%)')
    // Fixed Income has a stale product: starred.
    expect(items[1]).toHaveTextContent('Fixed Income*3000.00 (76.9%)')
    expect(screen.getByText('3900.00')).toBeInTheDocument()
  })

  it('explains the star in a footnote', async () => {
    renderWithQueryClient(<InvestmentAllocationChart />)

    expect(await screen.findByText(/newer than its latest snapshot/)).toBeInTheDocument()
  })

  it('has no footnote when every value is fresh', async () => {
    server.use(
      http.get('/api/investments/allocation', () =>
        HttpResponse.json(
          seedAllocationByCategory.map((row) => ({ ...row, needsSnapshot: false })),
        ),
      ),
    )
    renderWithQueryClient(<InvestmentAllocationChart />)
    await screen.findByRole('img', { name: 'Allocation by category' })

    expect(screen.queryByText(/newer than its latest snapshot/)).not.toBeInTheDocument()
    expect(legend().queryByText(/\*/)).not.toBeInTheDocument()
  })

  it('drills from a category into its sub-categories, summing to the category total', async () => {
    const user = userEvent.setup()
    renderWithQueryClient(<InvestmentAllocationChart />)
    await screen.findByRole('img', { name: 'Allocation by category' })

    await user.click(screen.getByRole('button', { name: 'Fixed Income: show sub-categories' }))

    expect(
      await screen.findByRole('img', { name: 'Allocation of Fixed Income by sub-category' }),
    ).toBeInTheDocument()
    const items = legend().getAllByRole('listitem')
    expect(items).toHaveLength(2)
    expect(items[0]).toHaveTextContent('CDB*1000.00 (33.3%)')
    expect(items[1]).toHaveTextContent('Tesouro Selic2000.00 (66.7%)')
    // The middle shows the category's total: the sum of its sub-category rows.
    expect(screen.getByText('3000.00')).toBeInTheDocument()
    // Other categories are gone, and sub-category slices don't drill any further.
    expect(screen.queryByText('Crypto')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /show sub-categories/ })).not.toBeInTheDocument()
  })

  it('goes back to all categories', async () => {
    const user = userEvent.setup()
    renderWithQueryClient(<InvestmentAllocationChart />)
    await screen.findByRole('img', { name: 'Allocation by category' })
    await user.click(screen.getByRole('button', { name: 'Fixed Income: show sub-categories' }))
    await screen.findByRole('img', { name: 'Allocation of Fixed Income by sub-category' })

    await user.click(screen.getByRole('button', { name: /All categories/ }))

    expect(await screen.findByRole('img', { name: 'Allocation by category' })).toBeInTheDocument()
    expect(legend().getByText('Crypto')).toBeInTheDocument()
  })

  it('labels the slice of products without a sub-category', async () => {
    const user = userEvent.setup()
    server.use(
      http.get('/api/investments/allocation', ({ request }) => {
        const bySub = new URL(request.url).searchParams.get('groupBy') === 'SUBCATEGORY'
        return HttpResponse.json(bySub ? seedAllocationBySubcategory : seedAllocationByCategory)
      }),
    )
    renderWithQueryClient(<InvestmentAllocationChart />)
    await screen.findByRole('img', { name: 'Allocation by category' })

    await user.click(screen.getByRole('button', { name: 'Crypto: show sub-categories' }))

    expect(await legend().findByText('No sub-category')).toBeInTheDocument()
  })

  it('shows an empty-state hint when there is nothing invested yet', async () => {
    server.use(http.get('/api/investments/allocation', () => HttpResponse.json([])))
    renderWithQueryClient(<InvestmentAllocationChart />)

    expect(await screen.findByText(/No investment values yet/)).toBeInTheDocument()
  })

  it('keeps a stale group with no value yet in the legend but out of the ring', async () => {
    server.use(
      http.get('/api/investments/allocation', () =>
        HttpResponse.json([
          { ...seedAllocationByCategory[0], totalValue: 0, needsSnapshot: true },
          seedAllocationByCategory[1],
        ]),
      ),
    )
    renderWithQueryClient(<InvestmentAllocationChart />)
    await screen.findByRole('img', { name: 'Allocation by category' })

    expect(legend().getAllByRole('listitem')[0]).toHaveTextContent('Crypto*0.00 (0.0%)')
    const ring = screen.getByRole('img', { name: 'Allocation by category' })
    expect(ring.querySelectorAll('path')).toHaveLength(1)
  })

  // The chart's load state is `combineLoadState` of both groupings; slowing/failing the shared
  // endpoint exercises the skeleton and the failure notice for either.
  expectLoadStates({
    render: () => renderWithQueryClient(<InvestmentAllocationChart />),
    url: '/api/investments/allocation',
    successBody: seedAllocationByCategory,
    loadedText: /Allocation by category|Crypto/,
  })
})
