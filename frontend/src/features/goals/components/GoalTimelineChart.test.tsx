import { afterEach, describe, expect, it, vi } from 'vitest'
import { act, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { delay, http, HttpResponse } from 'msw'
import { server } from '../../../mocks/server'
import {
  projectionFor,
  projectionWithoutPace,
  seedGoalCar,
  seedGoalFund,
} from '../../../mocks/handlers/goals'
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
  const points = (step: number) =>
    Array.from({ length: count }, (_, index): GoalPoint => {
      const month = 10 + index
      const year = 2026 + Math.floor(month / 12)
      return {
        month: `${year}-${String((month % 12) + 1).padStart(2, '0')}`,
        value: 12000 + index * step,
      }
    })
  return {
    ...base,
    scenarios: base.scenarios.map((scenario) => ({ ...scenario, points: points(500) })),
    pace: base.pace && { ...base.pace, points: points(300) },
  }
}

class FakeResizeObserver {
  static instances: FakeResizeObserver[] = []

  private readonly callback: (entries: { contentRect: { width: number } }[]) => void

  constructor(callback: (entries: { contentRect: { width: number } }[]) => void) {
    this.callback = callback
    FakeResizeObserver.instances.push(this)
  }

  observe() {}

  disconnect() {}

  report(width: number) {
    this.callback([{ contentRect: { width } }])
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

  describe('current pace (F041)', () => {
    it('draws a fourth line forking from the today marker, with its legend and the inflation note', async () => {
      const { container } = renderChart(seedGoalCar)

      const chart = await screen.findByRole('img', { name: CHART_NAME })
      const pace = chart.querySelector('path[data-pace]')
      expect(pace).not.toBeNull()
      // Drawn apart from the scenarios: a different dash pattern, and not one of data-scenario.
      expect(chart.querySelectorAll('path[data-scenario]')).toHaveLength(3)
      expect(pace?.getAttribute('stroke-dasharray')).toBe('8 3 2 3')
      const today = Number(chart.querySelector('line[data-today]')?.getAttribute('x1'))
      expect(pathX(pace, 'first')).toBeCloseTo(today, 5)
      expect(pathPointCount(pace)).toBe(3)
      expect(container.textContent).toContain('Pace (based on history): +500.00 a month')
      expect(
        screen.getByText('Pace is based on recorded values, not adjusted for inflation.'),
      ).toBeInTheDocument()
      expect(chart.textContent).toContain('Current pace: 31,000.00')
    })

    it('shows a falling pace with its sign', async () => {
      const projection = projectionFor(seedGoalCar)
      server.use(
        http.get('/api/goals/:id/projection', () =>
          HttpResponse.json({
            ...projection,
            pace: { ...projection.pace, monthlyPace: -200 },
          }),
        ),
      )
      const { container } = renderChart(seedGoalCar)

      await screen.findByRole('img', { name: CHART_NAME })
      expect(container.textContent).toContain('Pace (based on history): -200.00 a month')
    })

    it('says there is not enough history when a goal with allocations has no pace', async () => {
      server.use(
        http.get('/api/goals/:id/projection', () =>
          HttpResponse.json(projectionWithoutPace(seedGoalCar)),
        ),
      )
      const { container } = renderChart(seedGoalCar)

      const chart = await screen.findByRole('img', { name: CHART_NAME })
      expect(chart.querySelectorAll('path[data-pace]')).toHaveLength(0)
      expect(screen.getByText('Not enough history for a pace yet.')).toBeInTheDocument()
      expect(container.textContent).not.toContain('Pace (based on history)')
      expect(screen.queryByText(/not adjusted for inflation/)).not.toBeInTheDocument()
    })

    it('says nothing about the pace when the goal has no allocations', async () => {
      server.use(
        http.get('/api/goals/:id/projection', () =>
          HttpResponse.json(projectionWithoutPace(seedGoalFund)),
        ),
      )
      renderChart(seedGoalFund)

      await screen.findByRole('img', { name: CHART_NAME })
      expect(screen.queryByText('Not enough history for a pace yet.')).not.toBeInTheDocument()
      expect(screen.queryByText(/based on history/)).not.toBeInTheDocument()
    })

    it('follows the horizon selector with the scenarios', async () => {
      server.use(
        http.get('/api/goals/:id/projection', () =>
          HttpResponse.json(longProjection(seedGoalCar, 61)),
        ),
      )
      renderChart(seedGoalCar)
      const user = userEvent.setup()

      const chart = await screen.findByRole('img', { name: CHART_NAME })
      expect(pathPointCount(chart.querySelector('path[data-pace]'))).toBe(61)

      await user.click(screen.getByRole('button', { name: '1 year' }))
      expect(pathPointCount(chart.querySelector('path[data-pace]'))).toBe(13)
      expect(pathPointCount(chart.querySelector('path[data-scenario]'))).toBe(13)

      await user.click(screen.getByRole('button', { name: 'All' }))
      expect(pathPointCount(chart.querySelector('path[data-pace]'))).toBe(61)
    })
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

  describe('width', () => {
    afterEach(() => {
      vi.unstubAllGlobals()
      FakeResizeObserver.instances = []
    })

    it('keeps the default 640 by 260 frame where the width cannot be measured', async () => {
      renderChart(seedGoalCar)

      const chart = await screen.findByRole('img', { name: CHART_NAME })
      expect(chart.getAttribute('viewBox')).toBe('0 0 640 260')
    })

    it('fills the measured width: the plot runs to the right margin and the text keeps its size', async () => {
      vi.stubGlobal('ResizeObserver', FakeResizeObserver)
      renderChart(seedGoalCar)
      await screen.findByRole('img', { name: CHART_NAME })

      act(() => FakeResizeObserver.instances.forEach((observer) => observer.report(960)))

      const chart = screen.getByRole('img', { name: CHART_NAME })
      expect(chart.getAttribute('viewBox')).toBe('0 0 960 336')
      // The last projected month sits on the right margin (16) of the 960 wide frame, and the
      // dashed target line spans the plot.
      expect(pathX(chart.querySelector('path[data-scenario]'), 'last')).toBeCloseTo(944, 5)
      expect(Number(chart.querySelector('line[data-target]')?.getAttribute('x2'))).toBe(944)
      expect(chart).toHaveStyle({ maxWidth: 'none' })
    })

    it('labels fewer months on a narrow frame so they do not touch', async () => {
      vi.stubGlobal('ResizeObserver', FakeResizeObserver)
      renderChart(seedGoalCar)
      await screen.findByRole('img', { name: CHART_NAME })
      const labels = () =>
        [...screen.getByRole('img', { name: CHART_NAME }).querySelectorAll('text')].filter((node) =>
          /^[A-Z][a-z]{2} \d{4}$/.test(node.textContent ?? ''),
        )

      act(() => FakeResizeObserver.instances.forEach((observer) => observer.report(960)))
      expect(labels()).toHaveLength(4)
      act(() => FakeResizeObserver.instances.forEach((observer) => observer.report(328)))
      expect(labels()).toHaveLength(3)
    })

    it('follows a narrow container instead of scaling a 640 frame down', async () => {
      vi.stubGlobal('ResizeObserver', FakeResizeObserver)
      renderChart(seedGoalCar)
      await screen.findByRole('img', { name: CHART_NAME })

      act(() => FakeResizeObserver.instances.forEach((observer) => observer.report(328)))

      const chart = screen.getByRole('img', { name: CHART_NAME })
      expect(chart.getAttribute('viewBox')).toBe('0 0 328 260')
      expect(pathX(chart.querySelector('path[data-scenario]'), 'last')).toBeCloseTo(312, 5)
    })
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
