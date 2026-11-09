import { describe, expect, it } from 'vitest'
import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { delay, http, HttpResponse } from 'msw'
import { server } from '../../../mocks/server'
import { projectionFor, seedGoalCar, seedGoalFund } from '../../../mocks/handlers/goals'
import { renderWithQueryClient } from '../../../test/utils/renderWithQueryClient'
import type { Goal, GoalPoint } from '../../../api/goals/goals'
import { GoalTimelineChart } from './GoalTimelineChart'

const CHART_NAME =
  'Value of the goal: history as of its current allocation, then three projected scenarios, against the target'

function renderChart(goal: Goal) {
  return renderWithQueryClient(<GoalTimelineChart goal={goal} />)
}

/** The x of the first (`M`) or last point of a path's `d`. */
function pathX(path: Element | null, which: 'first' | 'last'): number {
  const coordinates = [...(path?.getAttribute('d') ?? '').matchAll(/[ML]([-\d.]+),([-\d.]+)/g)]
  const match = which === 'first' ? coordinates[0] : coordinates[coordinates.length - 1]
  return Number(match[1])
}

function pathPointCount(path: Element | null): number {
  return [...(path?.getAttribute('d') ?? '').matchAll(/[ML]/g)].length
}

/** A long dated projection: `count` monthly points per scenario starting at 2026-11. */
function longProjection(goal: Goal, count: number) {
  const base = projectionFor(goal)
  return {
    ...base,
    scenarios: base.scenarios.map((scenario) => ({
      ...scenario,
      points: Array.from({ length: count }, (_, index): GoalPoint => {
        const month = 10 + index
        const year = 2026 + Math.floor(month / 12)
        return {
          month: `${year}-${String((month % 12) + 1).padStart(2, '0')}`,
          value: 12000 + index * 500,
        }
      }),
    })),
  }
}

