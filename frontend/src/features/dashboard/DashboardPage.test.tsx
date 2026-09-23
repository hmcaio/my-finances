import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { server } from '../../mocks/server'
import { DashboardPage } from './DashboardPage'

describe('DashboardPage', () => {
  it('shows a success alert once the backend health check resolves', async () => {
    server.use(
      http.get('/api/health', () =>
        HttpResponse.json({ status: 'UP', timestamp: '2026-01-01T00:00:00Z' }),
      ),
    )

    render(<DashboardPage />)

    expect(screen.getByText('Checking backend health...')).toBeInTheDocument()
    expect(await screen.findByRole('alert')).toHaveTextContent('Backend is UP as of')
  })

  it('shows an error alert when the health check fails', async () => {
    server.use(http.get('/api/health', () => new HttpResponse(null, { status: 500 })))

    render(<DashboardPage />)

    expect(await screen.findByRole('alert')).toHaveTextContent('Could not reach the backend')
  })
})
