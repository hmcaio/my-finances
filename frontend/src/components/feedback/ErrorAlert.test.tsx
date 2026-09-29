import { describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ErrorAlert } from './ErrorAlert'

describe('ErrorAlert', () => {
  it('renders nothing when message is null', () => {
    const { container } = render(<ErrorAlert message={null} onDismiss={() => {}} />)

    expect(container).toBeEmptyDOMElement()
  })

  it('renders the message and calls onDismiss when closed', async () => {
    const user = userEvent.setup({ delay: null })
    const onDismiss = vi.fn()
    render(<ErrorAlert message="Something broke" onDismiss={onDismiss} />)

    expect(screen.getByText('Something broke')).toBeInTheDocument()

    await user.click(screen.getByRole('button'))

    expect(onDismiss).toHaveBeenCalledOnce()
  })
})
