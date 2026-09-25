import { describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { InlineEditActions } from './InlineEditActions'

describe('InlineEditActions', () => {
  it('shows the edit button and calls onEdit when not editing', async () => {
    const user = userEvent.setup()
    const onEdit = vi.fn()
    render(
      <InlineEditActions
        editing={false}
        onEdit={onEdit}
        onSave={() => {}}
        onCancel={() => {}}
        editLabel="Rename"
      />,
    )

    expect(screen.queryByRole('button', { name: 'Save' })).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Rename' }))

    expect(onEdit).toHaveBeenCalledOnce()
  })

  it('shows save/cancel with the given labels and calls the right handlers while editing', async () => {
    const user = userEvent.setup()
    const onSave = vi.fn()
    const onCancel = vi.fn()
    render(
      <InlineEditActions
        editing
        onEdit={() => {}}
        onSave={onSave}
        onCancel={onCancel}
        editLabel="Edit cap"
        saveLabel="Save cap"
      />,
    )

    expect(screen.queryByRole('button', { name: 'Edit cap' })).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Save cap' }))
    await user.click(screen.getByRole('button', { name: 'Cancel' }))

    expect(onSave).toHaveBeenCalledOnce()
    expect(onCancel).toHaveBeenCalledOnce()
  })

  it('disables save/cancel while saving', () => {
    render(
      <InlineEditActions
        editing
        onEdit={() => {}}
        onSave={() => {}}
        onCancel={() => {}}
        editLabel="Edit"
        saving
      />,
    )

    expect(screen.getByRole('button', { name: 'Save' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Cancel' })).toBeDisabled()
  })
})
