import { useMutation } from '@tanstack/react-query'
import { downloadExport } from './export'

/**
 * The export download. A read-only side effect (it saves a file), so it opts out of the global
 * invalidation that follows a successful write.
 */
export function useDownloadExport() {
  return useMutation({ mutationFn: downloadExport, meta: { skipInvalidate: true } })
}
