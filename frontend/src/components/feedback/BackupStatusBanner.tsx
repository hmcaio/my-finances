import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { TFunction } from 'i18next'
import { Alert } from '@mui/material'
import { useBackupStatus } from '../../api/backupStatus/backupStatusQueries'
import { useLocale, type Locale } from '../../hooks/locale/useLocale'
import { formatDate } from '../../i18n/format'

/**
 * The sidecar's `lastError` category for a backups folder that is an in-memory mount (issue #239,
 * ADR 0015): the one cause the banner names, because the generic text gives the user nothing to act
 * on. Every other category keeps the generic message.
 */
const VOLATILE_STORAGE_ERROR = 'storage_not_persistent'

/** `lastSuccessAt` is `null` before any backup has ever succeeded. */
function formatLastSuccess(
  lastSuccessAt: string | null,
  locale: Locale,
  t: TFunction<'common'>,
): string {
  if (lastSuccessAt === null) return t('backupStatus.never')
  return formatDate(lastSuccessAt, locale, { dateStyle: 'medium', timeStyle: 'short' })
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
  const { t } = useTranslation()
  const { locale } = useLocale()
  const { data: status } = useBackupStatus()
  const [dismissed, setDismissed] = useState(false)

  if (dismissed || !status) return null

  if (status.state === 'FAILING' && status.lastError === VOLATILE_STORAGE_ERROR) {
    return (
      <Alert severity="warning" onClose={() => setDismissed(true)}>
        {t('backupStatus.volatileStorage')}
      </Alert>
    )
  }

  if (status.state === 'STALE' || status.state === 'FAILING') {
    const verb = status.state === 'FAILING' ? t('backupStatus.failing') : t('backupStatus.stale')
    return (
      <Alert severity="warning" onClose={() => setDismissed(true)}>
        {t('backupStatus.message', {
          state: verb,
          lastSuccess: formatLastSuccess(status.lastSuccessAt, locale, t),
        })}
      </Alert>
    )
  }

  if (status.localOnly) {
    return (
      <Alert severity="info" onClose={() => setDismissed(true)}>
        {t('backupStatus.localOnly')}
      </Alert>
    )
  }

  return null
}
