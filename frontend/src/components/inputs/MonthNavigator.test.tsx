import { describe, expect, it, vi } from 'vitest'
import { fireEvent, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MonthNavigator } from './MonthNavigator'

describe('MonthNavigator', () => {
  it('shows the month in the picker', () => {
    render(<MonthNavigator value="2026-10" onChange={() => {}} />)

    expect(screen.getByLabelText('Month')).toHaveValue('2026-10')
  })

  it('steps to the previous and next month', async () => {
    const user = userEvent.setup({ delay: null })
    const onChange = vi.fn()
    render(<MonthNavigator value="2026-10" onChange={onChange} />)

    await user.click(screen.getByRole('button', { name: 'Previous month' }))
    await user.click(screen.getByRole('button', { name: 'Next month' }))

    expect(onChange).toHaveBeenNthCalledWith(1, '2026-09')
    expect(onChange).toHaveBeenNthCalledWith(2, '2026-11')
  })

  it('rolls the year over in both directions', async () => {
    const user = userEvent.setup({ delay: null })
    const onChange = vi.fn()
    const { rerender } = render(<MonthNavigator value="2026-01" onChange={onChange} />)

    await user.click(screen.getByRole('button', { name: 'Previous month' }))
    rerender(<MonthNavigator value="2026-12" onChange={onChange} />)
    await user.click(screen.getByRole('button', { name: 'Next month' }))

    expect(onChange).toHaveBeenNthCalledWith(1, '2025-12')
    expect(onChange).toHaveBeenNthCalledWith(2, '2027-01')
  })

  it('emits the month chosen in the picker, a future one included', () => {
    const onChange = vi.fn()
    render(<MonthNavigator value="2026-10" onChange={onChange} />)

    fireEvent.change(screen.getByLabelText('Month'), { target: { value: '2031-02' } })

    expect(onChange).toHaveBeenCalledWith('2031-02')
  })

  it('ignores the empty value a native month field emits while being cleared', () => {
    const onChange = vi.fn()
    render(<MonthNavigator value="2026-10" onChange={onChange} />)

    fireEvent.change(screen.getByLabelText('Month'), { target: { value: '' } })

    expect(onChange).not.toHaveBeenCalled()
  })

  it('uses a custom label when given', () => {
    render(<MonthNavigator value="2026-10" label="Statement month" onChange={() => {}} />)

    expect(screen.getByLabelText('Statement month')).toBeInTheDocument()
  })
})
