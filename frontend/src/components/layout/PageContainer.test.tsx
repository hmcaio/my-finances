import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import { PageContainer } from './PageContainer'

/** Emotion's rules for the current document: jsdom does not evaluate `@media`, so a responsive
 * `sx` value is asserted on the emitted rule text instead of on the computed style. */
function emittedCss() {
  return Array.from(document.querySelectorAll('style'))
    .map((s) => s.textContent)
    .join('')
}

describe('PageContainer', () => {
  it('renders its children in a plain div', () => {
    render(
      <PageContainer>
        <h1>Title</h1>
      </PageContainer>,
    )

    const root = screen.getByRole('heading', { name: 'Title' }).parentElement
    expect(root?.tagName).toBe('DIV')
  })

  it('applies the page vertical padding: 16px from xs, 32px from sm up', () => {
    render(<PageContainer>content</PageContainer>)

    const className = screen
      .getByText('content')
      .className.split(' ')
      .find((c) => c.startsWith('css-'))
    const css = emittedCss()
    expect(css).toContain(
      `@media (min-width:0px){.${className}{padding-top:16px;padding-bottom:16px;}}`,
    )
    expect(css).toContain(
      `@media (min-width:600px){.${className}{padding-top:32px;padding-bottom:32px;}}`,
    )
  })
})
