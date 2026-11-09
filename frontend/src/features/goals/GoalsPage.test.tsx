import { describe, expect, it } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { delay, http, HttpResponse } from 'msw'
import { server } from '../../mocks/server'
import { goalsStore, seedGoalCar, seedGoalFund, seedGoalOld } from '../../mocks/handlers/goals'
import { resolveErrorMessage } from '../../test/utils/errorMessages'
import { renderWithRouter } from '../../test/utils/testUtils'
import { GoalsPage } from './GoalsPage'

function renderPage() {
  renderWithRouter(<GoalsPage />)
  return userEvent.setup()
}

/** The card of a goal, found by its heading link. */
async function cardOf(name: string) {
  const heading = await screen.findByRole('heading', { name })
  return within(heading.closest('li') as HTMLElement)
}

describe('GoalsPage', () => {
  it('shows a delayed skeleton while the goals load', async () => {
    server.use(
      http.get('/api/goals', async () => {
        await delay(400)
        return HttpResponse.json([])
      }),
    )
    renderPage()

    expect(await screen.findByRole('status', { name: 'Loading goals' })).toBeInTheDocument()
  })

  it('lists the non-archived goals, dated first, each linking to its page', async () => {
    renderPage()

    const headings = await screen.findAllByRole('heading', { level: 2 })
    expect(headings.map((h) => h.textContent)).toEqual(['Car', 'Emergency fund'])
    expect(screen.queryByText('Old project')).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Car' })).toHaveAttribute('href', '/goals/goal-car')
  })

  it('shows the progress, the target date and the base-scenario status line of a dated goal', async () => {
    renderPage()

    const car = await cardOf('Car')
    expect(car.getByText('30,000.00 of 80,000.00 (37.5%)')).toBeInTheDocument()
    expect(car.getByText('Target date: November 15, 2028')).toBeInTheDocument()
    expect(car.getByText('Short by 5,000.00 by November 15, 2028')).toBeInTheDocument()
    expect(car.getByRole('progressbar', { name: 'Progress of Car' })).toHaveAttribute(
      'aria-valuenow',
      '37.5',
    )
    expect(car.queryByText('Target reached')).not.toBeInTheDocument()
  })

  it('shows when a dateless goal is reached', async () => {
    renderPage()

    const fund = await cardOf('Emergency fund')
    expect(fund.getByText('No target date')).toBeInTheDocument()
    expect(fund.getByText('Reached in 30 months')).toBeInTheDocument()
    expect(fund.getByText('Six months of expenses')).toBeInTheDocument()
  })

  it('badges a goal whose allocated value reached the target, with the bar capped at full', async () => {
    server.use(
      http.get('/api/goals', () =>
        HttpResponse.json([
          { ...seedGoalCar, targetReached: true, progressPercent: 125, projectedShortfall: -300 },
        ]),
      ),
    )
    renderPage()

    const car = await cardOf('Car')
    expect(car.getByText('Target reached')).toBeInTheDocument()
    expect(car.getByText('On track')).toBeInTheDocument()
    expect(car.getByText('30,000.00 of 80,000.00 (125.0%)')).toBeInTheDocument()
    expect(car.getByRole('progressbar')).toHaveAttribute('aria-valuenow', '100')
  })

  it('says a dateless goal is not reached within 50 years', async () => {
    server.use(
      http.get('/api/goals', () => HttpResponse.json([{ ...seedGoalFund, monthsToTarget: null }])),
    )
    renderPage()

    expect(await screen.findByText('Not reached within 50 years')).toBeInTheDocument()
  })

  it('shows the empty prompt, which opens the new-goal dialog', async () => {
    server.use(http.get('/api/goals', () => HttpResponse.json([])))
    const user = renderPage()

    expect(await screen.findByText('No goals yet')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Create your first goal' }))

    expect(await screen.findByRole('dialog', { name: 'New goal' })).toBeInTheDocument()
  })

  it('shows a load failure with a retry', async () => {
    server.use(
      http.get('/api/goals', () =>
        HttpResponse.json({ type: 'urn:my-finances:error:internal-error' }, { status: 500 }),
      ),
    )
    const user = renderPage()

    const retry = await screen.findByRole('button', { name: 'Retry' })
    server.resetHandlers()
    await user.click(retry)

    expect(await screen.findByRole('heading', { name: 'Car' })).toBeInTheDocument()
  })

  it('the dashboard switch calls the PATCH and reflects the new state', async () => {
    let sentBody: unknown = null
    let sentUrl = ''
    server.use(
      http.patch('/api/goals/:id/show-on-dashboard', async ({ request }) => {
        sentBody = await request.json()
        sentUrl = new URL(request.url).pathname
        goalsStore.replace('goal-car', (g) => ({ ...g, showOnDashboard: false }))
        return HttpResponse.json(goalsStore.find('goal-car'))
      }),
    )
    const user = renderPage()
    const car = await cardOf('Car')
    const toggle = car.getByRole('switch', { name: 'Show Car on the dashboard' })
    expect(toggle).toBeChecked()

    await user.click(toggle)

    await waitFor(() =>
      expect(
        within(screen.getByRole('heading', { name: 'Car' }).closest('li') as HTMLElement).getByRole(
          'switch',
          { name: 'Show Car on the dashboard' },
        ),
      ).not.toBeChecked(),
    )
    expect(sentUrl).toBe('/api/goals/goal-car/show-on-dashboard')
    expect(sentBody).toEqual({ showOnDashboard: false })
  })

  it('shows the banner when the switch is refused', async () => {
    server.use(
      http.patch('/api/goals/:id/show-on-dashboard', () =>
        HttpResponse.json({ type: 'urn:my-finances:error:goal-archived' }, { status: 409 }),
      ),
    )
    const user = renderPage()
    const car = await cardOf('Car')

    await user.click(car.getByRole('switch', { name: 'Show Car on the dashboard' }))

    expect(
      await screen.findByText(resolveErrorMessage('urn:my-finances:error:goal-archived')),
    ).toBeInTheDocument()
  })

  it('the archived filter adds archived goals, whose dashboard switch is disabled', async () => {
    const user = renderPage()
    await screen.findByRole('heading', { name: 'Car' })

    await user.click(screen.getByRole('switch', { name: 'Show archived' }))

    const old = await cardOf('Old project')
    expect(old.getByText('Archived')).toBeInTheDocument()
    expect(old.getByRole('switch', { name: 'Show Old project on the dashboard' })).toBeDisabled()
    expect(seedGoalOld.status).toBe('ARCHIVED')
  })

  it('creates a goal from the dialog and lists it', async () => {
    const user = renderPage()
    await screen.findByRole('heading', { name: 'Car' })

    await user.click(screen.getByRole('button', { name: 'New goal' }))
    const dialog = await screen.findByRole('dialog', { name: 'New goal' })
    await user.type(within(dialog).getByLabelText('Name'), 'Trip')
    await user.type(within(dialog).getByLabelText('Target amount'), '5000')
    await user.click(within(dialog).getByRole('button', { name: 'Create' }))

    expect(await screen.findByRole('heading', { name: 'Trip' })).toBeInTheDocument()
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
  })

  it('edits a goal from its card', async () => {
    const user = renderPage()
    const car = await cardOf('Car')

    await user.click(car.getByRole('button', { name: 'Edit Car' }))
    const dialog = await screen.findByRole('dialog', { name: 'Edit goal' })
    await user.clear(within(dialog).getByLabelText('Name'))
    await user.type(within(dialog).getByLabelText('Name'), 'New car')
    await user.click(within(dialog).getByRole('button', { name: 'Save' }))

    expect(await screen.findByRole('heading', { name: 'New car' })).toBeInTheDocument()
  })

  it('deletes a goal after confirming', async () => {
    const user = renderPage()
    const fund = await cardOf('Emergency fund')

    await user.click(fund.getByRole('button', { name: 'Delete Emergency fund' }))
    const dialog = await screen.findByRole('dialog', { name: 'Delete goal' })
    expect(
      within(dialog).getByText(
        'Delete "Emergency fund" and its allocations? This cannot be undone.',
      ),
    ).toBeInTheDocument()
    await user.click(within(dialog).getByRole('button', { name: 'Delete' }))

    await waitFor(() =>
      expect(screen.queryByRole('heading', { name: 'Emergency fund' })).not.toBeInTheDocument(),
    )
    expect(goalsStore.find('goal-fund')).toBeUndefined()
  })

  it('cancelling the confirmation deletes nothing', async () => {
    const user = renderPage()
    const fund = await cardOf('Emergency fund')

    await user.click(fund.getByRole('button', { name: 'Delete Emergency fund' }))
    await user.click(await screen.findByRole('button', { name: 'Cancel' }))

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(goalsStore.find('goal-fund')).toBeDefined()
  })
})
