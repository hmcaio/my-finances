import { describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AddFormDialog } from './AddFormDialog'

function renderDialog(props: Partial<React.ComponentProps<typeof AddFormDialog>> = {}) {
  const handlers = { onSubmit: vi.fn(), onCancel: vi.fn(), onDismissError: vi.fn() }
  render(
    <AddFormDialog
      open
      title="Add thing"
      error={null}
      busy={false}
      canSubmit
      {...handlers}
      afterFields={<p>a hint</p>}
      {...props}
    >
      <input aria-label="one" />
    </AddFormDialog>,
  )
  return { user: userEvent.setup({ delay: null }), ...handlers }
}

describe('AddFormDialog', () => {
  it('renders the title, the fields and the hint, and calls the handlers', async () => {
    const { user, onSubmit, onCancel } = renderDialog()

    expect(screen.getByRole('dialog', { name: 'Add thing' })).toBeInTheDocument()
    expect(screen.getByLabelText('one')).toBeInTheDocument()
    expect(screen.getByText('a hint')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Add' }))
    expect(onSubmit).toHaveBeenCalledTimes(1)
    await user.click(screen.getByRole('button', { name: 'Cancel' }))
    expect(onCancel).toHaveBeenCalledTimes(1)
  })

  it('disables Add until the draft is valid, and both buttons while busy', () => {
    renderDialog({ canSubmit: false })
    expect(screen.getByRole('button', { name: 'Add' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Cancel' })).toBeEnabled()
  })

  it('disables Cancel and Add while a save is in flight', () => {
    renderDialog({ busy: true })
    expect(screen.getByRole('button', { name: 'Add' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Cancel' })).toBeDisabled()
  })

  it('shows the error and closes through onCancel on Esc', async () => {
    const { user, onCancel } = renderDialog({ error: 'Name taken' })

    expect(screen.getByText('Name taken')).toBeInTheDocument()
    await user.keyboard('{Escape}')
    expect(onCancel).toHaveBeenCalled()
  })
})
