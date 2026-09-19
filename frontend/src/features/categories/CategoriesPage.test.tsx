import { describe, expect, it } from 'vitest'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { delay, http, HttpResponse } from 'msw'
import { server } from '../../mocks/server'
import { categoryDeleteConflictHandler, seedCategories } from '../../mocks/handlers/categories'
import { CategoriesPage } from './CategoriesPage'

function findRow(name: string) {
  const cell = screen.getByText(name)
  return within(cell.closest('tr') as HTMLElement)
}

describe('CategoriesPage', () => {
  it('renders the seeded categories', async () => {
    render(<CategoriesPage />)

    for (const category of seedCategories) {
      expect(await screen.findByText(category.name)).toBeInTheDocument()
    }
  })

  it('adds a new category', async () => {
    const user = userEvent.setup()
    render(<CategoriesPage />)
    await screen.findByText(seedCategories[0].name)

    await user.type(screen.getByLabelText('Name'), 'Rent')
    await user.click(screen.getByRole('button', { name: 'Add' }))

    expect(await screen.findByText('Rent')).toBeInTheDocument()
  })

  it('renames a category inline', async () => {
    const user = userEvent.setup()
    render(<CategoriesPage />)
    await screen.findByText(seedCategories[0].name)

    const row = findRow(seedCategories[0].name)
    await user.click(row.getByRole('button', { name: 'Rename' }))
    const input = row.getByRole('textbox')
    await user.clear(input)
    await user.type(input, 'Groceries & Dining')
    await user.click(row.getByRole('button', { name: 'Save' }))

    expect(await screen.findByText('Groceries & Dining')).toBeInTheDocument()
    expect(screen.queryByText(seedCategories[0].name)).not.toBeInTheDocument()
  })

  it('deletes a category', async () => {
    const user = userEvent.setup()
    render(<CategoriesPage />)
    const name = seedCategories[1].name
    await screen.findByText(name)

    const row = findRow(name)
    await user.click(row.getByRole('button', { name: 'Delete' }))

    await waitFor(() => expect(screen.queryByText(name)).not.toBeInTheDocument())
  })

  it('surfaces the 409 conflict message when delete fails', async () => {
    server.use(categoryDeleteConflictHandler)
    const user = userEvent.setup()
    render(<CategoriesPage />)
    const name = seedCategories[0].name
    await screen.findByText(name)

    const row = findRow(name)
    await user.click(row.getByRole('button', { name: 'Delete' }))

    expect(await screen.findByText(/reassign them/)).toBeInTheDocument()
    // The row is still there - a 409 must not optimistically remove it.
    expect(screen.getByText(name)).toBeInTheDocument()
  })

  it('shows a loading skeleton only when the first fetch is slow, then the rows', async () => {
    server.use(
      http.get('/api/categories', async () => {
        await delay(400)
        return HttpResponse.json(seedCategories)
      }),
    )
    render(<CategoriesPage />)

    expect(await screen.findByText('Loading…')).toBeInTheDocument()
    expect(await screen.findByText(seedCategories[0].name)).toBeInTheDocument()
    expect(screen.queryByText('Loading…')).not.toBeInTheDocument()
  })

  it('shows a failure row, never a skeleton, after the first fetch fails - even once the banner is dismissed', async () => {
    server.use(http.get('/api/categories', () => new HttpResponse(null, { status: 500 })))
    const user = userEvent.setup()
    render(<CategoriesPage />)

    const alert = await screen.findByRole('alert')
    expect(await screen.findByText(/Could not load data/)).toBeInTheDocument()
    await user.click(within(alert).getByRole('button', { name: 'Close' }))
    // Wait past the 150ms skeleton delay: the dismissed banner must not bring the skeleton back.
    await new Promise((resolve) => setTimeout(resolve, 250))

    expect(screen.queryByText('Loading…')).not.toBeInTheDocument()
    expect(screen.getByText(/Could not load data/)).toBeInTheDocument()
  })

  it('retries a failed first fetch from the failure row', async () => {
    server.use(
      http.get('/api/categories', () => new HttpResponse(null, { status: 500 }), { once: true }),
    )
    const user = userEvent.setup()
    render(<CategoriesPage />)

    await user.click(await screen.findByRole('button', { name: 'Retry' }))

    expect(await screen.findByText(seedCategories[0].name)).toBeInTheDocument()
    expect(screen.queryByText(/Could not load data/)).not.toBeInTheDocument()
  })
})
