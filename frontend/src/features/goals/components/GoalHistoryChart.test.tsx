import { describe, expect, it } from 'vitest'
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { delay, http, HttpResponse } from 'msw'
import { server } from '../../../mocks/server'
import { renderWithQueryClient } from '../../../test/utils/renderWithQueryClient'
import { GoalHistoryChart } from './GoalHistoryChart'

const CHART_NAME = 'Value of the goal over time, as of its current allocation'

describe('GoalHistoryChart', () => {
  it('draws the monthly values and labels them as of the current allocation', async () => {
    renderWithQueryClient(<GoalHistoryChart goalId="goal-car" />)

    const chart = await screen.findByRole('img', { name: CHART_NAME })
    expect(chart.querySelectorAll('path[data-history]')).toHaveLength(1)
    expect(chart.querySelectorAll('circle')).toHaveLength(3)
    expect(
      screen.getByText(
        "As of the current allocation: today's split applied to each product's past value, in today's money.",
      ),
    ).toBeInTheDocument()
  })

  it('puts each point value in its tooltip', async () => {
    renderWithQueryClient(<GoalHistoryChart goalId="goal-car" />)

    const chart = await screen.findByRole('img', { name: CHART_NAME })
    expect(chart.textContent).toContain('Oct 2026: 10,000.00')
    expect(chart.textContent).toContain('Nov 2026: 12,000.00')
  })

  it('draws a single month as a lone point', async () => {
    server.use(
      http.get('/api/goals/:id/history', () =>
        HttpResponse.json([{ month: '2026-11', value: 500 }]),
      ),
    )
    renderWithQueryClient(<GoalHistoryChart goalId="goal-car" />)

    const chart = await screen.findByRole('img', { name: CHART_NAME })
    expect(chart.querySelectorAll('circle')).toHaveLength(1)
  })

  it('shows a delayed skeleton while loading', async () => {
    server.use(
      http.get('/api/goals/:id/history', async () => {
        await delay(400)
        return HttpResponse.json([])
      }),
    )
    renderWithQueryClient(<GoalHistoryChart goalId="goal-car" />)

    expect(await screen.findByRole('status', { name: 'Loading history' })).toBeInTheDocument()
  })

  it('says so when there is no history', async () => {
    server.use(http.get('/api/goals/:id/history', () => HttpResponse.json([])))
    renderWithQueryClient(<GoalHistoryChart goalId="goal-car" />)

    expect(await screen.findByText('No history to show yet.')).toBeInTheDocument()
  })

  it('shows a load failure with a retry', async () => {
    server.use(
      http.get('/api/goals/:id/history', () =>
        HttpResponse.json({ type: 'urn:my-finances:error:internal-error' }, { status: 500 }),
      ),
    )
    renderWithQueryClient(<GoalHistoryChart goalId="goal-car" />)
    const user = userEvent.setup()

    const retry = await screen.findByRole('button', { name: 'Retry' })
    server.resetHandlers()
    await user.click(retry)

    expect(await screen.findByRole('img', { name: CHART_NAME })).toBeInTheDocument()
  })
})
