import { describe, expect, it } from 'vitest'
import { http, HttpResponse } from 'msw'
import { server } from '../mocks/server'
import { seedNetWorthTrend } from '../mocks/handlers/netWorth'
import { getNetWorth, getNetWorthTrend } from './netWorth'

describe('net worth API client', () => {
  it('passes asOf to the point endpoint', async () => {
    let sent: Record<string, string> = {}
    server.use(
      http.get('/api/net-worth', ({ request }) => {
        sent = Object.fromEntries(new URL(request.url).searchParams)
        return HttpResponse.json(seedNetWorthTrend[0])
      }),
    )

    await expect(getNetWorth('2026-06-30')).resolves.toEqual(seedNetWorthTrend[0])
    expect(sent).toEqual({ asOf: '2026-06-30' })
  })

  it('omits asOf when none is given', async () => {
    let query = ''
    server.use(
      http.get('/api/net-worth', ({ request }) => {
        query = new URL(request.url).search
        return HttpResponse.json(seedNetWorthTrend[0])
      }),
    )

    await getNetWorth()

    expect(query).toBe('')
  })

  it('returns the trend points', async () => {
    await expect(getNetWorthTrend()).resolves.toEqual(seedNetWorthTrend)
  })

  it('passes from, to and granularity as query params', async () => {
    let sent: Record<string, string> = {}
    server.use(
      http.get('/api/net-worth/trend', ({ request }) => {
        sent = Object.fromEntries(new URL(request.url).searchParams)
        return HttpResponse.json([])
      }),
    )

    await getNetWorthTrend({ from: '2026-01-01', to: '2026-08-31', granularity: 'MONTH' })

    expect(sent).toEqual({ from: '2026-01-01', to: '2026-08-31', granularity: 'MONTH' })
  })
})
