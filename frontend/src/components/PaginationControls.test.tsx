import { describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { PaginationControls } from './PaginationControls'

describe('PaginationControls', () => {
  it('renders nothing when pageInfo is null', () => {
    const { container } = render(<PaginationControls pageInfo={null} onPageChange={() => {}} />)

    expect(container).toBeEmptyDOMElement()
  })

  it('renders nothing when there is only one page', () => {
    const { container } = render(
      <PaginationControls pageInfo={{ number: 0, totalPages: 1 }} onPageChange={() => {}} />,
    )

    expect(container).toBeEmptyDOMElement()
  })

  it('disables Previous on the first page and Next on the last page', () => {
    render(<PaginationControls pageInfo={{ number: 0, totalPages: 3 }} onPageChange={() => {}} />)

    expect(screen.getByRole('button', { name: 'Previous' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Next' })).not.toBeDisabled()
    expect(screen.getByText('Page 1 of 3')).toBeInTheDocument()
  })

  it('calls onPageChange with a functional updater on Previous/Next', async () => {
    const user = userEvent.setup()
    const onPageChange = vi.fn()
    render(
      <PaginationControls pageInfo={{ number: 1, totalPages: 3 }} onPageChange={onPageChange} />,
    )

    await user.click(screen.getByRole('button', { name: 'Next' }))
    expect(onPageChange).toHaveBeenCalledOnce()
    expect(onPageChange.mock.calls[0][0](1)).toBe(2)

    await user.click(screen.getByRole('button', { name: 'Previous' }))
    expect(onPageChange.mock.calls[1][0](1)).toBe(0)
  })
})
