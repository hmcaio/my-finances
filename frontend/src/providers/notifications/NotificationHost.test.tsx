import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { act, render, screen } from '@testing-library/react'
import { NotificationHost } from './NotificationHost'
import { publish } from './notificationStore'
import { restoreViewport, setViewportWidth, VIEWPORT } from '../../test/utils/viewport'

/** Runs the timers, then once more so the exit transition (started by the close) can finish. */
function elapse(ms: number) {
  act(() => vi.advanceTimersByTime(ms))
  act(() => vi.advanceTimersByTime(500))
}

describe('NotificationHost', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
    restoreViewport()
  })

  it('renders nothing until a message is published', () => {
    render(<NotificationHost />)

    expect(screen.queryByRole('status')).not.toBeInTheDocument()
  })

  it('shows a published message as a status', () => {
    render(<NotificationHost />)

    act(() => publish('Transaction saved'))

    expect(screen.getByRole('status')).toHaveTextContent('Transaction saved')
  })

  it('shows a message published before the host mounted', () => {
    publish('Account saved')

    render(<NotificationHost />)

    expect(screen.getByRole('status')).toHaveTextContent('Account saved')
  })

  it('hides after the timeout, not before', () => {
    render(<NotificationHost />)
    act(() => publish('Transaction saved'))

    act(() => vi.advanceTimersByTime(3900))
    expect(screen.getByRole('status')).toBeInTheDocument()

    elapse(200)
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
  })

  it('replaces the first message with a second and restarts the timer', () => {
    render(<NotificationHost />)
    act(() => publish('First'))
    act(() => vi.advanceTimersByTime(3000))

    act(() => publish('Second'))

    expect(screen.getAllByRole('status')).toHaveLength(1)
    expect(screen.getByRole('status')).toHaveTextContent('Second')

    // 3000ms after the first would have closed it at 4000; the second still has ~1000ms to go.
    act(() => vi.advanceTimersByTime(1500))
    expect(screen.getByRole('status')).toHaveTextContent('Second')

    elapse(3000)
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
  })

  it('shows the same text again after it was hidden', () => {
    render(<NotificationHost />)
    act(() => publish('Saved'))
    elapse(4000)
    expect(screen.queryByRole('status')).not.toBeInTheDocument()

    act(() => publish('Saved'))

    expect(screen.getByRole('status')).toHaveTextContent('Saved')
  })

  it('anchors at the bottom start on desktop', () => {
    setViewportWidth(VIEWPORT.desktop)
    render(<NotificationHost />)
    act(() => publish('Saved'))

    const root = screen.getByRole('status').closest('.MuiSnackbar-root')
    expect(root).toHaveClass('MuiSnackbar-anchorOriginBottomLeft')
  })

  it('anchors at the top on a phone, above the page content, and lets taps through', () => {
    setViewportWidth(VIEWPORT.mobile)
    render(<NotificationHost />)
    act(() => publish('Saved'))

    const root = screen.getByRole('status').closest('.MuiSnackbar-root')
    expect(root).toHaveClass('MuiSnackbar-anchorOriginTopCenter')
    expect(root).toHaveStyle({ pointerEvents: 'none' })
  })
})
