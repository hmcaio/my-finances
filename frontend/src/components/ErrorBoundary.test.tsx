import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { ErrorBoundary } from './ErrorBoundary'

function Broken(): never {
  throw new Error('render crash')
}

describe('ErrorBoundary', () => {
  beforeEach(() => {
    // React reports every error a boundary catches through console.error; keep the output clean.
    vi.spyOn(console, 'error').mockImplementation(() => {})
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('renders its children when nothing throws', () => {
    render(
      <ErrorBoundary>
        <p>All good</p>
      </ErrorBoundary>,
    )

    expect(screen.getByText('All good')).toBeInTheDocument()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('shows the fallback with a Reload button when a child throws while rendering', () => {
    render(
      <ErrorBoundary>
        <Broken />
      </ErrorBoundary>,
    )

    expect(screen.getByRole('alert')).toHaveTextContent('Something went wrong on this page')
    expect(screen.getByRole('button', { name: 'Reload' })).toBeInTheDocument()
  })

  it('leaves siblings outside the boundary (like the navigation) rendered', () => {
    render(
      <>
        <nav>Sibling nav</nav>
        <ErrorBoundary>
          <Broken />
        </ErrorBoundary>
      </>,
    )

    expect(screen.getByText('Sibling nav')).toBeInTheDocument()
    expect(screen.getByRole('alert')).toBeInTheDocument()
  })

  it('resets when its key changes', () => {
    const { rerender } = render(
      <ErrorBoundary key="/broken">
        <Broken />
      </ErrorBoundary>,
    )
    expect(screen.getByRole('alert')).toBeInTheDocument()

    rerender(
      <ErrorBoundary key="/fine">
        <p>Recovered page</p>
      </ErrorBoundary>,
    )

    expect(screen.getByText('Recovered page')).toBeInTheDocument()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })
})
