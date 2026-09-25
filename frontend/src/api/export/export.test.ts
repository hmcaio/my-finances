import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { http, HttpResponse } from 'msw'
import { server } from '../../mocks/server'
import { exportBadRangeHandler } from '../../mocks/handlers/export'
import { ApiError } from '../core/apiError'
import { REVERSED_RANGE_MESSAGE, downloadExport, exportFileName } from './export'

describe('export API client', () => {
  let createObjectURL: ReturnType<typeof vi.fn>
  let revokeObjectURL: ReturnType<typeof vi.fn>
  let clicked: HTMLAnchorElement[]

  beforeEach(() => {
    createObjectURL = vi.fn(() => 'blob:export')
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

  it('saves the response as a dated zip file through a temporary object URL', async () => {
    await downloadExport()

    expect(createObjectURL).toHaveBeenCalledOnce()
    const blob = createObjectURL.mock.calls[0][0] as Blob
    expect(blob.size).toBe(4)
    expect(clicked).toHaveLength(1)
    expect(clicked[0].download).toBe(exportFileName())
    expect(clicked[0].download).toMatch(/^my-finances-export-\d{4}-\d{2}-\d{2}\.zip$/)
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:export')
  })

  it('sends only the filters that are set, and the request id header', async () => {
    let query: Record<string, string> = {}
    let requestId: string | null = null
    server.use(
      http.get('/api/export', ({ request }) => {
        query = Object.fromEntries(new URL(request.url).searchParams)
        requestId = request.headers.get('X-Request-Id')
        return HttpResponse.arrayBuffer(new ArrayBuffer(1), {
          headers: { 'Content-Type': 'application/zip' },
        })
      }),
    )

    await downloadExport({ dateFrom: '2026-01-01', accountId: 'acct-1', categoryId: '' })

    expect(query).toEqual({ dateFrom: '2026-01-01', accountId: 'acct-1' })
    expect(requestId).toMatch(/^[A-Za-z0-9-]{1,64}$/)
  })

  it('turns a 400 into the reversed-range message and saves nothing', async () => {
    server.use(exportBadRangeHandler)

    const failure = downloadExport({ dateFrom: '2026-03-01', dateTo: '2026-01-01' })

    await expect(failure).rejects.toEqual(new ApiError(400, REVERSED_RANGE_MESSAGE))
    expect(createObjectURL).not.toHaveBeenCalled()
  })

  it('surfaces other failures as an ApiError', async () => {
    server.use(http.get('/api/export', () => new HttpResponse(null, { status: 500 })))

    await expect(downloadExport()).rejects.toMatchObject({ status: 500 })
  })
})
