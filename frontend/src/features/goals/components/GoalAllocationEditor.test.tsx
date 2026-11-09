import { describe, expect, it } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { delay, http, HttpResponse } from 'msw'
import { server } from '../../../mocks/server'
import {
  allocationExceedsProductHandler,
  goalsStore,
  seedGoalCar,
  seedGoalOld,
} from '../../../mocks/handlers/goals'
import { resolveErrorMessage } from '../../../test/utils/errorMessages'
import { selectOption } from '../../../test/utils/testUtils'
import { renderWithQueryClient } from '../../../test/utils/renderWithQueryClient'
import type { Goal } from '../../../api/goals/goals'
import { GoalAllocationEditor } from './GoalAllocationEditor'

function renderEditor(goal: Goal = seedGoalCar) {
  renderWithQueryClient(<GoalAllocationEditor goal={goal} />)
  return userEvent.setup()
}

/** Another active goal already holding 30% of Tesouro Selic. */
function fundHoldsThirtyOfSelic() {
  goalsStore.replace('goal-fund', (g) => ({
    ...g,
    allocations: [{ productId: 'iprod-selic', productName: 'Tesouro Selic 2029', percentage: 30 }],
  }))
}

/** The row exists as soon as the goal does; its capacity line needs the goals list. */
async function rowReady(name = 'Tesouro Selic 2029') {
  const row = await screen.findByRole('group', { name })
  await within(row).findByText(/^Available:/)
  return row
}

describe('GoalAllocationEditor', () => {
  it('lists the goal allocations with each product capacity and the unassigned remainder', async () => {
    renderEditor()

    const row = await rowReady()
    expect(within(row).getByLabelText('Percentage')).toHaveValue(60)
    // Only the archived goal also holds Selic, and archived goals do not count.
    expect(await within(row).findByText('Available: 100%')).toBeInTheDocument()
    expect(within(row).getByText('Unassigned after this goal: 40%')).toBeInTheDocument()
  })

  it('names the stored products at once, before the product list has loaded', async () => {
    server.use(
      http.get('/api/investment-products', async () => {
        await delay(500)
        return HttpResponse.json({
          content: [],
          page: { size: 100, number: 0, totalElements: 0, totalPages: 1 },
        })
      }),
    )
    renderEditor()

    const row = await screen.findByRole('group', { name: 'Tesouro Selic 2029' })
    expect(within(row).getByRole('combobox', { name: 'Product' })).toHaveTextContent(
      'Tesouro Selic 2029',
    )
  })

  it('counts the other active goals against the available share', async () => {
    fundHoldsThirtyOfSelic()
    renderEditor()

    const row = await rowReady()
    expect(await within(row).findByText('Available: 70%')).toBeInTheDocument()
    expect(within(row).getByText('Unassigned after this goal: 10%')).toBeInTheDocument()
  })

  it('rejects a percentage above what the other goals leave, and disables saving', async () => {
    fundHoldsThirtyOfSelic()
    const user = renderEditor()
    const row = await rowReady()

    await user.clear(within(row).getByLabelText('Percentage'))
    await user.type(within(row).getByLabelText('Percentage'), '80')

    expect(within(row).getByText('Only 70% of this product is available.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Save allocation' })).toBeDisabled()
  })

  it('saves the whole set, including a newly added product', async () => {
    let sentBody: unknown = null
    server.use(
      http.put('/api/goals/:id/allocations', async ({ request }) => {
        sentBody = await request.json()
        return HttpResponse.json([])
      }),
    )
    const user = renderEditor()
    await rowReady()

    await user.click(screen.getByRole('button', { name: 'Add product' }))
    const added = screen.getByRole('group', { name: 'New allocation' })
    await selectOption(user, 'Product', 'Bitcoin', within(added))
    await user.type(within(added).getByLabelText('Percentage'), '25')
    await user.click(screen.getByRole('button', { name: 'Save allocation' }))

    await waitFor(() =>
      expect(sentBody).toEqual({
        allocations: [
          { productId: 'iprod-selic', percentage: 60 },
          { productId: 'iprod-btc', percentage: 25 },
        ],
      }),
    )
  })

  it('removes a product and saves the smaller set', async () => {
    let sentBody: unknown = null
    server.use(
      http.put('/api/goals/:id/allocations', async ({ request }) => {
        sentBody = await request.json()
        return HttpResponse.json([])
      }),
    )
    const user = renderEditor()
    await rowReady()

    await user.click(screen.getByRole('button', { name: 'Remove Tesouro Selic 2029' }))
    await user.click(screen.getByRole('button', { name: 'Save allocation' }))

    await waitFor(() => expect(sentBody).toEqual({ allocations: [] }))
  })

  it('offers only open products that are not already chosen', async () => {
    const user = renderEditor()
    await rowReady()

    await user.click(screen.getByRole('button', { name: 'Add product' }))
    await user.click(
      within(screen.getByRole('group', { name: 'New allocation' })).getByRole('combobox', {
        name: 'Product',
      }),
    )

    const options = (await screen.findAllByRole('option')).map((o) => o.textContent)
    expect(options).toEqual(['Bitcoin'])
  })

  it('keeps Save disabled until something changes', async () => {
    const user = renderEditor()
    const row = await rowReady()
    const save = screen.getByRole('button', { name: 'Save allocation' })
    expect(save).toBeDisabled()

    await user.clear(within(row).getByLabelText('Percentage'))
    await user.type(within(row).getByLabelText('Percentage'), '50')

    expect(save).toBeEnabled()
  })

  it('shows the mapped exceeds-100% message when the save is refused', async () => {
    server.use(allocationExceedsProductHandler)
    const user = renderEditor()
    const row = await rowReady()
    await user.clear(within(row).getByLabelText('Percentage'))
    await user.type(within(row).getByLabelText('Percentage'), '50')

    await user.click(screen.getByRole('button', { name: 'Save allocation' }))

    expect(
      await screen.findByText(
        resolveErrorMessage('urn:my-finances:error:goal-allocation-exceeds-product'),
      ),
    ).toBeInTheDocument()
  })

  it('says so when there is nothing allocated yet', async () => {
    renderEditor({ ...seedGoalCar, allocations: [] })

    expect(await screen.findByText('No products allocated yet.')).toBeInTheDocument()
  })

  it('an archived goal cannot be reallocated', async () => {
    renderEditor(seedGoalOld)

    expect(
      await screen.findByText('Restore the goal to change its allocation.'),
    ).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Save allocation' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Add product' })).not.toBeInTheDocument()
  })
})
