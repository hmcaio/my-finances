import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import { Box } from '@mui/material'
import { emittedCss, emotionClass } from '../../test/utils/emittedCss'
import { FORM_GRID_FULL_ROW, SECTION_PADDING, SELECT_MIN_WIDTH, WRAP_ANYWHERE } from './layoutSx'

describe('layoutSx', () => {
  it('SECTION_PADDING is 16px on a phone and 24px from sm up', () => {
    render(<Box sx={{ p: SECTION_PADDING }}>section</Box>)

    const className = emotionClass(screen.getByText('section'))
    const css = emittedCss()
    expect(css).toContain(`@media (min-width:0px){.${className}{padding:16px;}}`)
    expect(css).toContain(`@media (min-width:600px){.${className}{padding:24px;}}`)
  })

  it('WRAP_ANYWHERE lets long unbroken text wrap at any point', () => {
    render(<Box sx={WRAP_ANYWHERE}>wrap</Box>)

    expect(emittedCss()).toContain(
      `.${emotionClass(screen.getByText('wrap'))}{overflow-wrap:anywhere;}`,
    )
  })

  it('SELECT_MIN_WIDTH keeps a select at least 160px wide', () => {
    render(<Box sx={SELECT_MIN_WIDTH}>select</Box>)

    expect(screen.getByText('select')).toHaveStyle({ minWidth: '160px' })
  })

  it('FORM_GRID_FULL_ROW spans every column of a FormGrid', () => {
    render(<Box sx={FORM_GRID_FULL_ROW}>wide</Box>)

    expect(emittedCss()).toContain(`.${emotionClass(screen.getByText('wide'))}{grid-column:1/-1;}`)
  })
})
