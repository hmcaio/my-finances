import { apiClient } from '../core/client'
import { ApiError, unwrap } from '../core/apiError'
import { today } from '../../utils/localDate'

/**
 * Optional filters for the data export (F013, PRD S6.9). Each only affects the files that have
 * that dimension on the backend; reference files (categories, accounts, ...) are always complete.
 * Dates are `YYYY-MM-DD`, inclusive.
 */
export interface ExportFilter {
  dateFrom?: string
  dateTo?: string
  accountId?: string
  categoryId?: string
}

export const REVERSED_RANGE_MESSAGE = 'The "From" date must not be after the "To" date.'

/** The saved file's name: the backend names it the same way in `Content-Disposition`. */
export function exportFileName(): string {
  return `my-finances-export-${today()}.zip`
}

/**
 * Downloads the export ZIP. Goes through the shared Axios client (so `X-Request-Id` is sent and
 * the request is logged like every other one) with an `arraybuffer` response, then wraps them in a Blob and hands them to the
 * browser as a native file download via a temporary object URL - no JSON parsing involved.
 * Empty filter values are dropped so they are not sent as blank query params.
 */
export async function downloadExport(filter: ExportFilter = {}): Promise<void> {
  const params = Object.fromEntries(Object.entries(filter).filter(([, value]) => Boolean(value)))
  let bytes: ArrayBuffer
  try {
    bytes = await unwrap(
      apiClient.get<ArrayBuffer>('/export', { params, responseType: 'arraybuffer' }),
    )
  } catch (err) {
    if (err instanceof ApiError && err.status === 400) {
      // Error bodies arrive as raw bytes here, so `unwrap` can't read a message from them; the only
      // 400 the backend sends for this endpoint is a reversed date range.
      throw new ApiError(400, REVERSED_RANGE_MESSAGE)
    }
    throw err
  }
  saveBlob(new Blob([bytes], { type: 'application/zip' }), exportFileName())
}

function saveBlob(blob: Blob, fileName: string): void {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  document.body.appendChild(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(url)
}
