import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { API_KEY_ROOT } from '../core/queryClient'
import { getAuditLog, type AuditLogFilter } from './auditLog'

export const auditLogKeys = {
  all: [API_KEY_ROOT, 'auditLog'] as const,
  list: (filter: AuditLogFilter, page: number, size?: number) =>
    [...auditLogKeys.all, 'list', { filter, page, size }] as const,
}

/** One page of audit entries; the previous page stays on screen while the next one loads. */
export function useAuditLog(filter: AuditLogFilter = {}, page = 0, size?: number) {
  return useQuery({
    queryKey: auditLogKeys.list(filter, page, size),
    queryFn: () => getAuditLog(filter, page, size),
    placeholderData: keepPreviousData,
  })
}
