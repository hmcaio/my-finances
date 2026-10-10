package com.chm.myfinances.application.backupstatus;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * Pure-function tests for {@link BackupStateDeriver} (F018 spec's Backend section: {@code OK}/
 * {@code STALE}/{@code FAILING}/{@code UNKNOWN} plus {@code localOnly}), written before the
 * implementation (plan.md Phase 4). No Spring context, no file I/O - {@link
 * FileBackupStatusAdapterTest} covers reading the marker off disk separately.
 */
class BackupStateDeriverTest {

  private static final Instant NOW = Instant.parse("2026-03-15T12:00:00Z");

  private static BackupStatus statusOf(Instant lastSuccessAt, String lastError) {
    return new BackupStatus(
        lastSuccessAt, NOW, lastError, "sha-1", "build-1", "42", "volume", false, null, 1);
  }

  @Test
  void missingMarkerIsUnknown() {
    assertThat(BackupStateDeriver.deriveState(null, NOW)).isEqualTo(BackupState.UNKNOWN);
  }

  @Test
  void aRecordedFailureIsFailingEvenWithARecentLastSuccess() {
    BackupStatus status = statusOf(NOW.minusSeconds(60), "disk_full");
    assertThat(BackupStateDeriver.deriveState(status, NOW)).isEqualTo(BackupState.FAILING);
  }

  @Test
  void noLastSuccessAtAllIsStale() {
    BackupStatus status = statusOf(null, null);
    assertThat(BackupStateDeriver.deriveState(status, NOW)).isEqualTo(BackupState.STALE);
  }

  @Test
  void lastSuccessOlderThan48HoursIsStale() {
    BackupStatus status = statusOf(NOW.minus(java.time.Duration.ofHours(49)), null);
    assertThat(BackupStateDeriver.deriveState(status, NOW)).isEqualTo(BackupState.STALE);
  }

  @Test
  void lastSuccessExactlyAt48HoursIsStillOk() {
    BackupStatus status = statusOf(NOW.minus(java.time.Duration.ofHours(48)), null);
    assertThat(BackupStateDeriver.deriveState(status, NOW)).isEqualTo(BackupState.OK);
  }

  @Test
  void lastSuccessOneSecondPast48HoursIsStale() {
    BackupStatus status = statusOf(NOW.minus(java.time.Duration.ofHours(48)).minusSeconds(1), null);
    assertThat(BackupStateDeriver.deriveState(status, NOW)).isEqualTo(BackupState.STALE);
  }

  @Test
  void aRecentLastSuccessWithNoErrorIsOk() {
    BackupStatus status = statusOf(NOW.minusSeconds(3600), null);
    assertThat(BackupStateDeriver.deriveState(status, NOW)).isEqualTo(BackupState.OK);
  }

  @Test
  void aBlankLastErrorDoesNotCountAsAFailure() {
    BackupStatus status = statusOf(NOW.minusSeconds(60), "  ");
    assertThat(BackupStateDeriver.deriveState(status, NOW)).isEqualTo(BackupState.OK);
  }

  @Test
  void localOnlyIsFalseWhenTheMarkerIsMissing() {
    assertThat(BackupStateDeriver.isLocalOnly(null)).isFalse();
  }

  @Test
  void localOnlyIsTrueForANamedVolumeWithNoRemote() {
    BackupStatus status =
        new BackupStatus(NOW, NOW, null, "sha-1", "build-1", "42", "volume", false, null, 1);
    assertThat(BackupStateDeriver.isLocalOnly(status)).isTrue();
  }

  @Test
  void localOnlyIsFalseForABindMountEvenWithNoRemote() {
    BackupStatus status =
        new BackupStatus(NOW, NOW, null, "sha-1", "build-1", "42", "bind", false, null, 1);
    assertThat(BackupStateDeriver.isLocalOnly(status)).isFalse();
  }

  @Test
  void localOnlyIsFalseForANamedVolumeWithARemoteConfigured() {
    BackupStatus status =
        new BackupStatus(NOW, NOW, null, "sha-1", "build-1", "42", "volume", true, true, 1);
    assertThat(BackupStateDeriver.isLocalOnly(status)).isFalse();
  }
}
