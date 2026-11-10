import { describe, expect, it } from 'vitest'
import { http, HttpResponse } from 'msw'
import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { server } from '../../mocks/server'
import { seedDebitCardPaymentMethod } from '../../mocks/handlers/paymentMethods'
import { goalsStore } from '../../mocks/handlers/goals'
import { emittedCss, emotionClass } from '../../test/utils/emittedCss'
import { findRow } from '../../test/utils/testUtils'
import { DashboardPage } from './DashboardPage'
import { renderWithQueryClient } from '../../test/utils/renderWithQueryClient'

function renderDashboard() {
  return renderWithQueryClient(
    <MemoryRouter>
      <DashboardPage />
    </MemoryRouter>,
  )
}

describe('DashboardPage', () => {
  it('renders every widget with data from its owning feature', async () => {
    renderDashboard()

    expect(screen.getByRole('heading', { name: 'Dashboard' })).toBeInTheDocument()

    const spend = screen.getByRole('region', { name: 'Spend by category' })
    expect(await within(spend).findByText('Total 42.50')).toBeInTheDocument()

    const budgets = screen.getByRole('region', { name: 'Budget vs. actual' })
    expect(await within(budgets).findByText(/620\.00 \/ 500\.00 — over budget/)).toBeInTheDocument()

    const accounts = screen.getByRole('region', { name: 'Account balances' })
    expect(await within(accounts).findByRole('link', { name: 'Itau Checking' })).toBeInTheDocument()

    const netWorth = screen.getByRole('region', { name: 'Net worth' })
    expect(
      await within(netWorth).findByRole('img', { name: 'Net worth by month' }),
    ).toBeInTheDocument()

    const allocation = screen.getByRole('region', { name: 'Investment allocation' })
    expect(await within(allocation).findByRole('img')).toBeInTheDocument()

    const goals = await screen.findByRole('region', { name: 'Goals' })
    expect(await within(goals).findByRole('link', { name: 'Car' })).toBeInTheDocument()

    expect(screen.getByRole('heading', { name: 'Upcoming recurring bills' })).toBeInTheDocument()
    expect(await findRow('Rent')).toBeDefined()
  })

  it('refreshes the transaction-dependent widgets after confirming a pending occurrence', async () => {
    const user = userEvent.setup({ delay: null })
    let spendRequests = 0
    server.use(
      http.get('/api/transactions/spend-by-category', () => {
        spendRequests += 1
        return HttpResponse.json(
          spendRequests === 1
            ? [{ categoryId: 'cat-1', total: 42.5 }]
            : [{ categoryId: 'cat-1', total: 1692.5 }],
        )
      }),
    )
    renderDashboard()
    const spend = screen.getByRole('region', { name: 'Spend by category' })
    expect(await within(spend).findByText('Total 42.50')).toBeInTheDocument()

    const row = await findRow('Rent')
    await user.click(row.getByRole('button', { name: 'Confirm occurrence' }))
    await user.click(screen.getByLabelText('Payment Method'))
    await user.click(await screen.findByRole('option', { name: seedDebitCardPaymentMethod.name }))
    await user.click(screen.getByRole('button', { name: 'Confirm' }))

    expect(await within(spend).findByText('Total 1,692.50')).toBeInTheDocument()
    expect(screen.getByText('Nothing pending right now.')).toBeInTheDocument()
  })

  it('removes a dismissed occurrence without refetching the other widgets', async () => {
    const user = userEvent.setup({ delay: null })
    let spendRequests = 0
    server.use(
      http.get('/api/transactions/spend-by-category', () => {
        spendRequests += 1
        return HttpResponse.json([{ categoryId: 'cat-1', total: 42.5 }])
      }),
    )
    renderDashboard()
    const row = await findRow('Rent')

    await user.click(row.getByRole('button', { name: 'Dismiss occurrence' }))
    await user.click(screen.getByRole('button', { name: 'Dismiss' }))

    expect(await screen.findByText('Nothing pending right now.')).toBeInTheDocument()
    expect(spendRequests).toBe(1)
  })

  describe('layout (F040)', () => {
    /** The grid the widgets are children of. */
    function grid() {
      return screen.getByRole('region', { name: 'Spend by category' }).parentElement as HTMLElement
    }

    /** What each grid child is called: its region label, or the heading of the pending widget. */
    function childNames() {
      return Array.from(grid().children).map(
        (child) => child.getAttribute('aria-label') ?? child.querySelector('h2')?.textContent,
      )
    }

    const css = () => emittedCss().replace(/\s+/g, '')

    it('lists spend, budget, balances, allocation, net worth, goals and pending in that order', async () => {
      renderDashboard()
      await screen.findByRole('region', { name: 'Goals' })

      expect(childNames()).toEqual([
        'Spend by category',
        'Budget vs. actual',
        'Account balances',
        'Investment allocation',
        'Net worth',
        'Goals',
        'Upcoming recurring bills',
      ])
    })

    it('spans net worth two columns at lg and the whole row below, goals and pending the whole row from sm', async () => {
      renderDashboard()
      const goals = await screen.findByRole('region', { name: 'Goals' })

      const netWorth = screen.getByRole('region', { name: 'Net worth' })
      const pending = grid().lastElementChild as HTMLElement
      expect(css()).toContain(
        `@media(min-width:600px){.${emotionClass(netWorth)}{grid-column:1/-1;}}`,
      )
      expect(css()).toContain(
        `@media(min-width:1200px){.${emotionClass(netWorth)}{grid-column:span2;}}`,
      )
      expect(css()).toContain(`@media(min-width:600px){.${emotionClass(goals)}{grid-column:1/-1;}}`)
      expect(css()).toContain(
        `@media(min-width:600px){.${emotionClass(pending)}{grid-column:1/-1;}}`,
      )
    })

    it('leaves no gap where the goals card would be while no goal is flagged', async () => {
      goalsStore.replace('goal-car', (g) => ({ ...g, showOnDashboard: false }))
      goalsStore.replace('goal-fund', (g) => ({ ...g, showOnDashboard: false }))
      renderDashboard()
      await within(screen.getByRole('region', { name: 'Net worth' })).findByRole('img')
      await findRow('Rent')
      await new Promise((resolve) => setTimeout(resolve, 200))

      // Six children, none of them an empty placeholder for the missing card.
      expect(childNames()).toEqual([
        'Spend by category',
        'Budget vs. actual',
        'Account balances',
        'Investment allocation',
        'Net worth',
        'Upcoming recurring bills',
      ])
      expect(Array.from(grid().children).every((child) => child.childElementCount > 0)).toBe(true)
    })

    it('makes a card fill its row: the section is a flex column and its card grows', async () => {
      renderDashboard()
      await screen.findByRole('region', { name: 'Goals' })

      for (const name of [
        'Spend by category',
        'Budget vs. actual',
        'Account balances',
        'Investment allocation',
        'Net worth',
      ]) {
        const section = screen.getByRole('region', { name })
        const sectionRule = css().match(new RegExp(`\\.${emotionClass(section)}\\{([^}]*)\\}`))
        expect(sectionRule?.[1], `${name} section`).toContain('display:flex')
        expect(sectionRule?.[1], `${name} section`).toContain('flex-direction:column')
        const card = section.querySelector('.MuiPaper-root') as HTMLElement
        expect(css(), `${name} card`).toMatch(
          new RegExp(`\\.${emotionClass(card)}\\{[^}]*flex-grow:1[;}]`),
        )
      }
    })

    it('puts the budget report in an outlined card like the other widgets', async () => {
      renderDashboard()

      const budgets = screen.getByRole('region', { name: 'Budget vs. actual' })
      const card = budgets.querySelector('.MuiPaper-root')
      expect(card).toHaveClass('MuiPaper-outlined')
      // One card per section: the report is not nested in a second Paper.
      expect(budgets.querySelectorAll('.MuiPaper-root')).toHaveLength(1)
    })
  })
})
