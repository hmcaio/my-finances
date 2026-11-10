import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import { PageHeader } from './PageHeader'

function renderHeader(props: { sx?: object } = {}) {
  render(
    <PageHeader title="Accounts" {...props}>
      <button>Add</button>
    </PageHeader>,
  )
  return screen.getByRole('heading', { level: 1, name: 'Accounts' }).parentElement as HTMLElement
}

describe('PageHeader', () => {
  it('renders the title as the page h1 in the h4 style, with the actions beside it', () => {
    const row = renderHeader()

    const heading = screen.getByRole('heading', { level: 1, name: 'Accounts' })
    expect(heading).toHaveClass('MuiTypography-h4')
    expect(row).toContainElement(screen.getByRole('button', { name: 'Add' }))
  })

  it('lays the title and actions out as a centred, space-between row with a 16px gap and 8px below', () => {
    const row = renderHeader()

    expect(row.tagName).toBe('DIV')
    expect(row).toHaveStyle({
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'space-between',
      gap: '16px',
      marginBottom: '8px',
    })
  })

  it('wraps by default so an action never squeezes the title on a phone (F040)', () => {
    expect(renderHeader()).toHaveStyle({ flexWrap: 'wrap' })
  })

  it('merges a per-site sx after the base', () => {
    const row = renderHeader({ sx: { flexWrap: 'nowrap', gap: 1 } })

    expect(row).toHaveStyle({
      display: 'flex',
      justifyContent: 'space-between',
      flexWrap: 'nowrap',
      gap: '8px',
    })
  })

  it('renders without actions', () => {
    render(<PageHeader title="Only a title" />)

    expect(screen.getByRole('heading', { level: 1, name: 'Only a title' })).toBeInTheDocument()
  })
})
