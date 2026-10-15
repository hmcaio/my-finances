import { describe, expect, it, vi } from 'vitest'
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { server } from '../../mocks/server'
import { accountCreateConflictHandler } from '../../mocks/handlers/accounts'
import { BUILT_IN_INSTITUTION_ID } from '../../mocks/handlers/institutions'
import { resolveErrorMessage } from '../../test/errorMessages'
import { OnboardingPage } from './OnboardingPage'
import { renderWithQueryClient } from '../../test/renderWithQueryClient'

async function createFirstAccount(user: ReturnType<typeof userEvent.setup>) {
  await screen.findByRole('combobox', { name: 'Institution' })
  await user.type(screen.getByLabelText('Name'), 'Main checking')
  await user.click(screen.getByRole('button', { name: 'Create account' }))
  await screen.findByRole('heading', { name: /suggested categories/i })
}

describe('OnboardingPage', () => {
  it('shows a two-step Stepper, starting on the account step', async () => {
    renderWithQueryClient(<OnboardingPage onCompleted={() => {}} />)

    expect(screen.getByRole('heading', { name: /welcome/i })).toBeInTheDocument()
    expect(screen.getByText(/starting point for tracking/i)).toBeInTheDocument()
    expect(screen.getByText('Account')).toBeInTheDocument()
    expect(screen.getByText('Categories')).toBeInTheDocument()
    expect(screen.getByLabelText('Name')).toBeInTheDocument()
    expect(screen.getByRole('combobox', { name: 'Account type' })).toBeInTheDocument()
    expect(await screen.findByRole('combobox', { name: 'Institution' })).toHaveValue(
      'No institution',
    )
    expect(screen.queryByRole('heading', { name: /suggested categories/i })).not.toBeInTheDocument()
  })

  it('advances to the categories step after creating the first account, without completing yet', async () => {
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
    const user = userEvent.setup({ delay: null })
    renderWithQueryClient(<OnboardingPage onCompleted={onCompleted} />)

    await createFirstAccount(user)

    expect(body).toMatchObject({
      name: 'Main checking',
      institutionId: BUILT_IN_INSTITUTION_ID,
      type: 'CHECKING',
    })
    expect(onCompleted).not.toHaveBeenCalled()
    expect(screen.getByRole('heading', { name: /suggested categories/i })).toBeInTheDocument()
    expect(screen.queryByRole('combobox', { name: 'Institution' })).not.toBeInTheDocument()
  })

  it('completes onboarding on Finish with a mix of checked, unchecked and custom categories', async () => {
    const categoryBodies: Record<string, unknown>[] = []
    server.use(
      http.post('/api/accounts', async ({ request }) => {
        const body = (await request.json()) as Record<string, unknown>
        return HttpResponse.json(
          { id: 'acct-new', ...body, closed: false, closedDate: null, balance: 0 },
          { status: 201 },
        )
      }),
      http.post('/api/categories', async ({ request }) => {
        const body = (await request.json()) as Record<string, unknown>
        categoryBodies.push(body)
        return HttpResponse.json(
          {
            id: `cat-${categoryBodies.length}`,
            ...body,
            builtIn: false,
            fuelCategory: false,
            dividendCategory: false,
          },
          { status: 201 },
        )
      }),
    )
    const onCompleted = vi.fn()
    const user = userEvent.setup({ delay: null })
    renderWithQueryClient(<OnboardingPage onCompleted={onCompleted} />)

    await createFirstAccount(user)

    await user.click(screen.getByRole('checkbox', { name: 'Rent' }))
    await user.type(screen.getByLabelText('Name'), 'Pet care')
    await user.click(screen.getByRole('button', { name: 'Add' }))
    await user.click(screen.getByRole('button', { name: 'Finish' }))

    await vi.waitFor(() => expect(onCompleted).toHaveBeenCalledTimes(1))
    expect(onCompleted.mock.calls[0][0]).toMatchObject({ id: 'acct-new' })
    const names = categoryBodies.map((b) => b.name)
    expect(names).toContain('Groceries')
    expect(names).toContain('Pet care')
    expect(names).not.toContain('Rent')
  })

  it('completes onboarding on Finish with every suggestion unchecked and nothing custom added', async () => {
    const categoryBodies: Record<string, unknown>[] = []
    server.use(
      http.post('/api/accounts', async ({ request }) => {
        const body = (await request.json()) as Record<string, unknown>
        return HttpResponse.json(
          { id: 'acct-new', ...body, closed: false, closedDate: null, balance: 0 },
          { status: 201 },
        )
      }),
      http.post('/api/categories', async ({ request }) => {
        categoryBodies.push((await request.json()) as Record<string, unknown>)
        return HttpResponse.json({}, { status: 201 })
      }),
    )
    const onCompleted = vi.fn()
    const user = userEvent.setup({ delay: null })
    renderWithQueryClient(<OnboardingPage onCompleted={onCompleted} />)

    await createFirstAccount(user)

    for (const box of screen.getAllByRole('checkbox')) {
      await user.click(box)
    }
    await user.click(screen.getByRole('button', { name: 'Finish' }))

    await vi.waitFor(() => expect(onCompleted).toHaveBeenCalledTimes(1))
    expect(categoryBodies).toHaveLength(0)
  })

  it('shows the error and stays on the account step when account creation fails', async () => {
    server.use(accountCreateConflictHandler)
    const onCompleted = vi.fn()
    const user = userEvent.setup({ delay: null })
    renderWithQueryClient(<OnboardingPage onCompleted={onCompleted} />)

    await screen.findByRole('combobox', { name: 'Institution' })
    await user.type(screen.getByLabelText('Name'), 'Main checking')
    await user.click(screen.getByRole('button', { name: 'Create account' }))

    expect(
      await screen.findByText(
        resolveErrorMessage('urn:my-finances:error:account-name-already-exists'),
      ),
    ).toBeInTheDocument()
    expect(onCompleted).not.toHaveBeenCalled()
    expect(screen.queryByRole('heading', { name: /suggested categories/i })).not.toBeInTheDocument()
  })
})
