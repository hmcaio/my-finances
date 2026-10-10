package com.chm.myfinances.application.backupstatus;

/**
 * Derived backup health shown by {@code GET /api/backup-status} and the frontend banner (F018
 * spec's Backend section). {@code localOnly} is a separate boolean alongside this enum, not a fifth
 * value here - a stack can be simultaneously {@link #OK} (backups are succeeding) and local-only
 * (they exist on only this machine), and the frontend needs to tell those two warnings apart.
 */
public enum BackupState {
  /** Last attempt succeeded and the last success is within the staleness threshold. */
  OK,
  /** No attempt has ever succeeded, or the last success is older than the threshold. */
  STALE,
  /** The most recent attempt recorded a failure. Takes priority over {@link #STALE}. */
  FAILING,
  /** The marker is missing or could not be parsed. */
  UNKNOWN
}
