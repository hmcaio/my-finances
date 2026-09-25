import { describe, expect, it, vi } from 'vitest'
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { server } from '../../mocks/server'
import { accountCreateConflictHandler } from '../../mocks/handlers/accounts'
import { BUILT_IN_INSTITUTION_ID } from '../../mocks/handlers/institutions'
import { DUPLICATE_NAME_MESSAGE } from '../../api/accounts/accounts'
import { OnboardingPage } from './OnboardingPage'
import { renderWithQueryClient } from '../../test/renderWithQueryClient'

describe('OnboardingPage', () => {
  it('explains the starting point and offers the account fields', async () => {
    renderWithQueryClient(<OnboardingPage onCompleted={() => {}} />)

    expect(screen.getByRole('heading', { name: /welcome/i })).toBeInTheDocument()
    expect(screen.getByText(/starting point for tracking/i)).toBeInTheDocument()
    expect(screen.getByLabelText('Name')).toBeInTheDocument()
    expect(screen.getByRole('combobox', { name: 'Account type' })).toBeInTheDocument()
    expect(screen.getByLabelText('Opening Balance')).toBeInTheDocument()
    expect(screen.getByLabelText('Opening Balance Date')).toBeInTheDocument()
    expect(await screen.findByRole('combobox', { name: 'Institution' })).toHaveValue(
      'No institution',
    )
  })

  it('creates the first account with the preselected institution and reports completion', async () => {
    let body: Record<string, unknown> | null = null
    server.use(
      http.post('/api/accounts', async ({ request }) => {
        body = (await request.json()) as Record<string, unknown>
        return HttpResponse.json(
          { id: 'acct-new', ...body, closed: false, closedDate: null, balance: 250 },
          { status: 201 },
        )
      }),
    )
    const onCompleted = vi.fn()
    const user = userEvent.setup()
    renderWithQueryClient(<OnboardingPage onCompleted={onCompleted} />)

    await screen.findByRole('combobox', { name: 'Institution' })
    await user.type(screen.getByLabelText('Name'), 'Main checking')
    await user.clear(screen.getByLabelText('Opening Balance'))
    await user.type(screen.getByLabelText('Opening Balance'), '250')
    await user.click(screen.getByRole('button', { name: 'Create account' }))

    await vi.waitFor(() => expect(onCompleted).toHaveBeenCalledTimes(1))
    expect(body).toMatchObject({
      name: 'Main checking',
      institutionId: BUILT_IN_INSTITUTION_ID,
      type: 'CHECKING',
      openingBalance: 250,
    })
  })

  it('shows the error and stays on the form when creation fails', async () => {
    server.use(accountCreateConflictHandler)
    const onCompleted = vi.fn()
    const user = userEvent.setup()
    renderWithQueryClient(<OnboardingPage onCompleted={onCompleted} />)

    await screen.findByRole('combobox', { name: 'Institution' })
    await user.type(screen.getByLabelText('Name'), 'Main checking')
    await user.click(screen.getByRole('button', { name: 'Create account' }))

    expect(await screen.findByText(DUPLICATE_NAME_MESSAGE)).toBeInTheDocument()
    expect(onCompleted).not.toHaveBeenCalled()
  })
})
