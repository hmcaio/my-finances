import { describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ConfirmDialog } from './ConfirmDialog'

describe('ConfirmDialog', () => {
  it('renders nothing visible when closed', () => {
    render(
      <ConfirmDialog
        open={false}
        title="Delete this thing?"
        body="This cannot be undone."
        confirmLabel="Delete"
        loading={false}
        onConfirm={() => {}}
        onCancel={() => {}}
      />,
    )

    expect(screen.queryByText('Delete this thing?')).not.toBeInTheDocument()
  })

  it('renders title/body and calls the right handlers', async () => {
    const user = userEvent.setup({ delay: null })
    const onConfirm = vi.fn()
    const onCancel = vi.fn()
    render(
      <ConfirmDialog
        open
        title="Delete this thing?"
        body="This cannot be undone."
        confirmLabel="Delete"
        loading={false}
        onConfirm={onConfirm}
        onCancel={onCancel}
      />,
    )

    expect(screen.getByText('Delete this thing?')).toBeInTheDocument()
    expect(screen.getByText('This cannot be undone.')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Delete' }))
    expect(onConfirm).toHaveBeenCalledOnce()

    await user.click(screen.getByRole('button', { name: 'Cancel' }))
    expect(onCancel).toHaveBeenCalledOnce()
  })

  it('disables both buttons while loading', () => {
    render(
      <ConfirmDialog
        open
        title="Delete this thing?"
        body="This cannot be undone."
        confirmLabel="Delete"
        loading
        onConfirm={() => {}}
        onCancel={() => {}}
      />,
    )

    expect(screen.getByRole('button', { name: 'Delete' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Cancel' })).toBeDisabled()
  })
})
