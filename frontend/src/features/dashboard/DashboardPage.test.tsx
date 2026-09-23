import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import { server } from '../../mocks/server'
import { healthDownHandler } from '../../mocks/handlers/health'
import { DashboardPage } from './DashboardPage'

describe('DashboardPage', () => {
  it('shows a success alert once the backend health check resolves', async () => {
    // The default `GET /api/health` handler already answers UP - no override needed.
    render(<DashboardPage />)

    expect(screen.getByText('Checking backend health...')).toBeInTheDocument()
    expect(await screen.findByRole('alert')).toHaveTextContent('Backend is UP as of')
  })

  it('shows an error alert when the health check fails', async () => {
    server.use(healthDownHandler)

    render(<DashboardPage />)

    expect(await screen.findByRole('alert')).toHaveTextContent('Could not reach the backend')
  })
})
