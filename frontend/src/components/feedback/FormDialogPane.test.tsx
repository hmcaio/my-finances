import { describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Dialog, DialogTitle, TextField } from '@mui/material'
import { FormDialogPane } from './FormDialogPane'

function renderPane(props: Partial<React.ComponentProps<typeof FormDialogPane>> = {}) {
  const handlers = { onSubmit: vi.fn(), onCancel: vi.fn(), onExtra: vi.fn() }
  render(
    <Dialog open>
      <DialogTitle>Entry</DialogTitle>
      <FormDialogPane
        canSubmit
        busy={false}
        submitLabel="Add"
        onSubmit={handlers.onSubmit}
        onCancel={handlers.onCancel}
        extraActions={
          <button type="button" onClick={handlers.onExtra}>
            Add and new
          </button>
        }
        {...props}
      >
        <TextField label="Name" required />
      </FormDialogPane>
    </Dialog>,
  )
  return { user: userEvent.setup({ delay: null }), ...handlers }
}

describe('FormDialogPane', () => {
  it('is a novalidate form inside the dialog, with the required hint', () => {
    renderPane()

    const form = screen.getByRole('textbox', { name: /Name/ }).closest('form')
    expect(form).not.toBeNull()
    expect(form).toHaveProperty('noValidate', true)
    expect(screen.getByText('Fields marked * are required')).toBeVisible()
  })

  it('submits the primary action on Enter, not an extra action', async () => {
    const { user, onSubmit, onExtra } = renderPane()

    await user.type(screen.getByRole('textbox', { name: /Name/ }), 'Acme{Enter}')

    expect(onSubmit).toHaveBeenCalledTimes(1)
    expect(onExtra).not.toHaveBeenCalled()
  })

  it('does nothing on Enter while the form is not valid', async () => {
    const { user, onSubmit } = renderPane({ canSubmit: false })

    await user.type(screen.getByRole('textbox', { name: /Name/ }), 'Acme{Enter}')

    expect(onSubmit).not.toHaveBeenCalled()
  })

  it('does nothing on Enter while a save is in flight', async () => {
    const { user, onSubmit } = renderPane({ busy: true })

    await user.type(screen.getByRole('textbox', { name: /Name/ }), 'Acme{Enter}')

    expect(onSubmit).not.toHaveBeenCalled()
    expect(screen.getByRole('button', { name: 'Cancel' })).toBeDisabled()
  })

  it('renders Cancel, the extra action, then the type=submit primary button', async () => {
    const { user, onCancel } = renderPane()

    const buttons = screen.getAllByRole('button').map((b) => b.textContent)
    expect(buttons).toEqual(['Cancel', 'Add and new', 'Add'])
    expect(screen.getByRole('button', { name: 'Add' })).toHaveAttribute('type', 'submit')
    await user.click(screen.getByRole('button', { name: 'Cancel' }))
    expect(onCancel).toHaveBeenCalledTimes(1)
  })
})
