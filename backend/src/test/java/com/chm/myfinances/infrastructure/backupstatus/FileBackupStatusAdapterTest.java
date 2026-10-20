package com.chm.myfinances.infrastructure.backupstatus;

import static org.assertj.core.api.Assertions.assertThat;

import com.chm.myfinances.application.backupstatus.BackupStatus;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for {@link FileBackupStatusAdapter}: missing, malformed and valid marker file cases
 * (plan.md Phase 4 - "adapter tests for a missing, malformed and valid marker"). A plain JUnit test
 * with no Spring context and no Testcontainers: the adapter touches only the local filesystem,
 * never a database, so it belongs in the fast tier (ADR 0013) like any other no-DB-dependency unit
 * test rather than the Testcontainers-backed persistence tier that name otherwise implies for
 * "infrastructure".
 */
class FileBackupStatusAdapterTest {

  @TempDir Path tempDir;

  private FileBackupStatusAdapter adapterFor(String path) {
    return new FileBackupStatusAdapter(path);
  }

  @Test
  void returnsEmptyWhenTheMarkerFileDoesNotExist() {
    FileBackupStatusAdapter adapter = adapterFor(tempDir.resolve("status.json").toString());

    assertThat(adapter.read()).isEmpty();
  }

  @Test
  void returnsEmptyWhenTheMarkerIsNotValidJson() throws IOException {
    Path marker = tempDir.resolve("status.json");
    Files.writeString(marker, "{not json at all");

    assertThat(adapterFor(marker.toString()).read()).isEmpty();
  }

  @Test
  void returnsEmptyWhenTheMarkerIsAnEmptyFile() throws IOException {
    Path marker = tempDir.resolve("status.json");
    Files.writeString(marker, "");

    assertThat(adapterFor(marker.toString()).read()).isEmpty();
  }

  @Test
  void parsesAFullyPopulatedValidMarker() throws IOException {
    Path marker = tempDir.resolve("status.json");
    Files.writeString(
        marker,
        """
        {
          "lastSuccessAt": "2026-03-15T06:00:00Z",
          "lastAttemptAt": "2026-03-15T06:00:00Z",
          "lastError": null,
          "imageTag": "abc1234",
          "buildId": "abc1234",
          "schemaVersion": "42",
          "targetType": "volume",
          "remoteConfigured": false,
          "remoteOk": null,
          "count": 3
        }
        """);

    Optional<BackupStatus> result = adapterFor(marker.toString()).read();

    assertThat(result).isPresent();
    BackupStatus status = result.get();
    assertThat(status.lastSuccessAt()).isEqualTo(Instant.parse("2026-03-15T06:00:00Z"));
    assertThat(status.lastAttemptAt()).isEqualTo(Instant.parse("2026-03-15T06:00:00Z"));
    assertThat(status.lastError()).isNull();
    assertThat(status.imageTag()).isEqualTo("abc1234");
    assertThat(status.buildId()).isEqualTo("abc1234");
    assertThat(status.schemaVersion()).isEqualTo("42");
    assertThat(status.targetType()).isEqualTo("volume");
    assertThat(status.remoteConfigured()).isFalse();
    assertThat(status.remoteOk()).isNull();
    assertThat(status.count()).isEqualTo(3);
  }

  @Test
  void parsesAFailedAttemptMarkerWithANullLastSuccessAt() throws IOException {
    Path marker = tempDir.resolve("status.json");
    Files.writeString(
        marker,
        """
        {
          "lastSuccessAt": null,
          "lastAttemptAt": "2026-03-15T06:00:00Z",
          "lastError": "missing_recipient",
          "imageTag": null,
          "buildId": null,
          "schemaVersion": null,
          "targetType": null,
          "remoteConfigured": false,
          "remoteOk": null,
          "count": 0
        }
        """);

    Optional<BackupStatus> result = adapterFor(marker.toString()).read();

    assertThat(result).isPresent();
    assertThat(result.get().lastSuccessAt()).isNull();
    assertThat(result.get().lastError()).isEqualTo("missing_recipient");
  }

  @Test
  void returnsEmptyWhenTheConfiguredPathIsBlank() {
    assertThat(adapterFor("").read()).isEmpty();
    assertThat(adapterFor("   ").read()).isEmpty();
  }
}
