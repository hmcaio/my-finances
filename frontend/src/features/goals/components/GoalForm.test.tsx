import { describe, expect, it, vi } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { server } from '../../../mocks/server'
import { goalsStore, seedGoalCar, seedGoalOld } from '../../../mocks/handlers/goals'
import { resolveErrorMessage } from '../../../test/utils/errorMessages'
import { selectOption } from '../../../test/utils/testUtils'
import { renderWithQueryClient } from '../../../test/utils/renderWithQueryClient'
import type { Goal } from '../../../api/goals/goals'
import { GoalForm } from './GoalForm'

function renderForm(goal?: Goal, onClose = vi.fn()) {
  renderWithQueryClient(<GoalForm open goal={goal} onClose={onClose} />)
  return { onClose, user: userEvent.setup() }
}

async function fillValidCreate(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByLabelText('Name'), 'Trip')
  await user.type(screen.getByLabelText('Target amount'), '5000')
}

describe('GoalForm - create', () => {
  it('opens as a new goal with the default assumptions and no status field', () => {
    renderForm()

    expect(screen.getByRole('dialog', { name: 'New goal' })).toBeInTheDocument()
    expect(screen.getByLabelText('Planned monthly contribution')).toHaveValue(0)
    expect(screen.getByLabelText('Pessimistic return (% a year)')).toHaveValue(2)
    expect(screen.getByLabelText('Base return (% a year)')).toHaveValue(4)
    expect(screen.getByLabelText('Optimistic return (% a year)')).toHaveValue(6)
    expect(screen.queryByRole('combobox', { name: 'Status' })).not.toBeInTheDocument()
    expect(screen.getByRole('switch', { name: 'Show on dashboard' })).toBeChecked()
  })

  it('bounds the name and notes inputs', () => {
    renderForm()

    expect(screen.getByLabelText('Name')).toHaveAttribute('maxlength', '100')
    expect(screen.getByLabelText('Notes')).toHaveAttribute('maxlength', '500')
  })

  it('cannot be created without a name and a positive target', async () => {
    const { user } = renderForm()
    const create = screen.getByRole('button', { name: 'Create' })
    expect(create).toBeDisabled()

    await user.type(screen.getByLabelText('Name'), '   ')
    await user.type(screen.getByLabelText('Target amount'), '5000')
    expect(create).toBeDisabled()

    await user.clear(screen.getByLabelText('Name'))
    await user.type(screen.getByLabelText('Name'), 'Trip')
    expect(create).toBeEnabled()

    await user.clear(screen.getByLabelText('Target amount'))
    await user.type(screen.getByLabelText('Target amount'), '0')
    expect(create).toBeDisabled()
  })

  it('rejects a negative contribution', async () => {
    const { user } = renderForm()
    await fillValidCreate(user)

    await user.clear(screen.getByLabelText('Planned monthly contribution'))
    await user.type(screen.getByLabelText('Planned monthly contribution'), '-5')

    expect(screen.getByRole('button', { name: 'Create' })).toBeDisabled()
  })

  it('flags returns that do not run from pessimistic to optimistic', async () => {
    const { user } = renderForm()
    await fillValidCreate(user)

    await user.clear(screen.getByLabelText('Pessimistic return (% a year)'))
    await user.type(screen.getByLabelText('Pessimistic return (% a year)'), '9')

    expect(
      screen.getByText('Returns must run from pessimistic to base to optimistic.'),
    ).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Create' })).toBeDisabled()
  })

  it('flags a return outside -50 to 100', async () => {
    const { user } = renderForm()
    await fillValidCreate(user)

    await user.clear(screen.getByLabelText('Optimistic return (% a year)'))
    await user.type(screen.getByLabelText('Optimistic return (% a year)'), '101')

    expect(screen.getByText('Returns must be between -50 and 100.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Create' })).toBeDisabled()
  })

  it('requires a target date in the future when one is given', async () => {
    const { user } = renderForm()
    await fillValidCreate(user)

    await user.type(screen.getByLabelText('Target date (optional)'), '2020-01-01')

    expect(screen.getByText('The target date must be in the future.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Create' })).toBeDisabled()
  })

  it('creates the goal with the typed values and closes', async () => {
    let sentBody: unknown = null
    server.use(
      http.post('/api/goals', async ({ request }) => {
        sentBody = await request.json()
        return HttpResponse.json(seedGoalCar, { status: 201 })
      }),
    )
    const { onClose, user } = renderForm()
    await fillValidCreate(user)
    await user.type(screen.getByLabelText('Notes'), 'Summer')
    await user.type(screen.getByLabelText('Target date (optional)'), '2099-06-15')
    await user.clear(screen.getByLabelText('Planned monthly contribution'))
    await user.type(screen.getByLabelText('Planned monthly contribution'), '250.5')

    await user.click(screen.getByRole('button', { name: 'Create' }))

    await waitFor(() => expect(onClose).toHaveBeenCalled())
    expect(sentBody).toEqual({
      name: 'Trip',
      notes: 'Summer',
      targetAmount: 5000,
      targetDate: '2099-06-15',
      plannedMonthlyContribution: 250.5,
      returnPessimistic: 2,
      returnBase: 4,
      returnOptimistic: 6,
      showOnDashboard: true,
    })
  })

  it('omits an empty target date and notes', async () => {
    let sentBody: Record<string, unknown> = {}
    server.use(
      http.post('/api/goals', async ({ request }) => {
        sentBody = (await request.json()) as Record<string, unknown>
        return HttpResponse.json(seedGoalCar, { status: 201 })
      }),
    )
    const { user } = renderForm()
    await fillValidCreate(user)

    await user.click(screen.getByRole('button', { name: 'Create' }))

    await waitFor(() => expect(Object.keys(sentBody).length).toBeGreaterThan(0))
    expect(sentBody).not.toHaveProperty('targetDate')
    expect(sentBody).not.toHaveProperty('notes')
  })

  it('keeps the dialog open and shows the mapped message when the save fails', async () => {
    server.use(
      http.post('/api/goals', () =>
        HttpResponse.json({ type: 'urn:my-finances:error:invalid-goal' }, { status: 400 }),
      ),
    )
    const { onClose, user } = renderForm()
    await fillValidCreate(user)

    await user.click(screen.getByRole('button', { name: 'Create' }))

    expect(
      await screen.findByText(resolveErrorMessage('urn:my-finances:error:invalid-goal')),
    ).toBeInTheDocument()
    expect(onClose).not.toHaveBeenCalled()
  })

  it('fills the target from the helper and stores nothing', async () => {
    const { user } = renderForm()
    await user.click(screen.getByRole('button', { name: 'Suggest a target' }))
    await screen.findByText('Suggested target: 24,000.00')

    await user.click(screen.getByRole('button', { name: 'Use this target' }))

    await waitFor(() => expect(screen.getByLabelText('Target amount')).toHaveValue(24000))
    expect(goalsStore.list()).toHaveLength(3)
  })
})

