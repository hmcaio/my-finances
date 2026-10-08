import { describe, expect, it, vi } from 'vitest'
import { fireEvent, render, screen } from '@testing-library/react'
import { MonthPicker } from './MonthPicker'
import { currentMonth } from '../../utils/localDate'

/** The month after `currentMonth()`, `YYYY-MM` (handles the December-to-January rollover). */
function nextMonth(): string {
  const [year, month] = currentMonth().split('-').map(Number)
  const date = new Date(year, month)
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`
}

describe('MonthPicker', () => {
  it('renders the given label and value', () => {
    render(<MonthPicker label="Month" value="2026-03" onChange={() => {}} />)

    expect(screen.getByLabelText('Month')).toHaveValue('2026-03')
  })

  it('calls onChange with the selected value when unclamped', () => {
    const onChange = vi.fn()
    render(<MonthPicker label="Effective from" value={currentMonth()} onChange={onChange} />)

    fireEvent.change(screen.getByLabelText('Effective from'), { target: { value: nextMonth() } })

    expect(onChange).toHaveBeenCalledWith(nextMonth())
  })

  it('clamps a future month back to the current month when clampToCurrentMonth is set', () => {
    const onChange = vi.fn()
    render(
      <MonthPicker label="Month" value={currentMonth()} onChange={onChange} clampToCurrentMonth />,
    )

    fireEvent.change(screen.getByLabelText('Month'), { target: { value: nextMonth() } })

    expect(onChange).toHaveBeenCalledWith(currentMonth())
  })

  it('passes a past month through unchanged when clamped', () => {
    const onChange = vi.fn()
    render(
      <MonthPicker label="Month" value={currentMonth()} onChange={onChange} clampToCurrentMonth />,
    )

    fireEvent.change(screen.getByLabelText('Month'), { target: { value: '2020-01' } })

    expect(onChange).toHaveBeenCalledWith('2020-01')
  })
})
