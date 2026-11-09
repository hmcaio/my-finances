import { describe, expect, it } from 'vitest'
import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { server } from '../../mocks/server'
import { goalsStore } from '../../mocks/handlers/goals'
import { renderWithRouter } from '../../test/utils/testUtils'
import { GoalsWidget } from './GoalsWidget'

function renderWidget() {
  return renderWithRouter(<GoalsWidget />)
}

describe('GoalsWidget', () => {
  it('lists the flagged goals with a progress bar and one status line each', async () => {
    renderWidget()

    const region = await screen.findByRole('region', { name: 'Goals' })
    const car = within(
      within(region).getByRole('link', { name: 'Car' }).closest('li') as HTMLElement,
    )
    expect(car.getByRole('progressbar', { name: 'Progress of Car' })).toHaveAttribute(
      'aria-valuenow',
      '37.5',
    )
    expect(car.getByText('37.5%')).toBeInTheDocument()
    expect(car.getByText('Short by 5,000.00 by November 15, 2028')).toBeInTheDocument()

    const fund = within(
      within(region).getByRole('link', { name: 'Emergency fund' }).closest('li') as HTMLElement,
    )
    expect(fund.getByText('Reached in 30 months')).toBeInTheDocument()
  })

  it('says a dated goal on track is on track and a dateless one far away is not reached in 50 years', async () => {
    server.use(
      http.get('/api/goals/dashboard', () =>
        HttpResponse.json([
          {
            id: 'goal-a',
            name: 'Trip',
            targetAmount: 1000,
            targetDate: '2027-05-01',
            allocatedValue: 900,
            progressPercent: 90,
            targetReached: false,
            projectedShortfall: -50,
            monthsToTarget: null,
          },
          {
            id: 'goal-b',
            name: 'Someday',
            targetAmount: 9000000,
            targetDate: null,
            allocatedValue: 10,
            progressPercent: 0,
            targetReached: false,
            projectedShortfall: null,
            monthsToTarget: null,
          },
        ]),
      ),
    )
    renderWidget()

    expect(await screen.findByText('On track')).toBeInTheDocument()
    expect(screen.getByText('Not reached within 50 years')).toBeInTheDocument()
  })

  it('shows a goal that passed its target with the bar full and the percentage uncapped', async () => {
    server.use(
      http.get('/api/goals/dashboard', () =>
        HttpResponse.json([
          {
            id: 'goal-a',
            name: 'Trip',
            targetAmount: 1000,
            targetDate: null,
            allocatedValue: 1500,
            progressPercent: 150,
            targetReached: true,
            projectedShortfall: null,
            monthsToTarget: 0,
          },
        ]),
      ),
    )
    renderWidget()

    expect(await screen.findByText('150.0%')).toBeInTheDocument()
    expect(screen.getByRole('progressbar', { name: 'Progress of Trip' })).toHaveAttribute(
      'aria-valuenow',
      '100',
    )
    expect(screen.getByText('Target reached')).toBeInTheDocument()
  })

  it('links each goal to its page and offers View all', async () => {
    renderWidget()

    expect(await screen.findByRole('link', { name: 'Car' })).toHaveAttribute(
      'href',
      '/goals/goal-car',
    )
    expect(screen.getByRole('link', { name: 'View all' })).toHaveAttribute('href', '/goals')
  })

  it('is hidden once the data loads and no goal is flagged', async () => {
    goalsStore.replace('goal-car', (g) => ({ ...g, showOnDashboard: false }))
    goalsStore.replace('goal-fund', (g) => ({ ...g, showOnDashboard: false }))
    renderWidget()

    // Wait for the dashboard request to settle, then nothing is rendered.
    await new Promise((resolve) => setTimeout(resolve, 100))
    expect(screen.queryByRole('region', { name: 'Goals' })).not.toBeInTheDocument()
    expect(screen.queryByText('View all')).not.toBeInTheDocument()
  })

  it('shows a load failure with a retry', async () => {
    server.use(
      http.get('/api/goals/dashboard', () =>
        HttpResponse.json({ type: 'urn:my-finances:error:internal-error' }, { status: 500 }),
      ),
    )
    renderWidget()
    const user = userEvent.setup()

    const retry = await screen.findByRole('button', { name: 'Retry' })
    server.resetHandlers()
    await user.click(retry)

    expect(await screen.findByRole('link', { name: 'Car' })).toBeInTheDocument()
  })
})
