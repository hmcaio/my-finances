import { describe, expect, it } from 'vitest'
import { http, HttpResponse } from 'msw'
import { screen } from '@testing-library/react'
import { server } from '../../mocks/server'
import { SpendByCategoryWidget } from './SpendByCategoryWidget'
import { renderWithQueryClient } from '../../test/renderWithQueryClient'

describe('SpendByCategoryWidget', () => {
  it('lists each category with its total and the grand total', async () => {
    server.use(
      http.get('/api/transactions/spend-by-category', () =>
        HttpResponse.json([
          { categoryId: 'cat-1', total: 100 },
          { categoryId: 'cat-2', total: 25.5 },
        ]),
      ),
    )

    renderWithQueryClient(<SpendByCategoryWidget />)

    expect(await screen.findByText('Groceries')).toBeInTheDocument()
    expect(screen.getByText('100.00')).toBeInTheDocument()
    expect(screen.getByText('25.50')).toBeInTheDocument()
    expect(screen.getByText('Total 125.50')).toBeInTheDocument()
  })

  it('shows an empty state when nothing was spent this month', async () => {
    server.use(http.get('/api/transactions/spend-by-category', () => HttpResponse.json([])))

    renderWithQueryClient(<SpendByCategoryWidget />)

    expect(await screen.findByText('Nothing spent this month yet.')).toBeInTheDocument()
  })

  it('shows a retryable notice when the load fails', async () => {
    server.use(
      http.get('/api/transactions/spend-by-category', () => HttpResponse.json({}, { status: 500 })),
    )

    renderWithQueryClient(<SpendByCategoryWidget />)

    expect(await screen.findByRole('button', { name: /retry/i })).toBeInTheDocument()
  })
})
