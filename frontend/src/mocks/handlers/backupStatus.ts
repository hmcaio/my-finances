import { http, HttpResponse } from 'msw'
import type { BackupStatus } from '../../api/backupStatus/backupStatus'

/**
 * Default seed: no marker at all, same as a dev stack with no sidecar (F018 spec's Backend
 * section - `UNKNOWN`, which the banner renders as nothing). Tests that need `STALE`/`FAILING`/
 * `localOnly` override with `server.use(...)` and a full `BackupStatus` object of their own.
 */
export const seedBackupStatusUnknown: BackupStatus = {
  state: 'UNKNOWN',
  localOnly: false,
  lastSuccessAt: null,
  lastAttemptAt: null,
  lastError: null,
  imageTag: null,
  buildId: null,
  schemaVersion: null,
  targetType: null,
  remoteConfigured: false,
  remoteOk: null,
  count: 0,
}

export const backupStatusHandlers = [
  http.get('/api/backup-status', () => HttpResponse.json(seedBackupStatusUnknown)),
]
