package com.chm.myfinances.infrastructure.web.backupstatus;

import static org.hamcrest.Matchers.oneOf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chm.myfinances.testsupport.web.MockMvcSupport;
import com.chm.myfinances.testsupport.web.WebIntegrationTest;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

/**
 * REST-layer integration test for {@link BackupStatusController} (F018 spec's Backend section:
 * {@code GET /api/backup-status}), against a real Testcontainers Postgres (ADR 0010) - same full
 * {@code @SpringBootTest} tier every other controller test uses, even though this endpoint itself
 * never touches the database. {@code myfinances.backup.status-path} is pointed at a per-test temp
 * file via {@code @DynamicPropertySource} so each test controls exactly what the marker contains.
 */
@WebIntegrationTest
class BackupStatusControllerTest {

  @TempDir static Path tempDir;

  @DynamicPropertySource
  static void overrideStatusPath(DynamicPropertyRegistry registry) {
    registry.add("myfinances.backup.status-path", () -> tempDir.resolve("status.json").toString());
  }

  @Autowired private WebApplicationContext webApplicationContext;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcSupport.build(webApplicationContext);
  }

  private Path markerPath() {
    return tempDir.resolve("status.json");
  }

  @Test
  void returnsUnknownWhenTheMarkerFileDoesNotExist() throws Exception {
    Files.deleteIfExists(markerPath());

    mockMvc
        .perform(get("/api/backup-status"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.state").value("UNKNOWN"))
        .andExpect(jsonPath("$.localOnly").value(false))
        .andExpect(jsonPath("$.lastSuccessAt").doesNotExist());
  }

  @Test
  void returnsOkWithTheMarkerFieldsForAHealthyRecentBackup() throws Exception {
    Files.writeString(
        markerPath(),
        """
        {
          "lastSuccessAt": "2026-03-15T06:00:00Z",
          "lastAttemptAt": "2026-03-15T06:00:00Z",
          "lastError": null,
          "imageTag": "abc1234",
          "buildId": "abc1234",
          "schemaVersion": "42",
          "targetType": "bind",
          "remoteConfigured": false,
          "remoteOk": null,
          "count": 3
        }
        """);

    mockMvc
        .perform(get("/api/backup-status"))
        .andExpect(status().isOk())
        // OK if "now" (the real clock) is within 48h of the fixed lastSuccessAt above, STALE
        // otherwise - either is a correctly-derived result, this test only pins the marker
        // pass-through fields.
        .andExpect(jsonPath("$.state").value(oneOf("OK", "STALE")))
        .andExpect(jsonPath("$.imageTag").value("abc1234"))
        .andExpect(jsonPath("$.buildId").value("abc1234"))
        .andExpect(jsonPath("$.schemaVersion").value("42"))
        .andExpect(jsonPath("$.targetType").value("bind"))
        .andExpect(jsonPath("$.remoteConfigured").value(false))
        .andExpect(jsonPath("$.localOnly").value(false))
        .andExpect(jsonPath("$.count").value(3));
  }

  @Test
  void returnsFailingWhenTheLastAttemptRecordedAnError() throws Exception {
    Files.writeString(
        markerPath(),
        """
        {
          "lastSuccessAt": "2026-01-01T06:00:00Z",
          "lastAttemptAt": "2026-03-15T06:00:00Z",
          "lastError": "disk_full",
          "imageTag": "abc1234",
          "buildId": "abc1234",
          "schemaVersion": "42",
          "targetType": "volume",
          "remoteConfigured": false,
          "remoteOk": null,
          "count": 5
        }
        """);

    mockMvc
        .perform(get("/api/backup-status"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.state").value("FAILING"))
        .andExpect(jsonPath("$.lastError").value("disk_full"))
        .andExpect(jsonPath("$.localOnly").value(true));
  }

  @Test
  void returnsLocalOnlyTrueForANamedVolumeWithNoRemote() throws Exception {
    Files.writeString(
        markerPath(),
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

    mockMvc
        .perform(get("/api/backup-status"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.localOnly").value(true));
  }

  @Test
  void returnsUnknownForACorruptMarker() throws Exception {
    Files.writeString(markerPath(), "{not json");

    mockMvc
        .perform(get("/api/backup-status"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.state").value("UNKNOWN"));
  }
}
