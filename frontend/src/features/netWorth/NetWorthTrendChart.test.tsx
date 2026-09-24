import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { server } from '../../mocks/server'
import { seedNetWorthTrend } from '../../mocks/handlers/netWorth'
import { expectLoadStates } from '../../test/loadStates'
import { NetWorthTrendChart } from './NetWorthTrendChart'

describe('NetWorthTrendChart', () => {
  it('draws the monthly net worth line with a point per month', async () => {
    render(<NetWorthTrendChart />)

    const chart = await screen.findByRole('img', { name: 'Net worth by month' })
    expect(chart.querySelectorAll('circle')).toHaveLength(seedNetWorthTrend.length)
    expect(chart.querySelectorAll('path[d^="M"]')).toHaveLength(1)
    expect(screen.getByText('2026-07-31: net worth 900.00', { exact: false })).toBeInTheDocument()
  })

  it('summarizes the latest point as assets plus investments minus liabilities', async () => {
    render(<NetWorthTrendChart />)

    expect(
      await screen.findByText(
        'As of 2026-08-31: net worth 1700.00 = assets 1000.00 + investments 800.00 - liabilities 100.00',
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
    render(<NetWorthTrendChart />)
    await screen.findByRole('img', { name: 'Net worth by month' })
    expect(screen.getByRole('button', { name: 'Monthly' })).toHaveAttribute('aria-pressed', 'true')

    await userEvent.click(screen.getByRole('button', { name: 'Every change' }))

    expect(
      await screen.findByRole('img', { name: 'Net worth at every change' }),
    ).toBeInTheDocument()
    expect(requested).toEqual(['MONTH', 'CHANGE_DATE'])
  })

  it('draws the change-date series as a step line', async () => {
    render(<NetWorthTrendChart />)
    await screen.findByRole('img', { name: 'Net worth by month' })

    await userEvent.click(screen.getByRole('button', { name: 'Every change' }))

    const chart = await screen.findByRole('img', { name: 'Net worth at every change' })
    // M + (horizontal, vertical) per later point: 1 + 2 * 2 commands.
    const commands = chart.querySelector('path[d^="M"]')?.getAttribute('d')?.match(/[ML]/g)
    expect(commands).toHaveLength(1 + 2 * (seedNetWorthTrend.length - 1))
  })

  it('shows a message when the period has no data', async () => {
    server.use(http.get('/api/net-worth/trend', () => HttpResponse.json([])))

    render(<NetWorthTrendChart />)

    expect(await screen.findByText('No net worth data for this period.')).toBeInTheDocument()
  })

  it('renders a single point without breaking', async () => {
    server.use(http.get('/api/net-worth/trend', () => HttpResponse.json([seedNetWorthTrend[0]])))

    render(<NetWorthTrendChart />)

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

    render(<NetWorthTrendChart />)

    expect(await screen.findByText(/net worth -20.00 = assets 60.00/)).toBeInTheDocument()
  })

  describe('load states', () => {
    expectLoadStates({
      render: () => render(<NetWorthTrendChart />),
      url: '/api/net-worth/trend',
      successBody: seedNetWorthTrend,
      loadedText: /As of 2026-08-31/,
    })
  })
})
