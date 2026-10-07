import { describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { FuelTimeSeriesChart, type FuelSeries } from './FuelTimeSeriesChart'

const axisColor = '#666'
const gridColor = '#ccc'

describe('FuelTimeSeriesChart', () => {
  it('gives two same-date points in one series distinct keys instead of colliding', () => {
    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => {})
    const series: FuelSeries[] = [
      {
        label: 'Gasoline',
        color: '#f00',
        points: [
          { date: '2026-01-10', value: 12 },
          { date: '2026-01-10', value: 13 },
        ],
      },
    ]

    const { container } = render(
      <FuelTimeSeriesChart
        series={series}
        label="Km per liter"
        axisColor={axisColor}
        gridColor={gridColor}
      />,
    )

    expect(container.querySelectorAll('circle')).toHaveLength(2)
    // The old key (`${s.label}-${p.date}`, no index) collides on two same-date points in one
    // series - React warns "Encountered two children with the same key" at render time.
    expect(consoleError.mock.calls.some((args) => String(args[0]).includes('same key'))).toBe(false)
    consoleError.mockRestore()
  })

  it('tracks the data range instead of forcing a zero baseline when zeroBaseline is false', () => {
    const series: FuelSeries[] = [
      {
        label: 'Km/L',
        color: '#f00',
        points: [
          { date: '2026-01-01', value: 14.8 },
          { date: '2026-01-02', value: 15.2 },
        ],
      },
    ]

    const { container: withZero } = render(
      <FuelTimeSeriesChart
        series={series}
        label="Km per liter"
        axisColor={axisColor}
        gridColor={gridColor}
      />,
    )
    const { container: withoutZero } = render(
      <FuelTimeSeriesChart
        series={series}
        label="Km per liter"
        axisColor={axisColor}
        gridColor={gridColor}
        zeroBaseline={false}
      />,
    )

    const axisLabels = (c: HTMLElement) =>
      Array.from(c.querySelectorAll('text')).map((t) => t.textContent)
    // Forcing 0 into a 14.8-15.2 range pulls in a 0.00 tick that wouldn't otherwise appear.
    expect(axisLabels(withZero)).toContain('0.00')
    expect(axisLabels(withoutZero)).not.toContain('0.00')
  })

  it('shows the empty message when every series has no points', () => {
    render(
      <FuelTimeSeriesChart
        series={[{ label: 'Gasoline', color: '#f00', points: [] }]}
        label="Km per liter"
        axisColor={axisColor}
        gridColor={gridColor}
        emptyMessage="No fills yet."
      />,
    )

    expect(screen.getByText('No fills yet.')).toBeInTheDocument()
  })
})