describe('GoalTimelineChart', () => {
  it('draws the history, one line per scenario, a target line and a today marker', async () => {
    const { container } = renderChart(seedGoalCar)

    const chart = await screen.findByRole('img', { name: CHART_NAME })
    expect(chart.querySelectorAll('path[data-history]')).toHaveLength(1)
    expect(
      [...chart.querySelectorAll('path[data-scenario]')].map((p) =>
        p.getAttribute('data-scenario'),
      ),
    ).toEqual(['PESSIMISTIC', 'BASE', 'OPTIMISTIC'])
    expect(chart.querySelectorAll('line[data-target]')).toHaveLength(1)
    expect(chart.querySelectorAll('line[data-today]')).toHaveLength(1)
    expect(screen.getByText('Target 80,000.00')).toBeInTheDocument()
    expect(screen.getByText('Today')).toBeInTheDocument()
    expect(container.textContent).toContain('History')
    expect(container.textContent).toContain('Pessimistic (2%)')
    expect(container.textContent).toContain('Base (4%)')
    expect(container.textContent).toContain('Optimistic (6%)')
  })

  it('forks the scenarios from the end of the history, on the today marker', async () => {
    renderChart(seedGoalCar)

    const chart = await screen.findByRole('img', { name: CHART_NAME })
    const historyEnd = pathX(chart.querySelector('path[data-history]'), 'last')
    const historyStart = pathX(chart.querySelector('path[data-history]'), 'first')
    const today = Number(chart.querySelector('line[data-today]')?.getAttribute('x1'))
    for (const scenario of chart.querySelectorAll('path[data-scenario]')) {
      expect(pathX(scenario, 'first')).toBeCloseTo(historyEnd, 5)
    }
    expect(today).toBeCloseTo(historyEnd, 5)
    // The history lies to the left of today, on the same month axis (Sep, Oct, Nov, Dec, Jan).
    expect(historyStart).toBeLessThan(historyEnd)
    expect(pathX(chart.querySelector('path[data-scenario]'), 'last')).toBeGreaterThan(historyEnd)
  })

  it('puts each history point value in its tooltip', async () => {
    renderChart(seedGoalCar)

    const chart = await screen.findByRole('img', { name: CHART_NAME })
    expect(chart.textContent).toContain('Oct 2026: 10,000.00')
    expect(chart.textContent).toContain('Nov 2026: 12,000.00')
  })

  it("captions the values as today's money with the snapshot date and the allocation basis", async () => {
    renderChart(seedGoalCar)

    expect(
      await screen.findByText("Values in today's money. Latest snapshot: November 1, 2026."),
    ).toBeInTheDocument()
    expect(
      screen.getByText(
        "As of the current allocation: today's split applied to each product's past value, in today's money.",
      ),
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

  it('draws only the projection, with no today marker or history note, when there is no history', async () => {
    server.use(http.get('/api/goals/:id/history', () => HttpResponse.json([])))
    renderChart(seedGoalCar)

    const chart = await screen.findByRole('img', { name: CHART_NAME })
    expect(chart.querySelectorAll('path[data-history]')).toHaveLength(0)
    expect(chart.querySelectorAll('line[data-today]')).toHaveLength(0)
    expect(chart.querySelectorAll('path[data-scenario]')).toHaveLength(3)
    expect(screen.queryByText(/^As of the current allocation/)).not.toBeInTheDocument()
  })

  it('still draws the projection when the history fails to load', async () => {
    server.use(
      http.get('/api/goals/:id/history', () =>
        HttpResponse.json({ type: 'urn:my-finances:error:internal-error' }, { status: 500 }),
      ),
    )
    renderChart(seedGoalCar)

    const chart = await screen.findByRole('img', { name: CHART_NAME })
    expect(chart.querySelectorAll('path[data-scenario]')).toHaveLength(3)
    expect(chart.querySelectorAll('path[data-history]')).toHaveLength(0)
    expect(await screen.findByText('The progress history could not be loaded.')).toBeInTheDocument()
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

  it('shows a delayed skeleton while loading', async () => {
    server.use(
      http.get('/api/goals/:id/projection', async () => {
        await delay(400)
        return HttpResponse.json(projectionFor(seedGoalCar))
      }),
    )
    renderChart(seedGoalCar)

    expect(
      await screen.findByRole('status', { name: 'Progress and projection' }),
    ).toBeInTheDocument()
  })

  it('shows a projection load failure with a retry', async () => {
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

    expect(await screen.findByRole('img', { name: CHART_NAME })).toBeInTheDocument()
  })

  describe('horizon selector', () => {
    it('is hidden when the projection is short', async () => {
      renderChart(seedGoalCar)

      await screen.findByRole('img', { name: CHART_NAME })
      expect(screen.queryByRole('group', { name: 'Chart horizon' })).not.toBeInTheDocument()
    })

    it('offers only the horizons shorter than the projection, and shows it all by default', async () => {
      server.use(
        http.get('/api/goals/:id/projection', () =>
          HttpResponse.json(longProjection(seedGoalCar, 31)),
        ),
      )
      renderChart(seedGoalCar)

      const chart = await screen.findByRole('img', { name: CHART_NAME })
      const group = screen.getByRole('group', { name: 'Chart horizon' })
      expect(
        within(group)
          .getAllByRole('button')
          .map((button) => button.textContent),
      ).toEqual(['1 year', '2 years', 'All'])
      expect(pathPointCount(chart.querySelector('path[data-scenario]'))).toBe(31)
    })

    it('cuts the projected lines to the chosen horizon but keeps the history', async () => {
      server.use(
        http.get('/api/goals/:id/projection', () =>
          HttpResponse.json(longProjection(seedGoalCar, 61)),
        ),
      )
      renderChart(seedGoalCar)
      const user = userEvent.setup()

      await screen.findByRole('img', { name: CHART_NAME })
      await user.click(screen.getByRole('button', { name: '1 year' }))

      const chart = screen.getByRole('img', { name: CHART_NAME })
      // The starting month plus twelve more.
      expect(pathPointCount(chart.querySelector('path[data-scenario]'))).toBe(13)
      expect(chart.querySelectorAll('path[data-history]')).toHaveLength(1)
      expect(chart.querySelectorAll('line[data-today]')).toHaveLength(1)

      await user.click(screen.getByRole('button', { name: 'All' }))
      expect(pathPointCount(chart.querySelector('path[data-scenario]'))).toBe(61)
    })
  })
})
