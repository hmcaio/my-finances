import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import { Dialog } from '@mui/material'
import { FormDialogContent } from './FormDialogContent'

describe('FormDialogContent', () => {
  it('renders its children inside the dialog content', () => {
    render(
      <Dialog open>
        <FormDialogContent>
          <label>
            Name
            <input />
          </label>
        </FormDialogContent>
      </Dialog>,
    )

    const field = screen.getByLabelText('Name')
    expect(field.closest('.MuiDialogContent-root')).not.toBeNull()
  })

  it('wraps the children in a div with 8px of top padding, so floating labels are not clipped', () => {
    render(
      <Dialog open>
        <FormDialogContent>
          <p>body</p>
        </FormDialogContent>
      </Dialog>,
    )

    const body = screen.getByText('body').parentElement as HTMLElement
    expect(body.tagName).toBe('DIV')
    expect(body).toHaveStyle({ paddingTop: '8px' })
    expect(body.parentElement).toHaveClass('MuiDialogContent-root')
  })
})
