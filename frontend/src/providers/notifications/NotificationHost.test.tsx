import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { act, render, screen, waitFor } from '@testing-library/react'
import { Dialog, DialogTitle } from '@mui/material'
import { NotificationHost } from './NotificationHost'
import { publish } from './notificationStore'
import { emittedCss } from '../../test/utils/emittedCss'
import { DRAWER_WIDTH } from '../../components/layout/layoutSx'
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

  it('starts beside the permanent drawer from lg, so it does not cover the nav items', () => {
    setViewportWidth(VIEWPORT.desktop)
    render(<NotificationHost />)
    act(() => publish('Saved'))

    const css = emittedCss().replace(/\s+/g, '')
    expect(css).toMatch(
      new RegExp(`@media\\(min-width:1200px\\)\\{[^}]*left:${DRAWER_WIDTH + 24}px`),
    )
  })

  it('anchors at the top on a phone, above the page content, and lets taps through', () => {
    setViewportWidth(VIEWPORT.mobile)
    render(<NotificationHost />)
    act(() => publish('Saved'))

    const root = screen.getByRole('status').closest('.MuiSnackbar-root')
    expect(root).toHaveClass('MuiSnackbar-anchorOriginTopCenter')
    expect(root).toHaveStyle({ pointerEvents: 'none' })
  })

  describe('while a dialog is open', () => {
    // The 4 s auto-hide is far beyond these assertions; real timers let `waitFor` poll.
    beforeEach(() => vi.useRealTimers())

    // MUI's modal manager marks every other child of `body` aria-hidden while a modal is open; a
    // toast inside that subtree is not announced, and "Add and new" keeps the dialog open.
    function App({ dialogOpen }: { dialogOpen: boolean }) {
      return (
        <>
          <main>Page</main>
          <NotificationHost />
          <Dialog open={dialogOpen}>
            <DialogTitle>New entry</DialogTitle>
          </Dialog>
        </>
      )
    }

    it('keeps a toast published while the dialog is open reachable as a status', async () => {
      render(<App dialogOpen />)
      expect(screen.getByRole('dialog')).toBeInTheDocument()

      act(() => publish('Transaction saved'))

      await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('Transaction saved'))
    })

    it('keeps a toast that is already showing reachable when a dialog opens over it', async () => {
      const { rerender } = render(<App dialogOpen={false} />)
      act(() => publish('Transaction saved'))
      expect(screen.getByRole('status')).toBeInTheDocument()

      rerender(<App dialogOpen />)

      await screen.findByRole('dialog')
      await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('Transaction saved'))
    })

    it('still hides the page behind the dialog', async () => {
      render(<App dialogOpen />)
      act(() => publish('Transaction saved'))

      await screen.findByRole('status')

      expect(screen.queryByRole('main')).not.toBeInTheDocument()
      expect(screen.getByRole('dialog')).toBeInTheDocument()
    })
  })
})
