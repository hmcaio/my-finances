import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { http, HttpResponse } from 'msw'
import { server } from '../../mocks/server'
import { reportsBadRequestHandler } from '../../mocks/handlers/reports'
import { downloadReports } from './reports'

describe('reports API client', () => {
  let createObjectURL: ReturnType<typeof vi.fn>
  let revokeObjectURL: ReturnType<typeof vi.fn>
  let clicked: HTMLAnchorElement[]

  beforeEach(() => {
    createObjectURL = vi.fn(() => 'blob:reports')
    revokeObjectURL = vi.fn()
    Object.assign(URL, { createObjectURL, revokeObjectURL })
    clicked = []
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (
      this: HTMLAnchorElement,
    ) {
      clicked.push(this)
    })
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('names a single csv report by its slug and the request id header is sent', async () => {
    let requestId: string | null = null
    server.use(
      http.post('/api/reports', ({ request }) => {
        requestId = request.headers.get('X-Request-Id')
        return HttpResponse.arrayBuffer(new ArrayBuffer(4), {
          headers: { 'Content-Type': 'text/csv' },
        })
      }),
    )

    await downloadReports(['ANNUAL_SUMMARY'])

    expect(createObjectURL).toHaveBeenCalledOnce()
    expect(clicked).toHaveLength(1)
    expect(clicked[0].download).toMatch(/^my-finances-annual-summary-\d{4}-\d{2}-\d{2}\.csv$/)
    expect(requestId).toMatch(/^[A-Za-z0-9-]{1,64}$/)
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:reports')
  })

  it('names a single pdf report with a pdf extension', async () => {
    server.use(
      http.post('/api/reports', () =>
        HttpResponse.arrayBuffer(new ArrayBuffer(4), {
          headers: { 'Content-Type': 'application/pdf' },
        }),
      ),
    )

    await downloadReports(['FII_PORTFOLIO'])

    expect(clicked[0].download).toMatch(/^my-finances-fii-portfolio-\d{4}-\d{2}-\d{2}\.pdf$/)
  })

  it('names a zip the same way regardless of how many types were requested', async () => {
    server.use(
      http.post('/api/reports', () =>
        HttpResponse.arrayBuffer(new ArrayBuffer(4), {
          headers: { 'Content-Type': 'application/zip' },
        }),
      ),
    )

    await downloadReports(['ANNUAL_SUMMARY', 'DIVIDEND_INCOME'])

    expect(clicked[0].download).toMatch(/^my-finances-reports-\d{4}-\d{2}-\d{2}\.zip$/)
  })

  it('sends the selected types and filter as the request body', async () => {
    let body: unknown
    server.use(
      http.post('/api/reports', async ({ request }) => {
        body = await request.clone().json()
        return HttpResponse.arrayBuffer(new ArrayBuffer(4), {
          headers: { 'Content-Type': 'text/csv' },
        })
      }),
    )

    await downloadReports(['ANNUAL_SUMMARY'], { month: '2026-06' })

    expect(body).toEqual({ types: ['ANNUAL_SUMMARY'], filter: { month: '2026-06' } })
  })

  it('surfaces a 400 as an ApiError and saves nothing', async () => {
    server.use(reportsBadRequestHandler)

    await expect(downloadReports([])).rejects.toMatchObject({ status: 400 })
    expect(createObjectURL).not.toHaveBeenCalled()
  })
})
