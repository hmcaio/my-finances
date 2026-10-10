import { useQuery } from '@tanstack/react-query'
import { API_KEY_ROOT } from '../core/queryClient'
import { getBackupStatus } from './backupStatus'

export const backupStatusKeys = {
  all: [API_KEY_ROOT, 'backupStatus'] as const,
}

/** The backup sidecar's current status, for {@link BackupStatusBanner}. */
export function useBackupStatus() {
  return useQuery({
    queryKey: backupStatusKeys.all,
    queryFn: getBackupStatus,
  })
}
