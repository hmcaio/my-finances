import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import { emittedCss, emotionClass } from '../../test/utils/emittedCss'
import { PageSection } from './PageSection'

describe('PageSection', () => {
  it('renders its children in an outlined paper', () => {
    render(
      <PageSection>
        <h2>Holdings</h2>
      </PageSection>,
    )

    const section = screen.getByRole('heading', { name: 'Holdings' }).parentElement
    expect(section).toHaveClass('MuiPaper-root', 'MuiPaper-outlined')
    expect(section?.tagName).toBe('DIV')
  })

  it('pads 16px from xs and 24px from sm up', () => {
    render(<PageSection>section</PageSection>)

    const className = emotionClass(screen.getByText('section'))
    const css = emittedCss()
    expect(css).toContain(`@media (min-width:0px){.${className}{padding:16px;}}`)
    expect(css).toContain(`@media (min-width:600px){.${className}{padding:24px;}}`)
  })

  it('merges a per-site sx after the padding', () => {
    render(<PageSection sx={{ mb: 3, maxWidth: 480 }}>section</PageSection>)

    const section = screen.getByText('section')
    expect(section).toHaveStyle({ marginBottom: '24px', maxWidth: '480px' })
    expect(emittedCss()).toContain(
      `@media (min-width:600px){.${emotionClass(section)}{padding:24px;}}`,
    )
  })

  it('forwards Paper props such as aria-busy', () => {
    render(<PageSection aria-busy>section</PageSection>)

    expect(screen.getByText('section')).toHaveAttribute('aria-busy', 'true')
  })
})
