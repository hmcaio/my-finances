import { describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { TextField } from '@mui/material'
import { FormDialog } from './FormDialog'

function renderDialog(props: Partial<React.ComponentProps<typeof FormDialog>> = {}) {
  const handlers = { onSubmit: vi.fn(), onClose: vi.fn(), onDismissError: vi.fn() }
  render(
    <FormDialog
      open
      title="Add thing"
      error={null}
      busy={false}
      canSubmit
      submitLabel="Add"
      {...handlers}
      {...props}
    >
      <TextField label="Name" required autoFocus />
      <TextField label="Notes" />
    </FormDialog>,
  )
  return { user: userEvent.setup({ delay: null }), ...handlers }
}

describe('FormDialog', () => {
  it('makes the dialog paper a novalidate form with a titled dialog role', () => {
    renderDialog()

    const dialog = screen.getByRole('dialog', { name: 'Add thing' })
    expect(dialog.tagName).toBe('FORM')
    expect(dialog).toHaveProperty('noValidate', true)
  })

  it('submits on Enter in a text field when the form is valid', async () => {
    const { user, onSubmit } = renderDialog()

    await user.type(screen.getByRole('textbox', { name: /Name/ }), 'Acme{Enter}')

    expect(onSubmit).toHaveBeenCalledTimes(1)
  })

  it('does nothing on Enter while the form is not valid', async () => {
    const { user, onSubmit } = renderDialog({ canSubmit: false })

    expect(screen.getByRole('button', { name: 'Add' })).toBeDisabled()
    await user.type(screen.getByRole('textbox', { name: /Name/ }), 'Acme{Enter}')
    await user.type(screen.getByRole('textbox', { name: 'Notes' }), '{Enter}')

    expect(onSubmit).not.toHaveBeenCalled()
  })

  it('does nothing on Enter while a save is in flight', async () => {
    const { user, onSubmit } = renderDialog({ busy: true })

    await user.type(screen.getByRole('textbox', { name: /Name/ }), 'Acme{Enter}')

    expect(onSubmit).not.toHaveBeenCalled()
  })

  it('guards in the handler itself, not only through the disabled button', () => {
    const { onSubmit } = renderDialog({ canSubmit: false })

    // Submit the form directly, whatever the browser's implicit-submission rule would do.
    screen
      .getByRole('dialog')
      .dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }))

    expect(onSubmit).not.toHaveBeenCalled()
  })

  it('prevents the browser default of the submit event', () => {
    renderDialog()

    const submit = new Event('submit', { bubbles: true, cancelable: true })
    screen.getByRole('dialog').dispatchEvent(submit)

    expect(submit.defaultPrevented).toBe(true)
  })

  it('renders a type=submit primary button with the given label', async () => {
    const { user, onSubmit } = renderDialog({ submitLabel: 'Save changes' })

    const submit = screen.getByRole('button', { name: 'Save changes' })
    expect(submit).toHaveAttribute('type', 'submit')
    await user.click(submit)
    expect(onSubmit).toHaveBeenCalledTimes(1)
  })

  it('does not let native validation block the submit (noValidate)', async () => {
    const { user, onSubmit } = renderDialog()

    // "Name" is required and empty: without noValidate the browser (and jsdom) would block it.
    await user.click(screen.getByRole('button', { name: 'Add' }))

    expect(onSubmit).toHaveBeenCalledTimes(1)
  })

  it('marks required fields with an asterisk and shows the required hint', () => {
    renderDialog()

    expect(screen.getByText('Fields marked * are required')).toBeVisible()
    expect(screen.getByRole('textbox', { name: /Name/ })).toBeRequired()
    expect(document.querySelector('.MuiFormLabel-asterisk')).toBeInTheDocument()
  })

  it('disables the primary button while the form is not valid', () => {
    renderDialog({ canSubmit: false })

    expect(screen.getByRole('button', { name: 'Add' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Cancel' })).toBeEnabled()
  })

  it('disables Cancel and the primary button while a save is in flight', () => {
    renderDialog({ busy: true })

    expect(screen.getByRole('button', { name: 'Add' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Cancel' })).toBeDisabled()
  })

  it('closes on Cancel and on Esc', async () => {
    const { user, onClose } = renderDialog()

    await user.click(screen.getByRole('button', { name: 'Cancel' }))
    expect(onClose).toHaveBeenCalledTimes(1)
    await user.keyboard('{Escape}')
    expect(onClose).toHaveBeenCalledTimes(2)
  })

  it('does not close on Esc while a save is in flight', async () => {
    const { user, onClose } = renderDialog({ busy: true })

    await user.keyboard('{Escape}')

    expect(onClose).not.toHaveBeenCalled()
  })

  it('shows the error banner and renders extra actions before the primary button', async () => {
    const { user, onDismissError } = renderDialog({
      error: 'Name taken',
      extraActions: <button type="button">Add and new</button>,
    })

    expect(screen.getByText('Name taken')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: /close/i }))
    expect(onDismissError).toHaveBeenCalledTimes(1)
    const buttons = screen.getAllByRole('button').map((b) => b.textContent)
    expect(buttons.indexOf('Add and new')).toBeLessThan(buttons.indexOf('Add'))
  })
})
