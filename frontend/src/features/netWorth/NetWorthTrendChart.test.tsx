import { afterEach, describe, expect, it, vi } from 'vitest'
import { act, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { server } from '../../mocks/server'
import { seedNetWorthTrend } from '../../mocks/handlers/netWorth'
import { expectLoadStates } from '../../test/contracts/loadStates'
import { NetWorthTrendChart } from './NetWorthTrendChart'
import { renderWithQueryClient } from '../../test/utils/renderWithQueryClient'

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

describe('NetWorthTrendChart', () => {
  it('draws the monthly net worth line with a point per month', async () => {
    renderWithQueryClient(<NetWorthTrendChart />)

    const chart = await screen.findByRole('img', { name: 'Net worth by month' })
    expect(chart.querySelectorAll('circle')).toHaveLength(seedNetWorthTrend.length)
    expect(chart.querySelectorAll('path[d^="M"]')).toHaveLength(1)
    expect(screen.getByText('2026-07-31: net worth 900.00', { exact: false })).toBeInTheDocument()
  })

  it('summarizes the latest point as assets plus investments minus liabilities', async () => {
    renderWithQueryClient(<NetWorthTrendChart />)

    expect(
      await screen.findByText(
        'As of 2026-08-31: net worth 1,700.00 = assets 1,000.00 + investments 800.00 - liabilities 100.00',
      ),
    ).toBeInTheDocument()
  })

  it('requests the monthly series first and every change after the toggle', async () => {
    const requested: (string | null)[] = []
    server.use(
      http.get('/api/net-worth/trend', ({ request }) => {
        requested.push(new URL(request.url).searchParams.get('granularity'))
        return HttpResponse.json(seedNetWorthTrend)
      }),
    )
    renderWithQueryClient(<NetWorthTrendChart />)
    await screen.findByRole('img', { name: 'Net worth by month' })
    expect(screen.getByRole('button', { name: 'Monthly' })).toHaveAttribute('aria-pressed', 'true')

    await userEvent.click(screen.getByRole('button', { name: 'Every change' }))

    expect(
      await screen.findByRole('img', { name: 'Net worth at every change' }),
    ).toBeInTheDocument()
    expect(requested).toEqual(['MONTH', 'CHANGE_DATE'])
  })

  it('draws the change-date series as a step line', async () => {
    renderWithQueryClient(<NetWorthTrendChart />)
    await screen.findByRole('img', { name: 'Net worth by month' })

    await userEvent.click(screen.getByRole('button', { name: 'Every change' }))

    const chart = await screen.findByRole('img', { name: 'Net worth at every change' })
    // M + (horizontal, vertical) per later point: 1 + 2 * 2 commands.
    const commands = chart.querySelector('path[d^="M"]')?.getAttribute('d')?.match(/[ML]/g)
    expect(commands).toHaveLength(1 + 2 * (seedNetWorthTrend.length - 1))
  })

  it('shows a message when the period has no data', async () => {
    server.use(http.get('/api/net-worth/trend', () => HttpResponse.json([])))

    renderWithQueryClient(<NetWorthTrendChart />)

    expect(await screen.findByText('No net worth data for this period.')).toBeInTheDocument()
  })

  it('renders a single point without breaking', async () => {
    server.use(http.get('/api/net-worth/trend', () => HttpResponse.json([seedNetWorthTrend[0]])))

    renderWithQueryClient(<NetWorthTrendChart />)

    const chart = await screen.findByRole('img', { name: 'Net worth by month' })
    expect(chart.querySelectorAll('circle')).toHaveLength(1)
  })

  it('handles a negative net worth on the axis', async () => {
    server.use(
      http.get('/api/net-worth/trend', () =>
        HttpResponse.json([
          { date: '2026-07-31', netWorth: -80, assets: 0, liabilities: 80, investments: 0 },
          { date: '2026-08-31', netWorth: -20, assets: 60, liabilities: 80, investments: 0 },
        ]),
      ),
    )

    renderWithQueryClient(<NetWorthTrendChart />)

    expect(await screen.findByText(/net worth -20.00 = assets 60.00/)).toBeInTheDocument()
  })

  describe('width (F040)', () => {
    afterEach(() => {
      vi.unstubAllGlobals()
      FakeResizeObserver.instances = []
    })

    it('keeps the default 640 by 260 frame where the width cannot be measured', async () => {
      renderWithQueryClient(<NetWorthTrendChart />)

      const chart = await screen.findByRole('img', { name: 'Net worth by month' })
      expect(chart.getAttribute('viewBox')).toBe('0 0 640 260')
    })

    it('fills the measured width: the plot runs to the right margin and the frame is not capped', async () => {
      vi.stubGlobal('ResizeObserver', FakeResizeObserver)
      renderWithQueryClient(<NetWorthTrendChart />)
      await screen.findByRole('img', { name: 'Net worth by month' })

      act(() => FakeResizeObserver.instances.forEach((observer) => observer.report(960)))

      const chart = screen.getByRole('img', { name: 'Net worth by month' })
      expect(chart.getAttribute('viewBox')).toBe('0 0 960 336')
      // The gridlines end at the 16px right margin of the 960 wide frame, and so does the last point.
      expect(Number(chart.querySelector('line')?.getAttribute('x2'))).toBe(944)
      const circles = chart.querySelectorAll('circle')
      expect(Number(circles[circles.length - 1].getAttribute('cx'))).toBe(944)
      expect(chart).toHaveStyle({ maxWidth: 'none' })
    })

    it('measures a box that is mounted before the data arrives, so the first paint is already fitted', async () => {
      vi.stubGlobal('ResizeObserver', FakeResizeObserver)
      renderWithQueryClient(<NetWorthTrendChart />)
      // The observer exists while the chart has not loaded yet: a ref on the data branch would
      // never attach.
      expect(FakeResizeObserver.instances).toHaveLength(1)
      act(() => FakeResizeObserver.instances[0].report(800))

      const chart = await screen.findByRole('img', { name: 'Net worth by month' })
      expect(chart.getAttribute('viewBox')).toBe('0 0 800 280')
    })
  })

  describe('load states', () => {
    expectLoadStates({
      render: () => renderWithQueryClient(<NetWorthTrendChart />),
      url: '/api/net-worth/trend',
      successBody: seedNetWorthTrend,
      loadedText: /As of 2026-08-31/,
    })
  })
})
