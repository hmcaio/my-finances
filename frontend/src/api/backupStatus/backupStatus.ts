import { apiClient } from '../core/client'
import { unwrap } from '../core/apiError'
import type { components } from '../generated/schema'

export type BackupState = NonNullable<components['schemas']['BackupStatusResponse']['state']>

/**
 * The F018 backup sidecar's status, as returned by the read-only `GET /api/backup-status`
 * (ADR 0015). Every timestamp field is `null` when there is none yet (no marker, or no backup
 * has ever succeeded) - `lastError`/`imageTag`/`buildId`/`schemaVersion`/`targetType`/`remoteOk`
 * are likewise `null` whenever the backend has nothing to report for them.
 */
export interface BackupStatus {
  state: BackupState
  localOnly: boolean
  lastSuccessAt: string | null
  lastAttemptAt: string | null
  lastError: string | null
  imageTag: string | null
  buildId: string | null
  schemaVersion: string | null
  targetType: string | null
  remoteConfigured: boolean
  remoteOk: boolean | null
  count: number
}

/** Read-only; no `conflictMessage` needed (ADR 0015, F018 spec's Frontend section). */
export async function getBackupStatus(): Promise<BackupStatus> {
  return unwrap(apiClient.get<BackupStatus>('/backup-status'))
}
