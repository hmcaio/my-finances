import { describe, expect, it } from 'vitest'
import { http, HttpResponse } from 'msw'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { server } from '../../mocks/server'
import { BackupStatusBanner } from './BackupStatusBanner'
import { renderWithQueryClient } from '../../test/renderWithQueryClient'
import type { BackupStatus } from '../../api/backupStatus/backupStatus'

function mockStatus(overrides: Partial<BackupStatus>) {
  const base: BackupStatus = {
    state: 'OK',
    localOnly: false,
    lastSuccessAt: '2026-01-01T06:00:00Z',
    lastAttemptAt: '2026-01-01T06:00:00Z',
    lastError: null,
    imageTag: 'abc1234',
    buildId: 'abc1234',
    schemaVersion: '42',
    targetType: 'bind',
    remoteConfigured: false,
    remoteOk: null,
    count: 3,
  }
  const status = { ...base, ...overrides }
  server.use(http.get('/api/backup-status', () => HttpResponse.json(status)))
  return status
}

describe('BackupStatusBanner', () => {
  it('renders nothing for OK (not local-only)', async () => {
    mockStatus({ state: 'OK', localOnly: false })

    const { container } = renderWithQueryClient(<BackupStatusBanner />)

    await waitFor(() => expect(container).toBeEmptyDOMElement())
  })

  it('renders nothing for UNKNOWN', async () => {
    mockStatus({
      state: 'UNKNOWN',
      localOnly: false,
      lastSuccessAt: null,
      lastAttemptAt: null,
    })

    const { container } = renderWithQueryClient(<BackupStatusBanner />)

    await waitFor(() => expect(container).toBeEmptyDOMElement())
  })

  it('warns for STALE, naming the last success time', async () => {
    mockStatus({ state: 'STALE', lastSuccessAt: '2026-01-01T06:00:00Z' })

    renderWithQueryClient(<BackupStatusBanner />)

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent(/stale/i)
    expect(alert).toHaveTextContent('2026')
  })

  it('warns for STALE with no last success at all', async () => {
    mockStatus({ state: 'STALE', lastSuccessAt: null })

    renderWithQueryClient(<BackupStatusBanner />)

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent(/never/i)
  })

  it('warns for FAILING, naming the last success time', async () => {
    mockStatus({
      state: 'FAILING',
      lastError: 'disk_full',
      lastSuccessAt: '2026-01-01T06:00:00Z',
    })

    renderWithQueryClient(<BackupStatusBanner />)

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent(/failing/i)
    expect(alert).toHaveTextContent('2026')
  })

  it('shows an info banner for localOnly (when otherwise OK)', async () => {
    mockStatus({ state: 'OK', localOnly: true })

    renderWithQueryClient(<BackupStatusBanner />)

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent(/only on this machine/i)
  })

  it('shows the warning, not the localOnly info banner, when both conditions hold', async () => {
    mockStatus({ state: 'FAILING', localOnly: true, lastError: 'disk_full' })

    renderWithQueryClient(<BackupStatusBanner />)

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent(/failing/i)
    expect(screen.getAllByRole('alert')).toHaveLength(1)
  })

  it('dismisses for this render and does not reappear until remounted', async () => {
    mockStatus({ state: 'STALE' })
    const user = userEvent.setup()

    const { container, unmount } = renderWithQueryClient(<BackupStatusBanner />)

    await screen.findByRole('alert')
    await user.click(screen.getByRole('button', { name: /close/i }))
    await waitFor(() => expect(container).toBeEmptyDOMElement())

    unmount()
    renderWithQueryClient(<BackupStatusBanner />)
    expect(await screen.findByRole('alert')).toBeInTheDocument()
  })
})
