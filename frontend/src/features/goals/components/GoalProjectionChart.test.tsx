import { describe, expect, it } from 'vitest'
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { server } from '../../../mocks/server'
import { projectionFor, seedGoalCar, seedGoalFund } from '../../../mocks/handlers/goals'
import { renderWithQueryClient } from '../../../test/utils/renderWithQueryClient'
import type { Goal } from '../../../api/goals/goals'
import { GoalProjectionChart } from './GoalProjectionChart'

function renderChart(goal: Goal) {
  return renderWithQueryClient(<GoalProjectionChart goal={goal} />)
}

describe('GoalProjectionChart', () => {
  it('draws one line per scenario and a target line, with a legend', async () => {
    const { container } = renderChart(seedGoalCar)

    const chart = await screen.findByRole('img', {
      name: 'Projected value of the goal in three scenarios, against the target',
    })
    expect(chart.querySelectorAll('path[data-scenario]')).toHaveLength(3)
    expect(
      [...chart.querySelectorAll('path[data-scenario]')].map((p) =>
        p.getAttribute('data-scenario'),
      ),
    ).toEqual(['PESSIMISTIC', 'BASE', 'OPTIMISTIC'])
    expect(chart.querySelectorAll('line[data-target]')).toHaveLength(1)
    expect(screen.getByText('Target 80,000.00')).toBeInTheDocument()
    expect(container.textContent).toContain('Pessimistic (2%)')
    expect(container.textContent).toContain('Base (4%)')
    expect(container.textContent).toContain('Optimistic (6%)')
  })

  it("captions the values as today's money with the latest snapshot date", async () => {
    renderChart(seedGoalCar)

    expect(
      await screen.findByText("Values in today's money. Latest snapshot: November 1, 2026."),
    ).toBeInTheDocument()
  })

  it('says when no snapshot has been recorded', async () => {
    server.use(
      http.get('/api/goals/:id/projection', () =>
        HttpResponse.json({ ...projectionFor(seedGoalCar), latestSnapshotDate: null }),
      ),
    )
    renderChart(seedGoalCar)

    expect(
      await screen.findByText("Values in today's money. No snapshot recorded yet."),
    ).toBeInTheDocument()
  })

  it('tells a dateless goal how long each scenario takes, or that it is not reached in 50 years', async () => {
    const dateless = projectionFor(seedGoalFund)
    server.use(
      http.get('/api/goals/:id/projection', () =>
        HttpResponse.json({
          ...dateless,
          scenarios: dateless.scenarios.map((s) =>
            s.scenario === 'PESSIMISTIC' ? { ...s, monthsToTarget: null } : s,
          ),
        }),
      ),
    )
    renderChart(seedGoalFund)

    expect(
      await screen.findByText(/Pessimistic \(1%\).*not reached within 50 years/),
    ).toBeInTheDocument()
    expect(screen.getByText(/Base \(3%\).*reached in 27 months/)).toBeInTheDocument()
    expect(screen.getByText(/Optimistic \(5%\).*reached in 25 months/)).toBeInTheDocument()
  })

  it('shows a load failure with a retry', async () => {
    server.use(
      http.get('/api/goals/:id/projection', () =>
        HttpResponse.json({ type: 'urn:my-finances:error:internal-error' }, { status: 500 }),
      ),
    )
    renderChart(seedGoalCar)
    const user = userEvent.setup()

    const retry = await screen.findByRole('button', { name: 'Retry' })
    server.resetHandlers()
    await user.click(retry)

    expect(
      await screen.findByRole('img', {
        name: 'Projected value of the goal in three scenarios, against the target',
      }),
    ).toBeInTheDocument()
  })
})
