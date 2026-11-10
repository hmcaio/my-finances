import { describe, expect, it } from 'vitest'
import { screen, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { server } from '../../../mocks/server'
import {
  projectionFor,
  projectionWithoutPace,
  seedGoalCar,
  seedGoalFund,
} from '../../../mocks/handlers/goals'
import { setViewportWidth, restoreViewport, VIEWPORT } from '../../../test/utils/viewport'
import { renderWithQueryClient } from '../../../test/utils/renderWithQueryClient'
import { afterEach, beforeEach } from 'vitest'
import type { Goal } from '../../../api/goals/goals'
import { GoalScenarioTable } from './GoalScenarioTable'

function renderTable(goal: Goal) {
  return renderWithQueryClient(<GoalScenarioTable goal={goal} />)
}

describe('GoalScenarioTable', () => {
  beforeEach(() => setViewportWidth(VIEWPORT.desktop))
  afterEach(restoreViewport)

  it('shows, per scenario, the value at the target date, the gap and the contribution needed', async () => {
    renderTable(seedGoalCar)

    const base = within(await screen.findByRole('row', { name: /Base/ }))
    // The mock projection: 4% base, last point 30000 + 2000 + 40 = 32040, so 47960.00 short.
    expect(base.getByText('4%')).toBeInTheDocument()
    expect(base.getByText('32,040.00')).toBeInTheDocument()
    expect(base.getByText('Short by 47,960.00')).toBeInTheDocument()
    expect(base.getByText('1,504.00')).toBeInTheDocument()
    expect(
      screen.getByRole('columnheader', { name: 'Value at November 15, 2028' }),
    ).toBeInTheDocument()
    expect(screen.getAllByRole('row')).toHaveLength(5)
  })

  it('shows a surplus when the projection passes the target', async () => {
    const projection = projectionFor(seedGoalCar)
    server.use(
      http.get('/api/goals/:id/projection', () =>
        HttpResponse.json({
          ...projection,
          scenarios: projection.scenarios.map((s) => ({
            ...s,
            shortfallAtTargetDate: -1200,
            requiredMonthlyContribution: 0,
          })),
        }),
      ),
    )
    renderTable(seedGoalCar)

    const base = within(await screen.findByRole('row', { name: /Base/ }))
    expect(base.getByText('Surplus of 1,200.00')).toBeInTheDocument()
    expect(base.getByText('None needed')).toBeInTheDocument()
  })

  it('shows a dash when no contribution can reach a target that is already due', async () => {
    const projection = projectionFor(seedGoalCar)
    server.use(
      http.get('/api/goals/:id/projection', () =>
        HttpResponse.json({
          ...projection,
          scenarios: projection.scenarios.map((s) => ({ ...s, requiredMonthlyContribution: null })),
        }),
      ),
    )
    renderTable(seedGoalCar)

    const base = within(await screen.findByRole('row', { name: /Base/ }))
    expect(base.getByText('—')).toBeInTheDocument()
  })

  it('shows the time to the target for a dateless goal', async () => {
    const projection = projectionFor(seedGoalFund)
    server.use(
      http.get('/api/goals/:id/projection', () =>
        HttpResponse.json({
          ...projection,
          scenarios: projection.scenarios.map((s) =>
            s.scenario === 'PESSIMISTIC' ? { ...s, monthsToTarget: null } : s,
          ),
        }),
      ),
    )
    renderTable(seedGoalFund)

    expect(await screen.findByRole('columnheader', { name: 'Time to target' })).toBeInTheDocument()
    expect(
      within(await screen.findByRole('row', { name: /Pessimistic/ })).getByText(
        'Not reached within 50 years',
      ),
    ).toBeInTheDocument()
    expect(
      within(screen.getByRole('row', { name: /Base/ })).getByText('Reached in 27 months'),
    ).toBeInTheDocument()
    expect(screen.queryByRole('columnheader', { name: /Value at/ })).not.toBeInTheDocument()
  })

  describe('current pace row (F041)', () => {
    it('shows the value at the date and the gap for a dated goal, with dashes where a rate or a contribution would be', async () => {
      renderTable(seedGoalCar)

      const pace = within(await screen.findByRole('row', { name: /Current pace/ }))
      // The mock pace: 30000 now, 500 a month, last point 31000 against 80000.
      expect(pace.getByText('31,000.00')).toBeInTheDocument()
      expect(pace.getByText('Short by 49,000.00')).toBeInTheDocument()
      // The real return and the monthly contribution needed do not apply to a pace.
      expect(pace.getAllByText('—')).toHaveLength(2)
      expect(screen.getAllByRole('row')).toHaveLength(5)
    })

    it('shows a surplus when the pace passes the target', async () => {
      const projection = projectionFor(seedGoalCar)
      server.use(
        http.get('/api/goals/:id/projection', () =>
          HttpResponse.json({
            ...projection,
            pace: { ...projection.pace, shortfallAtTargetDate: -300 },
          }),
        ),
      )
      renderTable(seedGoalCar)

      expect(
        within(await screen.findByRole('row', { name: /Current pace/ })).getByText(
          'Surplus of 300.00',
        ),
      ).toBeInTheDocument()
    })

    it('shows the time to the target for a dateless goal, or that it is not reached', async () => {
      renderTable(seedGoalFund)

      // The mock pace: (30000 - 12000) / 500 = 36 months.
      expect(
        within(await screen.findByRole('row', { name: /Current pace/ })).getByText(
          'Reached in 36 months',
        ),
      ).toBeInTheDocument()
    })

    it('says a flat or falling pace never reaches a dateless target', async () => {
      const projection = projectionFor(seedGoalFund)
      server.use(
        http.get('/api/goals/:id/projection', () =>
          HttpResponse.json({
            ...projection,
            pace: { ...projection.pace, monthlyPace: -100, monthsToTarget: null },
          }),
        ),
      )
      renderTable(seedGoalFund)

      expect(
        within(await screen.findByRole('row', { name: /Current pace/ })).getByText(
          'Not reached within 50 years',
        ),
      ).toBeInTheDocument()
    })

    it('has no pace row without a pace', async () => {
      server.use(
        http.get('/api/goals/:id/projection', () =>
          HttpResponse.json(projectionWithoutPace(seedGoalCar)),
        ),
      )
      renderTable(seedGoalCar)

      await screen.findByRole('row', { name: /Base/ })
      expect(screen.queryByRole('row', { name: /Current pace/ })).not.toBeInTheDocument()
      expect(screen.getAllByRole('row')).toHaveLength(4)
    })

    it('is a card of its own on a phone', async () => {
      setViewportWidth(VIEWPORT.mobile)
      renderTable(seedGoalCar)

      const list = await screen.findByRole('list', { name: 'Projection by scenario' })
      const cards = within(list).getAllByRole('listitem')
      expect(cards).toHaveLength(4)
      expect(within(cards[3]).getByText('Current pace')).toBeInTheDocument()
      expect(within(cards[3]).getByText('31,000.00')).toBeInTheDocument()
    })
  })

  it('says a dateless goal already at its target is reached', async () => {
    const projection = projectionFor(seedGoalFund)
    server.use(
      http.get('/api/goals/:id/projection', () =>
        HttpResponse.json({
          ...projection,
          scenarios: projection.scenarios.map((s) => ({ ...s, monthsToTarget: 0 })),
        }),
      ),
    )
    renderTable(seedGoalFund)

    expect(
      within(await screen.findByRole('row', { name: /Base/ })).getByText('Target reached'),
    ).toBeInTheDocument()
  })
})
