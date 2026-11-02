package com.chm.myfinances.infrastructure.backupstatus;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The rule that decides whether the folder holding the sidecar's marker is an in-memory mount
 * (issue #239): the nearest enclosing mount of the path is a {@code tmpfs} or {@code ramfs}. This
 * is the Java twin of {@code backup/scripts/storage.sh}, which also treats a folder on the
 * container's own {@code /} layer as volatile; the backend cannot tell that from a native run on a
 * host filesystem, so it flags only the in-memory kinds. Change the two together. The mountinfo
 * lines are the ones {@code backup/test/storage.bats} uses, captured from Docker Desktop (WSL2):
 * the prod sidecar whose bind mount came up as a tmpfs, a {@code --tmpfs}, a named volume, a host
 * bind.
 */
class VolatileMountTest {

  private static final String ROOT =
      "1442 1272 0:140 / / rw,relatime - overlay overlay rw,lowerdir=/l";
  private static final String DEV_TMPFS =
      "1444 1442 0:142 / /dev rw,nosuid - tmpfs tmpfs rw,size=65536k";

  private static boolean volatileAt(String path, String... mounts) {
    return VolatileMount.onVolatileFilesystem(
        path,
        java.util.stream.Stream.concat(List.of(ROOT, DEV_TMPFS).stream(), List.of(mounts).stream())
            .toList());
  }

  @Test
  void theProdSidecarsDockerDesktopTmpfsBindIsVolatile() {
    assertThat(
            volatileAt(
                "/backups",
                "1054 1045 0:34 /docker-desktop-bind-mounts/Ubuntu/a1b2c3d4e5f6 /backups rw,relatime - tmpfs none rw"))
        .isTrue();
  }

  @Test
  void aTmpfsAndARamfsAreVolatile() {
    assertThat(
            volatileAt(
                "/backups",
                "1451 1442 0:182 / /backups rw,nosuid,nodev,noexec,relatime - tmpfs tmpfs rw"))
        .isTrue();
    assertThat(volatileAt("/backups", "1451 1442 0:182 / /backups rw,relatime - ramfs ramfs rw"))
        .isTrue();
  }

  @Test
  void aNamedVolumeAHostBindAndTheWindowsDriveAreNot() {
    assertThat(
            volatileAt(
                "/backups",
                "1451 1442 8:48 /data/docker/volumes/v/_data /backups rw,relatime master:30 - ext4 /dev/sdd rw"))
        .isFalse();
    assertThat(
            volatileAt(
                "/backups",
                "1476 1467 8:80 /tmp/tmp.q6tEgd5OW5 /backups rw,relatime - ext4 /dev/sdf rw"))
        .isFalse();
    assertThat(
            volatileAt(
                "/backups",
                "1500 1442 0:200 / /backups rw,noatime - 9p C:\\134 rw,aname=drvfs;path=C:\\;uid=1000"))
        .isFalse();
  }

  @Test
  void aFolderInsideAMountTakesThatMountsFilesystem() {
    assertThat(
            volatileAt("/backups/archive/2026", "1451 1442 0:182 / /backups rw - tmpfs tmpfs rw"))
        .isTrue();
    assertThat(
            volatileAt("/backups/archive/2026", "1451 1442 8:48 /v /backups rw - ext4 /dev/sdd rw"))
        .isFalse();
  }

  @Test
  void theLongestMountPointWinsSoANestedTmpfsOnlyAffectsItsOwnFolder() {
    String[] mounts = {
      "1451 1442 8:48 /v /backups rw - ext4 /dev/sdd rw",
      "1452 1451 0:184 / /backups/cache rw - tmpfs tmpfs rw"
    };
    assertThat(volatileAt("/backups", mounts)).isFalse();
    assertThat(volatileAt("/backups/cache", mounts)).isTrue();
  }

  @Test
  void aSiblingThatSharesTheNamePrefixIsNotTheMount() {
    assertThat(volatileAt("/backups2", "1451 1442 0:182 / /backups rw - tmpfs tmpfs rw")).isFalse();
    assertThat(volatileAt("/backups", "1452 1442 0:184 / /backups2 rw - tmpfs tmpfs rw")).isFalse();
  }

  @Test
  void anEscapedSpaceInAMountPointIsMatched() {
    assertThat(volatileAt("/my backups", "1452 1442 0:184 / /my\\040backups rw - tmpfs tmpfs rw"))
        .isTrue();
  }

  @Test
  void aFolderOnTheRootLayerIsNotFlaggedHere() {
    assertThat(volatileAt("/backups")).isFalse();
  }

  @Test
  void aMountInfoWithOptionalFieldsAndBlankOrMalformedLinesIsParsed(@TempDir Path dir)
      throws IOException {
    Path mountInfo = dir.resolve("mountinfo");
    Files.writeString(
        mountInfo,
        "\nnot a mountinfo line\n1451 1442 0:182 / "
            + dir
            + " rw,relatime shared:5 master:1 - tmpfs tmpfs rw\n");

    assertThat(VolatileMount.holds(dir.resolve("status.json"), mountInfo)).isTrue();
  }

  @Test
  void anUnreadableMountInfoIsNotVolatile(@TempDir Path dir) {
    assertThat(VolatileMount.holds(dir.resolve("status.json"), dir.resolve("missing"))).isFalse();
  }
}
