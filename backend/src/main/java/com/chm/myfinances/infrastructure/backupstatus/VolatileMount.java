package com.chm.myfinances.infrastructure.backupstatus;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * Is the folder holding the sidecar's marker an in-memory mount? After a Docker Desktop (or WSL)
 * restart a {@code BACKUP_DIR} bind mount can come up as a {@code tmpfs} because its host drive is
 * not mounted yet (issue #239, ADR 0015). When the backend started in the same race it reads that
 * same in-memory folder, so a sidecar that later remounts the real one is invisible to it until the
 * backend restarts too: the marker it shows is stale, and the banner should say why.
 *
 * <p>Finds the mount containing the path in {@code /proc/self/mountinfo} (the longest mount point
 * that prefixes it) and reports whether its filesystem is {@code tmpfs} or {@code ramfs}. This is
 * the Java twin of {@code backup/scripts/storage.sh}, which also calls a folder on the container's
 * own {@code /} layer volatile; the backend cannot tell that from a native run on a host
 * filesystem, so it flags only the in-memory kinds. Change the two together (the same real
 * mountinfo lines are in {@code backup/test/storage.bats} and {@code VolatileMountTest}). Anything
 * that cannot be read or parsed is "not volatile": a false alarm would be worse than the silence it
 * replaces.
 */
final class VolatileMount {

  private static final Set<String> IN_MEMORY = Set.of("tmpfs", "ramfs");

  private VolatileMount() {}

  /**
   * Whether the folder containing {@code file} sits on an in-memory mount per {@code mountInfo}.
   */
  static boolean holds(Path file, Path mountInfo) {
    try {
      Path dir = file.toAbsolutePath().normalize().getParent();
      if (dir == null) {
        return false;
      }
      return onVolatileFilesystem(realPathOrSelf(dir), Files.readAllLines(mountInfo));
    } catch (IOException | RuntimeException e) {
      return false;
    }
  }

  private static String realPathOrSelf(Path dir) {
    try {
      return dir.toRealPath().toString();
    } catch (IOException e) {
      return dir.toString();
    }
  }

  /** The pure rule, over the lines of a mountinfo file. */
  static boolean onVolatileFilesystem(String absolutePath, List<String> mountInfoLines) {
    String bestMount = null;
    String bestFs = null;
    for (String line : mountInfoLines) {
      // "id parent major:minor root mountpoint options [optional fields] - fstype source ..."
      int separator = line.indexOf(" - ");
      if (separator < 0) {
        continue;
      }
      String[] fields = line.substring(0, separator).trim().split("\\s+");
      if (fields.length < 5) {
        continue;
      }
      String mount = unescape(fields[4]);
      String fs = line.substring(separator + 3).trim().split("\\s+", 2)[0];
      boolean contains =
          mount.equals("/") || absolutePath.equals(mount) || absolutePath.startsWith(mount + "/");
      // >= like storage.sh: a later line of the same length (an overmount) wins.
      if (contains && (bestMount == null || mount.length() >= bestMount.length())) {
        bestMount = mount;
        bestFs = fs;
      }
    }
    return bestFs != null && IN_MEMORY.contains(bestFs);
  }

  /**
   * mountinfo escapes a space, tab, newline and backslash in a mount point as \040 \011 \012 \134.
   */
  private static String unescape(String mount) {
    return mount
        .replace("\\040", " ")
        .replace("\\011", "\t")
        .replace("\\012", "\n")
        .replace("\\134", "\\");
  }
}
