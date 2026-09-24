import { describe, expect, it } from 'vitest'
import { http, HttpResponse } from 'msw'
import { render, screen } from '@testing-library/react'
import { server } from '../../mocks/server'
import { BudgetVsActualReport } from './BudgetVsActualReport'

describe('BudgetVsActualReport', () => {
  it('flags an over-cap category and names it', async () => {
    render(<BudgetVsActualReport month="2026-01" />)

    expect(await screen.findByText('Groceries')).toBeInTheDocument()
    expect(screen.getByText(/620\.00 \/ 500\.00 — over budget/)).toBeInTheDocument()
  })

  it('asks for the given month and refetches when reloadKey changes', async () => {
    const months: (string | null)[] = []
    server.use(
      http.get('/api/budgets/report', ({ request }) => {
        months.push(new URL(request.url).searchParams.get('month'))
        return HttpResponse.json([])
      }),
    )

    const { rerender } = render(<BudgetVsActualReport month="2026-03" reloadKey={0} />)
    expect(await screen.findByText('No budgeted categories yet.')).toBeInTheDocument()
    rerender(<BudgetVsActualReport month="2026-03" reloadKey={1} />)
    await screen.findByText('No budgeted categories yet.')

    expect(months).toEqual(['2026-03', '2026-03'])
  })
})
