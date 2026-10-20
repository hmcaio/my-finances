import { afterEach, describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import { DialogContent, DialogTitle } from '@mui/material'
import { restoreViewport, setViewportWidth } from '../../test/viewport'
import { FormGrid, ResponsiveDialog } from './ResponsiveDialog'

function renderDialog() {
  render(
    <ResponsiveDialog open onClose={() => {}}>
      <DialogTitle>Add thing</DialogTitle>
      <DialogContent>
        <FormGrid>
          <input aria-label="one" />
          <input aria-label="two" />
        </FormGrid>
      </DialogContent>
    </ResponsiveDialog>,
  )
}

describe('ResponsiveDialog', () => {
  afterEach(restoreViewport)

  it('is full-screen below sm', () => {
    setViewportWidth(390)
    renderDialog()

    expect(screen.getByRole('dialog')).toHaveClass('MuiDialog-paperFullScreen')
  })

  it.each([600, 768, 1280])('is a floating dialog at %ipx', (width) => {
    setViewportWidth(width)
    renderDialog()

    expect(screen.getByRole('dialog')).not.toHaveClass('MuiDialog-paperFullScreen')
  })

  it('renders the form grid children (column layout is asserted in Playwright, jsdom cannot)', () => {
    setViewportWidth(390)
    renderDialog()

    expect(screen.getByLabelText('one').parentElement).toHaveStyle({ display: 'grid' })
  })
})