describe('GoalForm - edit', () => {
  it('opens prefilled with the goal and offers the status', () => {
    renderForm(seedGoalCar)

    expect(screen.getByRole('dialog', { name: 'Edit goal' })).toBeInTheDocument()
    expect(screen.getByLabelText('Name')).toHaveValue('Car')
    expect(screen.getByLabelText('Target amount')).toHaveValue(80000)
    expect(screen.getByLabelText('Target date (optional)')).toHaveValue('2028-11-15')
    expect(screen.getByRole('combobox', { name: 'Status' })).toHaveTextContent('Active')
    expect(screen.getByRole('button', { name: 'Save' })).toBeEnabled()
  })

  it('saves the full goal including the status', async () => {
    let sentBody: unknown = null
    server.use(
      http.patch('/api/goals/:id', async ({ request }) => {
        sentBody = await request.json()
        return HttpResponse.json(seedGoalCar)
      }),
    )
    const { onClose, user } = renderForm(seedGoalCar)
    await selectOption(user, 'Status', 'Achieved')

    await user.click(screen.getByRole('button', { name: 'Save' }))

    await waitFor(() => expect(onClose).toHaveBeenCalled())
    expect(sentBody).toEqual({
      name: 'Car',
      targetAmount: 80000,
      targetDate: '2028-11-15',
      plannedMonthlyContribution: 2000,
      returnPessimistic: 2,
      returnBase: 4,
      returnOptimistic: 6,
      status: 'ACHIEVED',
      showOnDashboard: true,
    })
  })

  it('accepts a target date that has since passed when it is unchanged', async () => {
    renderForm({ ...seedGoalCar, targetDate: '2020-01-01' })

    expect(screen.queryByText('The target date must be in the future.')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Save' })).toBeEnabled()
  })

  it('rejects a changed target date that is not in the future', async () => {
    const { user } = renderForm(seedGoalCar)

    await user.clear(screen.getByLabelText('Target date (optional)'))
    await user.type(screen.getByLabelText('Target date (optional)'), '2020-01-01')

    expect(screen.getByText('The target date must be in the future.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Save' })).toBeDisabled()
  })

  it('clearing the target date makes the goal dateless', async () => {
    let sentBody: Record<string, unknown> = {}
    server.use(
      http.patch('/api/goals/:id', async ({ request }) => {
        sentBody = (await request.json()) as Record<string, unknown>
        return HttpResponse.json(seedGoalCar)
      }),
    )
    const { user } = renderForm(seedGoalCar)

    await user.clear(screen.getByLabelText('Target date (optional)'))
    await user.click(screen.getByRole('button', { name: 'Save' }))

    await waitFor(() => expect(Object.keys(sentBody).length).toBeGreaterThan(0))
    expect(sentBody).not.toHaveProperty('targetDate')
  })

  it('an archived goal shows the dashboard switch off and disabled; restoring enables it', async () => {
    const { user } = renderForm(seedGoalOld)
    const show = screen.getByRole('switch', { name: 'Show on dashboard' })
    expect(show).not.toBeChecked()
    expect(show).toBeDisabled()

    await selectOption(user, 'Status', 'Active')

    expect(screen.getByRole('switch', { name: 'Show on dashboard' })).toBeEnabled()
    expect(screen.getByRole('switch', { name: 'Show on dashboard' })).not.toBeChecked()
  })

  it('archiving turns the dashboard switch off and disables it', async () => {
    const { user } = renderForm(seedGoalCar)
    expect(screen.getByRole('switch', { name: 'Show on dashboard' })).toBeChecked()

    await selectOption(user, 'Status', 'Archived')

    expect(screen.getByRole('switch', { name: 'Show on dashboard' })).not.toBeChecked()
    expect(screen.getByRole('switch', { name: 'Show on dashboard' })).toBeDisabled()
  })

  it('shows the exceeds-100% conflict when restoring does not fit, and stays open', async () => {
    const { onClose, user } = renderForm(seedGoalOld)
    await selectOption(user, 'Status', 'Active')

    await user.click(screen.getByRole('button', { name: 'Save' }))

    expect(
      await screen.findByText(
        resolveErrorMessage('urn:my-finances:error:goal-allocation-exceeds-product'),
      ),
    ).toBeInTheDocument()
    expect(onClose).not.toHaveBeenCalled()
  })
})
