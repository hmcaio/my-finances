import { describe, expect, it, vi } from 'vitest'
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { server } from '../../../mocks/server'
import { seedEmergencyFundSuggestion } from '../../../mocks/handlers/goals'
import { renderWithQueryClient } from '../../../test/utils/renderWithQueryClient'
import { TargetHelperDialog } from './TargetHelperDialog'

function renderHelper(onApply = vi.fn(), onClose = vi.fn()) {
  renderWithQueryClient(<TargetHelperDialog open onApply={onApply} onClose={onClose} />)
  return { onApply, onClose, user: userEvent.setup() }
}

describe('TargetHelperDialog', () => {
  it('suggests the emergency-fund target as months times the average expense', async () => {
    const { onApply, user } = renderHelper()

    expect(
      await screen.findByText('Average monthly expense over the last 12 full months: 4,000.00'),
    ).toBeInTheDocument()
    expect(screen.getByText('Suggested target: 24,000.00')).toBeInTheDocument()
    expect(screen.getByLabelText('Months of expenses')).toHaveValue(6)

    await user.click(screen.getByRole('button', { name: 'Use this target' }))

    expect(onApply).toHaveBeenCalledWith(24000)
  })

  it('asks again when the months change', async () => {
    const requestedMonths: string[] = []
    server.use(
      http.get('/api/goals/emergency-fund-suggestion', ({ request }) => {
        const months = new URL(request.url).searchParams.get('months') ?? ''
        requestedMonths.push(months)
        return HttpResponse.json({
          ...seedEmergencyFundSuggestion,
          months: Number(months),
          suggestedTarget: Number(months) * 4000,
        })
      }),
    )
    const { onApply, user } = renderHelper()
    await screen.findByText('Suggested target: 24,000.00')

    const months = screen.getByLabelText('Months of expenses')
    await user.clear(months)
    await user.type(months, '3')

    expect(await screen.findByText('Suggested target: 12,000.00')).toBeInTheDocument()
    expect(requestedMonths).toContain('3')
    await user.click(screen.getByRole('button', { name: 'Use this target' }))
    expect(onApply).toHaveBeenCalledWith(12000)
  })

  it('does not ask, and offers nothing, for months outside 1 to 120', async () => {
    let calls = 0
    server.use(
      http.get('/api/goals/emergency-fund-suggestion', () => {
        calls += 1
        return HttpResponse.json(seedEmergencyFundSuggestion)
      }),
    )
    const { user } = renderHelper()
    await screen.findByText('Suggested target: 24,000.00')
    const before = calls

    const months = screen.getByLabelText('Months of expenses')
    await user.clear(months)
    await user.type(months, '0')

    expect(screen.getByRole('button', { name: 'Use this target' })).toBeDisabled()
    expect(calls).toBe(before)
  })

  it('says so, and offers nothing, when there are no expenses to average', async () => {
    server.use(
      http.get('/api/goals/emergency-fund-suggestion', () =>
        HttpResponse.json({
          months: 6,
          averageMonthlyExpense: 0,
          suggestedTarget: 0,
          monthsAveraged: 0,
        }),
      ),
    )
    renderHelper()

    expect(
      await screen.findByText('No expenses recorded yet, so there is no average to suggest from.'),
    ).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Use this target' })).toBeDisabled()
  })

  it('shows a load failure instead of a suggestion', async () => {
    server.use(
      http.get('/api/goals/emergency-fund-suggestion', () =>
        HttpResponse.json({ type: 'urn:my-finances:error:internal-error' }, { status: 500 }),
      ),
    )
    renderHelper()

    expect(await screen.findByText('Could not load your average expense.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Use this target' })).toBeDisabled()
  })

  it('suggests the retirement target as income times twelve over the withdrawal rate', async () => {
    const { onApply, user } = renderHelper()
    await user.click(screen.getByRole('button', { name: 'Retirement' }))

    expect(screen.getByLabelText('Withdrawal rate (% a year)')).toHaveValue(4)
    expect(screen.getByRole('button', { name: 'Use this target' })).toBeDisabled()

    await user.type(screen.getByLabelText('Desired monthly income'), '8000')

    expect(screen.getByText('Suggested target: 2,400,000.00')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Use this target' }))
    expect(onApply).toHaveBeenCalledWith(2400000)
  })

  it('the retirement helper does not ask the backend for anything', async () => {
    let calls = 0
    server.use(
      http.get('/api/goals/emergency-fund-suggestion', () => {
        calls += 1
        return HttpResponse.json(seedEmergencyFundSuggestion)
      }),
    )
    const { user } = renderHelper()
    await screen.findByText('Suggested target: 24,000.00')
    const before = calls

    await user.click(screen.getByRole('button', { name: 'Retirement' }))
    await user.type(screen.getByLabelText('Desired monthly income'), '1000')

    expect(calls).toBe(before)
  })

  it('closing without applying fills nothing', async () => {
    const { onApply, onClose, user } = renderHelper()
    await screen.findByText('Suggested target: 24,000.00')

    await user.click(screen.getByRole('button', { name: 'Cancel' }))

    expect(onClose).toHaveBeenCalled()
    expect(onApply).not.toHaveBeenCalled()
  })
})
