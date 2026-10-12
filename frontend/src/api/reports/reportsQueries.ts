import { useMutation } from '@tanstack/react-query'
import { downloadReports, type ReportFilter, type ReportType } from './reports'

/**
 * Report generation/download. A read-only side effect (it saves a file, same as F013's export),
 * so it opts out of the global invalidation that follows a successful write.
 */
export function useDownloadReports() {
  return useMutation({
    mutationFn: ({ types, filter }: { types: ReportType[]; filter: ReportFilter }) =>
      downloadReports(types, filter),
    meta: { skipInvalidate: true },
  })
}
