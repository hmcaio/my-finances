import { useState } from 'react'
import { Alert } from '@mui/material'
import { useBackupStatus } from '../../api/backupStatus/backupStatusQueries'

/** `lastSuccessAt` is `null` before any backup has ever succeeded. */
function formatLastSuccess(lastSuccessAt: string | null): string {
  if (lastSuccessAt === null) return 'never'
  return new Date(lastSuccessAt).toLocaleString()
}

/**
 * App-shell banner for the F018 backup sidecar's status (spec's Frontend section). Mounted once
 * outside the route tree (and outside the F011 onboarding gate) so it shows regardless of which
 * page is open or whether onboarding has happened yet.
 *
 * `STALE`/`FAILING` win over `localOnly`: a stack can be simultaneously `OK` and local-only (an
 * info-level notice), but a stack that is actively failing or stale needs the one warning, not
 * two banners. `OK` (not local-only) and `UNKNOWN` (no marker - a dev stack, or a prod stack
 * whose sidecar hasn't written its first marker yet) render nothing.
 *
 * Dismissal is per-session only (plan.md Phase 5): this is a plain `useState`, not
 * `sessionStorage` - the banner lives in the app shell for the lifetime of the page, so a React
 * remount (an actual browser reload) is exactly "next load", and a route change within the SPA
 * never remounts it, so a dismissal survives navigation without any persistence layer at all.
 */
export function BackupStatusBanner() {
  const { data: status } = useBackupStatus()
  const [dismissed, setDismissed] = useState(false)

  if (dismissed || !status) return null

  if (status.state === 'STALE' || status.state === 'FAILING') {
    const verb = status.state === 'FAILING' ? 'failing' : 'stale'
    return (
      <Alert severity="warning" onClose={() => setDismissed(true)}>
        Backups are {verb} - last success: {formatLastSuccess(status.lastSuccessAt)}.
      </Alert>
    )
  }

  if (status.localOnly) {
    return (
      <Alert severity="info" onClose={() => setDismissed(true)}>
        Backups exist only on this machine - set BACKUP_DIR to a synced folder or configure rclone
        for an off-machine copy.
      </Alert>
    )
  }

  return null
}
