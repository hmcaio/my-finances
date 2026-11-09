import { describe, expect, it } from 'vitest'
import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { delay, http, HttpResponse } from 'msw'
import { Route, Routes } from 'react-router-dom'
import { server } from '../../mocks/server'
import { renderWithRouter } from '../../test/utils/testUtils'
import { GoalDetailPage } from './GoalDetailPage'

function renderDetail(id: string) {
  renderWithRouter(
    <Routes>
      <Route path="/goals/:id" element={<GoalDetailPage />} />
    </Routes>,
    { initialEntries: [`/goals/${id}`] },
  )
  return userEvent.setup()
}

describe('GoalDetailPage', () => {
  it('shows the goal summary, its allocation and the history with the projection in one chart', async () => {
    renderDetail('goal-car')

    expect(await screen.findByRole('heading', { level: 1, name: 'Car' })).toBeInTheDocument()
    expect(screen.getByText('Target date: November 15, 2028')).toBeInTheDocument()
    expect(screen.getByText('30,000.00 of 80,000.00 (37.5%)')).toBeInTheDocument()
    expect(screen.getByText('Planned monthly contribution: 2,000.00')).toBeInTheDocument()
    expect(screen.getByText('Real returns: 2% / 4% / 6%')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Allocation' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Progress and projection' })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Progress history' })).not.toBeInTheDocument()
    expect(await screen.findByRole('group', { name: 'Tesouro Selic 2029' })).toBeInTheDocument()
    expect(
      await screen.findByRole('img', {
        name: 'Value of the goal: history as of its current allocation, then three projected scenarios, against the target',
      }),
    ).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Back to goals' })).toHaveAttribute('href', '/goals')
  })

  it('badges a goal whose target is reached and shows its status when not active', async () => {
    server.use(
      http.get('/api/goals/:id', () =>
        HttpResponse.json({
          id: 'goal-car',
          name: 'Car',
          notes: 'The family car',
          targetAmount: 80000,
          targetDate: null,
          plannedMonthlyContribution: 0,
          returnPessimistic: 1,
          returnBase: 2,
          returnOptimistic: 3,
          status: 'ACHIEVED',
          showOnDashboard: true,
          allocatedValue: 90000,
          progressPercent: 112.5,
          targetReached: true,
          projectedShortfall: null,
          monthsToTarget: 0,
          allocations: [],
        }),
      ),
    )
    renderDetail('goal-car')

    expect(await screen.findByText('Target reached')).toBeInTheDocument()
    expect(screen.getByText('Achieved')).toBeInTheDocument()
    expect(screen.getByText('The family car')).toBeInTheDocument()
    expect(screen.getByText('No target date')).toBeInTheDocument()
  })

  it('shows a delayed skeleton while the goal loads', async () => {
    server.use(
      http.get('/api/goals/:id', async () => {
        await delay(400)
        return HttpResponse.json({}, { status: 500 })
      }),
    )
    renderDetail('goal-car')

    expect(await screen.findByRole('status', { name: 'Loading the goal' })).toBeInTheDocument()
  })

  it('says the goal was not found for an unknown id', async () => {
    renderDetail('goal-nope')

    expect(await screen.findByText('Goal not found.')).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Allocation' })).not.toBeInTheDocument()
  })

  it('edits the goal from the page', async () => {
    const user = renderDetail('goal-car')
    await screen.findByRole('heading', { level: 1, name: 'Car' })

    await user.click(screen.getByRole('button', { name: 'Edit goal' }))
    const dialog = await screen.findByRole('dialog', { name: 'Edit goal' })
    await user.clear(within(dialog).getByLabelText('Name'))
    await user.type(within(dialog).getByLabelText('Name'), 'New car')
    await user.click(within(dialog).getByRole('button', { name: 'Save' }))

    expect(await screen.findByRole('heading', { level: 1, name: 'New car' })).toBeInTheDocument()
  })
})
