import { describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { LoadFailedNotice } from './LoadFailedNotice'

describe('LoadFailedNotice', () => {
  it('renders the message and calls onRetry when Retry is clicked', async () => {
    const user = userEvent.setup()
    const onRetry = vi.fn()
    render(<LoadFailedNotice message="Network error" onRetry={onRetry} />)

    expect(screen.getByText('Could not load data (Network error).')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Retry' }))

    expect(onRetry).toHaveBeenCalledOnce()
  })
})
