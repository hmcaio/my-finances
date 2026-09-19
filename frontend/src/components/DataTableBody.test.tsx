import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { act, fireEvent, render, screen } from '@testing-library/react'
import { Table } from '@mui/material'
import { DataTableBody } from './DataTableBody'

type State = Parameters<typeof DataTableBody>[0]['state']

const LOADING: State = { loading: true, loadError: null, reload: () => {} }
const LOADED: State = { loading: false, loadError: null, reload: () => {} }

function body(
  state: State,
  props: { columns?: number; rows?: number; actionsColumn?: boolean } = {},
) {
  return (
    <Table>
      <DataTableBody state={state} columns={3} {...props}>
        <tr>
          <td>Real row</td>
        </tr>
      </DataTableBody>
    </Table>
  )
}

const skeletons = (root: HTMLElement) => root.querySelectorAll('.MuiSkeleton-root')

describe('DataTableBody', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('shows nothing (but is marked busy) until the skeleton delay elapses', () => {
    const { container } = render(body(LOADING))
    const tbody = container.querySelector('tbody') as HTMLElement

    expect(tbody).toHaveAttribute('aria-busy', 'true')
    expect(skeletons(tbody)).toHaveLength(0)
    expect(screen.queryByText('Real row')).not.toBeInTheDocument()
  })

  it('shows skeleton rows sized to `rows` x `columns` after the delay', () => {
    const { container } = render(body(LOADING, { rows: 4, columns: 3 }))

    act(() => vi.advanceTimersByTime(150))

    const tbody = container.querySelector('tbody') as HTMLElement
    expect(tbody.querySelectorAll('tr')).toHaveLength(4)
    expect(skeletons(tbody)).toHaveLength(4 * 3)
    expect(screen.getByText('Loading…')).toBeInTheDocument()
    expect(screen.queryByText('Real row')).not.toBeInTheDocument()
  })

  it('renders circular placeholders in the last column when actionsColumn is set', () => {
    const { container } = render(body(LOADING, { rows: 2, columns: 3, actionsColumn: true }))

    act(() => vi.advanceTimersByTime(150))

    // Per row: 2 text placeholders + 2 round icon-button placeholders.
    expect(container.querySelectorAll('.MuiSkeleton-circular')).toHaveLength(2 * 2)
    expect(container.querySelectorAll('.MuiSkeleton-text')).toHaveLength(2 * 2)
  })

  it('never shows a skeleton when loading ends before the delay', () => {
    const { container, rerender } = render(body(LOADING))
    act(() => vi.advanceTimersByTime(100))

    rerender(body(LOADED))
    act(() => vi.advanceTimersByTime(500))

    expect(screen.getByText('Real row')).toBeInTheDocument()
    expect(skeletons(container)).toHaveLength(0)
    expect(container.querySelector('tbody')).not.toHaveAttribute('aria-busy')
  })

  it('replaces the skeleton with the real rows once loading ends', () => {
    const { container, rerender } = render(body(LOADING))
    act(() => vi.advanceTimersByTime(150))
    expect(skeletons(container).length).toBeGreaterThan(0)

    rerender(body(LOADED))

    expect(screen.getByText('Real row')).toBeInTheDocument()
    expect(skeletons(container)).toHaveLength(0)
    expect(screen.queryByText('Loading…')).not.toBeInTheDocument()
  })

  it('shows a failure row with a Retry button instead of a skeleton when the load failed', () => {
    const reload = vi.fn()
    const { container } = render(body({ loading: false, loadError: 'Network Error', reload }))
    act(() => vi.advanceTimersByTime(500))

    expect(screen.getByText('Could not load data (Network Error).')).toBeInTheDocument()
    expect(skeletons(container)).toHaveLength(0)

    fireEvent.click(screen.getByRole('button', { name: 'Retry' }))
    expect(reload).toHaveBeenCalledTimes(1)
  })
})
