import { describe, expect, it } from 'vitest'
import { screen, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { server } from '../../../mocks/server'
import { seedFiiAllocation } from '../../../mocks/handlers/fiiAllocation'
import type { FiiAllocationRow } from '../../../api/investments/fii/fiiAllocation'
import { expectLoadStates } from '../../../test/contracts/loadStates'
import { renderWithQueryClient } from '../../../test/utils/renderWithQueryClient'
import { currentMonth } from '../../../utils/localDate'
import { FiiAllocationComparison } from './FiiAllocationComparison'
import { FiiNestedAllocationDonutChart } from './FiiNestedAllocationDonutChart'

const MONTH = currentMonth()

/** Overrides the four allocation fixtures one by one, keeping the default for any not named. */
function useAllocation(overrides: Partial<Record<string, FiiAllocationRow[]>>) {
  server.use(
    http.get('/api/fii/allocation', ({ request }) => {
      const url = new URL(request.url)
      const key = `${url.searchParams.get('basis')}:${url.searchParams.get('groupBy')}`
      return HttpResponse.json(overrides[key] ?? seedFiiAllocation[key] ?? [])
    }),
  )
}

function table() {
  return screen.getByRole('table', { name: 'Actual versus planned allocation' })
}

/** The cells of the row that carries `label`, as text, in column order. */
function rowCells(label: string): string[] {
  const row = within(table()).getByText(label).closest('tr') as HTMLElement
  return Array.from(row.querySelectorAll('th, td')).map((cell) => cell.textContent ?? '')
}

/** The color dot of a row: the one `aria-hidden` span in its first cell. */
function dotOf(label: string): HTMLElement {
  const row = within(table()).getByText(label).closest('tr') as HTMLElement
  return row.querySelector('[aria-hidden="true"]') as HTMLElement
}

describe('FiiAllocationComparison', () => {
  it('lists each segment with its tickers beneath, Actual %, Planned % and the difference', async () => {
    renderWithQueryClient(<FiiAllocationComparison month={MONTH} />)

    await within(await screen.findByRole('table')).findByText('Shoppings')

    const labels = within(table())
      .getAllByRole('row')
      .slice(1)
      .map((row) => row.querySelector('th, td')?.textContent)
    expect(labels).toEqual(['Shoppings', 'KNRI11', 'Logistica', 'HGLG11'])
    expect(
      within(table())
        .getAllByRole('columnheader')
        .map((header) => header.textContent),
    ).toEqual(['Segment / ticker', 'Actual %', 'Planned %', 'Difference (pp)'])
    expect(rowCells('Shoppings')).toEqual(['Shoppings', '68.6', '60.0', '+8.6'])
    expect(rowCells('KNRI11')).toEqual(['KNRI11', '68.6', '60.0', '+8.6'])
    expect(rowCells('Logistica')).toEqual(['Logistica', '31.4', '40.0', '-8.6'])
    expect(rowCells('HGLG11')).toEqual(['HGLG11', '31.4', '40.0', '-8.6'])
  })

  it('shows a dash for a side that has no value and counts it as zero in the difference', async () => {
    useAllocation({
      'PLANNED:SEGMENT': [
        { key: 'iseg-shoppings', label: 'Shoppings', percentage: 100, segmentId: null },
      ],
      'PLANNED:TICKER': [
        { key: 'iprod-knri11', label: 'KNRI11', percentage: 100, segmentId: 'iseg-shoppings' },
      ],
    })
    renderWithQueryClient(<FiiAllocationComparison month={MONTH} />)
    await within(await screen.findByRole('table')).findByText('Logistica')

    expect(rowCells('Logistica')).toEqual(['Logistica', '31.4', '—', '+31.4'])
    expect(rowCells('HGLG11')).toEqual(['HGLG11', '31.4', '—', '+31.4'])
    expect(rowCells('Shoppings')).toEqual(['Shoppings', '68.6', '100.0', '-31.4'])
  })

  it('shows a segment that exists only in the plan, with a dash under Actual', async () => {
    useAllocation({
      'PLANNED:SEGMENT': [
        { key: 'iseg-hotel', label: 'Hotel', percentage: 20, segmentId: null },
        { key: 'iseg-shoppings', label: 'Shoppings', percentage: 40, segmentId: null },
        { key: 'iseg-logistica', label: 'Logistica', percentage: 40, segmentId: null },
      ],
      'PLANNED:TICKER': [
        { key: 'iprod-xpto11', label: 'XPTO11', percentage: 20, segmentId: 'iseg-hotel' },
        { key: 'iprod-knri11', label: 'KNRI11', percentage: 40, segmentId: 'iseg-shoppings' },
        { key: 'iprod-hglg11', label: 'HGLG11', percentage: 40, segmentId: 'iseg-logistica' },
      ],
    })
    renderWithQueryClient(<FiiAllocationComparison month={MONTH} />)
    await within(await screen.findByRole('table')).findByText('Hotel')

    expect(rowCells('Hotel')).toEqual(['Hotel', '—', '20.0', '-20.0'])
    expect(rowCells('XPTO11')).toEqual(['XPTO11', '—', '20.0', '-20.0'])
  })

  it('writes an equal pair as a plain zero, never a signed one', async () => {
    useAllocation({
      'PLANNED:SEGMENT': seedFiiAllocation['ACTUAL:SEGMENT'],
      'PLANNED:TICKER': seedFiiAllocation['ACTUAL:TICKER'],
    })
    renderWithQueryClient(<FiiAllocationComparison month={MONTH} />)
    await within(await screen.findByRole('table')).findByText('Shoppings')

    expect(rowCells('Shoppings')[3]).toBe('0.0')
  })

  it('uses the color of the donut slice for each row, in both donuts', async () => {
    // Planned lists the segments the other way round: each donut colors by its own order today,
    // so a table dot could match only one of them unless the palette is shared.
    useAllocation({
      'PLANNED:SEGMENT': [
        { key: 'iseg-logistica', label: 'Logistica', percentage: 60, segmentId: null },
        { key: 'iseg-shoppings', label: 'Shoppings', percentage: 40, segmentId: null },
      ],
      'PLANNED:TICKER': [
        { key: 'iprod-hglg11', label: 'HGLG11', percentage: 60, segmentId: 'iseg-logistica' },
        { key: 'iprod-knri11', label: 'KNRI11', percentage: 40, segmentId: 'iseg-shoppings' },
      ],
    })
    renderWithQueryClient(
      <>
        <FiiAllocationComparison month={MONTH} />
        <FiiNestedAllocationDonutChart
          basis="ACTUAL"
          month={MONTH}
          ariaLabel="Actual allocation"
          emptyMessage="-"
        />
        <FiiNestedAllocationDonutChart
          basis="PLANNED"
          month={MONTH}
          ariaLabel="Planned allocation"
          emptyMessage="-"
        />
      </>,
    )
    await within(await screen.findByRole('table')).findByText('Shoppings')
    const actualDonut = await screen.findByRole('img', { name: 'Actual allocation' })
    const plannedDonut = await screen.findByRole('img', { name: 'Planned allocation' })

    const sliceFill = (donut: HTMLElement, title: string) =>
      within(donut).getByText(title).closest('path')?.getAttribute('fill')
    const shoppingsActual = sliceFill(actualDonut, 'Shoppings: 68.6%')
    const shoppingsPlanned = sliceFill(plannedDonut, 'Shoppings: 40.0%')
    const logisticaActual = sliceFill(actualDonut, 'Logistica: 31.4%')
    const logisticaPlanned = sliceFill(plannedDonut, 'Logistica: 60.0%')

    expect(shoppingsActual).toBe(shoppingsPlanned)
    expect(logisticaActual).toBe(logisticaPlanned)
    expect(shoppingsActual).not.toBe(logisticaActual)
    expect(dotOf('Shoppings')).toHaveStyle({ backgroundColor: shoppingsActual! })
    expect(dotOf('Logistica')).toHaveStyle({ backgroundColor: logisticaActual! })
    // A ticker is its segment's color, shaded: the first ticker of a segment is the full color.
    expect(dotOf('KNRI11')).toHaveStyle({ backgroundColor: shoppingsActual! })
    // The dots are decoration: color is never the only thing that tells the rows apart.
    expect(dotOf('Shoppings')).toHaveAttribute('aria-hidden', 'true')
  })

  it('says so when there is neither an allocation nor a plan', async () => {
    useAllocation({
      'ACTUAL:SEGMENT': [],
      'ACTUAL:TICKER': [],
      'PLANNED:SEGMENT': [],
      'PLANNED:TICKER': [],
    })
    renderWithQueryClient(<FiiAllocationComparison month={MONTH} />)

    expect(await screen.findByText('Nothing to compare yet.')).toBeInTheDocument()
    expect(screen.queryByRole('table')).not.toBeInTheDocument()
  })

  describe('load states', () => {
    expectLoadStates({
      render: () => renderWithQueryClient(<FiiAllocationComparison month={MONTH} />),
      url: '/api/fii/allocation',
      // One body serves all four requests: as a segment row and as a ticker row of that segment.
      successBody: [
        { key: 'iseg-shoppings', label: 'Shoppings', percentage: 100, segmentId: 'iseg-shoppings' },
      ],
      loadedText: 'Shoppings',
    })
  })
})
