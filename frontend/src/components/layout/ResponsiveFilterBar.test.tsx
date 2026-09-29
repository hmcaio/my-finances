import { afterEach, describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { restoreViewport, setViewportWidth } from '../../test/viewport'
import { ResponsiveFilterBar } from './ResponsiveFilterBar'

function renderBar(props: { activeCount: number; onClear?: () => void }) {
  return render(
    <ResponsiveFilterBar {...props}>
      <label>
        Search
        <input />
      </label>
    </ResponsiveFilterBar>,
  )
}

describe('ResponsiveFilterBar', () => {
  afterEach(restoreViewport)

  it.each([600, 768, 1280])('shows the controls inline, without a Filters button, at %ipx', (w) => {
    setViewportWidth(w)
    renderBar({ activeCount: 2 })

    expect(screen.getByLabelText('Search')).toBeVisible()
    expect(screen.queryByRole('button', { name: /Filters/ })).not.toBeInTheDocument()
  })

  it('offers Clear inline only while a filter is active', async () => {
    setViewportWidth(1280)
    const onClear = vi.fn()
    const user = userEvent.setup({ delay: null })
    const { rerender } = renderBar({ activeCount: 0, onClear })
    expect(screen.queryByRole('button', { name: 'Clear filters' })).not.toBeInTheDocument()

    rerender(
      <ResponsiveFilterBar activeCount={1} onClear={onClear}>
        <input aria-label="Search" />
      </ResponsiveFilterBar>,
    )
    await user.click(screen.getByRole('button', { name: 'Clear filters' }))
    expect(onClear).toHaveBeenCalledOnce()
  })

  it('collapses the controls behind a Filters button below sm', () => {
    setViewportWidth(390)
    renderBar({ activeCount: 0 })

    expect(screen.getByRole('button', { name: 'Filters' })).toBeInTheDocument()
    expect(screen.queryByLabelText('Search')).not.toBeInTheDocument()
  })

  it('shows the active count on the button', () => {
    setViewportWidth(390)
    renderBar({ activeCount: 3 })

    const button = screen.getByRole('button', { name: 'Filters, 3 active' })
    expect(button).toHaveTextContent('3')
  })

  it('opens a full-screen sheet with the controls, and Done closes it', async () => {
    setViewportWidth(390)
    const user = userEvent.setup({ delay: null })
    renderBar({ activeCount: 0 })

    await user.click(screen.getByRole('button', { name: 'Filters' }))
    const dialog = await screen.findByRole('dialog')
    expect(dialog).toHaveClass('MuiDialog-paperFullScreen')
    expect(screen.getByLabelText('Search')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Done' }))
    await waitFor(() => expect(screen.queryByLabelText('Search')).not.toBeInTheDocument())
  })

  it('Clear in the sheet calls onClear and is disabled when nothing is active', async () => {
    setViewportWidth(390)
    const onClear = vi.fn()
    const user = userEvent.setup({ delay: null })
    const { rerender } = renderBar({ activeCount: 0, onClear })

    await user.click(screen.getByRole('button', { name: 'Filters' }))
    expect(await screen.findByRole('button', { name: 'Clear filters' })).toBeDisabled()

    rerender(
      <ResponsiveFilterBar activeCount={2} onClear={onClear}>
        <input aria-label="Search" />
      </ResponsiveFilterBar>,
    )
    await user.click(screen.getByRole('button', { name: 'Clear filters' }))
    expect(onClear).toHaveBeenCalledOnce()
  })
})
