import { describe, expect, it, vi } from 'vitest'
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { server } from '../../mocks/server'
import { categoryCreateConflictHandler } from '../../mocks/handlers/categories'
import { CategorySuggestionsStep } from './CategorySuggestionsStep'
import { renderWithQueryClient } from '../../test/renderWithQueryClient'

describe('CategorySuggestionsStep', () => {
  it('lists all 11 suggestions pre-checked, grouped expense then income', async () => {
    renderWithQueryClient(<CategorySuggestionsStep onError={() => {}} onCompleted={() => {}} />)

    const expenseNames = [
      'Groceries',
      'Rent',
      'Utilities',
      'Subscriptions',
      'Transport',
      'Dining',
      'Health',
      'Entertainment',
      'Education',
      'Insurance',
    ]
    for (const name of expenseNames) {
      expect(screen.getByRole('checkbox', { name })).toBeChecked()
    }
    expect(screen.getByRole('checkbox', { name: 'Salary' })).toBeChecked()

    const checkboxes = screen.getAllByRole('checkbox')
    expect(checkboxes).toHaveLength(11)
    // Expense group renders before the income group.
    const groceriesIndex = checkboxes.findIndex(
      (box) => box.closest('label')?.textContent === 'Groceries',
    )
    const salaryIndex = checkboxes.findIndex(
      (box) => box.closest('label')?.textContent === 'Salary',
    )
    expect(groceriesIndex).toBeLessThan(salaryIndex)
  })

  it('creates only the checked suggestions and the custom entries on Finish', async () => {
    const bodies: Record<string, unknown>[] = []
    server.use(
      http.post('/api/categories', async ({ request }) => {
        const body = (await request.json()) as Record<string, unknown>
        bodies.push(body)
        return HttpResponse.json(
          {
            id: `cat-${bodies.length}`,
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
    renderWithQueryClient(<CategorySuggestionsStep onError={() => {}} onCompleted={onCompleted} />)

    // Uncheck Rent and Salary.
    await user.click(screen.getByRole('checkbox', { name: 'Rent' }))
    await user.click(screen.getByRole('checkbox', { name: 'Salary' }))

    // Add a custom category.
    await user.type(screen.getByLabelText('Name'), 'Pet care')
    await user.click(screen.getByRole('button', { name: 'Add' }))
    expect(screen.getByText(/Pet care/)).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Finish' }))

    await vi.waitFor(() => expect(onCompleted).toHaveBeenCalledTimes(1))
    const names = bodies.map((b) => b.name)
    expect(names).toContain('Groceries')
    expect(names).toContain('Pet care')
    expect(names).not.toContain('Rent')
    expect(names).not.toContain('Salary')
    expect(names).toHaveLength(10) // 9 remaining suggestions + 1 custom
  })

  it('allows removing a custom entry before Finish', async () => {
    const user = userEvent.setup({ delay: null })
    renderWithQueryClient(<CategorySuggestionsStep onError={() => {}} onCompleted={() => {}} />)

    await user.type(screen.getByLabelText('Name'), 'Pet care')
    await user.click(screen.getByRole('button', { name: 'Add' }))
    expect(screen.getByText(/Pet care/)).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: /Remove Pet care/i }))
    expect(screen.queryByText(/Pet care/)).not.toBeInTheDocument()
  })

  it('is always allowed to Finish with every suggestion unchecked and nothing custom added', async () => {
    const bodies: Record<string, unknown>[] = []
    server.use(
      http.post('/api/categories', async ({ request }) => {
        bodies.push((await request.json()) as Record<string, unknown>)
        return HttpResponse.json({}, { status: 201 })
      }),
    )
    const onCompleted = vi.fn()
    const user = userEvent.setup({ delay: null })
    renderWithQueryClient(<CategorySuggestionsStep onError={() => {}} onCompleted={onCompleted} />)

    for (const box of screen.getAllByRole('checkbox')) {
      await user.click(box)
    }
    const finishButton = screen.getByRole('button', { name: 'Finish' })
    expect(finishButton).toBeEnabled()
    await user.click(finishButton)

    await vi.waitFor(() => expect(onCompleted).toHaveBeenCalledTimes(1))
    expect(bodies).toHaveLength(0)
  })

  it('shows the error and does not call onCompleted when a create fails', async () => {
    server.use(categoryCreateConflictHandler)
    const onCompleted = vi.fn()
    const onError = vi.fn()
    const user = userEvent.setup({ delay: null })
    renderWithQueryClient(<CategorySuggestionsStep onError={onError} onCompleted={onCompleted} />)

    await user.click(screen.getByRole('button', { name: 'Finish' }))

    await vi.waitFor(() => expect(onError).toHaveBeenCalled())
    expect(onError.mock.calls.some(([message]) => typeof message === 'string')).toBe(true)
    expect(onCompleted).not.toHaveBeenCalled()
  })
})
