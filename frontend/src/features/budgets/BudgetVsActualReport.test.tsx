import { describe, expect, it } from 'vitest'
import { http, HttpResponse } from 'msw'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useSetBudgetCap } from '../../api/budgets/budgetsQueries'
import { renderWithQueryClient } from '../../test/renderWithQueryClient'
import { server } from '../../mocks/server'
import { BudgetVsActualReport } from './BudgetVsActualReport'

describe('BudgetVsActualReport', () => {
  it('flags an over-cap category and names it', async () => {
    renderWithQueryClient(<BudgetVsActualReport month="2026-01" />)

    expect(await screen.findByText('Groceries')).toBeInTheDocument()
    expect(screen.getByText(/620\.00 \/ 500\.00 — over budget/)).toBeInTheDocument()
  })

  it('asks for the given month and refetches after a budget cap is saved', async () => {
    const months: (string | null)[] = []
    server.use(
      http.get('/api/budgets/report', ({ request }) => {
        months.push(new URL(request.url).searchParams.get('month'))
        return HttpResponse.json([])
      }),
    )
    const user = userEvent.setup({ delay: null })
    renderWithQueryClient(<WithCapEditor />)
    expect(await screen.findByText('No active budgets for this month.')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Save cap' }))

    await waitFor(() => expect(months).toEqual(['2026-03', '2026-03']))
  })
})

/** The report next to something that changes a budget, as the budgets page and dashboard have. */
function WithCapEditor() {
  const setCap = useSetBudgetCap()
  return (
    <>
      <BudgetVsActualReport month="2026-03" />
      <button
        onClick={() => setCap.mutate({ id: 'budget-1', monthlyCap: 1, effectiveFrom: '2026-03' })}
      >
        Save cap
      </button>
    </>
  )
}
