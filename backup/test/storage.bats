#!/usr/bin/env bats
# Tests for backup/scripts/storage.sh (issue #239, ADR 0015's amendment): does the folder the
# sidecar writes its backups to survive the container? It reads /proc/self/mountinfo (the path is
# BACKUP_MOUNTINFO here), finds the mount that contains the folder (the longest mount point that
# prefixes it) and calls it volatile when that mount is a tmpfs or ramfs, or when the container's
# own `/` holds the folder (nothing is mounted there: its writable layer). Exit 0 = acceptable
# (prints "ok <fstype>"), exit 1 = refuse (prints "volatile <fstype>").
#
# The fixtures below are real /proc/self/mountinfo lines, captured on Docker Desktop (WSL2):
# the prod sidecar whose bind mount came up as a tmpfs after a Docker Desktop restart (the bug), a
# `--tmpfs` mount, a named volume, a host bind and a container with nothing mounted at the folder.
# The 9p line is built from the shape of the same sidecar's /proc/mounts line once it was healthy.

setup() {
  SCRIPT="$BATS_TEST_DIRNAME/../scripts/storage.sh"
  export BACKUP_MOUNTINFO="$BATS_TEST_TMPDIR/mountinfo"
  unset BACKUP_RCLONE_REMOTE BACKUP_ALLOW_VOLATILE_STORAGE BACKUP_STORAGE_DIR
  # Mounts every container has: the root, and a tmpfs at /dev that must never match /backups.
  cat >"$BACKUP_MOUNTINFO" <<'EOF'
1442 1272 0:140 / / rw,relatime - overlay overlay rw,lowerdir=/l1:/l2,upperdir=/u/fs,workdir=/u/work
1443 1442 0:141 / /proc rw,nosuid,nodev,noexec,relatime - proc proc rw
1444 1442 0:142 / /dev rw,nosuid - tmpfs tmpfs rw,size=65536k,mode=755
1445 1444 0:143 / /dev/shm rw,nosuid,nodev,noexec,relatime - tmpfs shm rw,size=65536k
EOF
}

add_mount() { printf '%s\n' "$1" >>"$BACKUP_MOUNTINFO"; }

# --- the bug and its siblings: volatile -----------------------------------------------------------

@test "the prod sidecar whose bind mount came up as a Docker Desktop tmpfs is volatile" {
  add_mount '1054 1045 0:34 /docker-desktop-bind-mounts/Ubuntu/a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f90 /backups rw,relatime - tmpfs none rw'
  run bash "$SCRIPT" /backups
  [ "$status" -eq 1 ]
  [ "$output" = "volatile tmpfs" ]
}

@test "a --tmpfs mount is volatile" {
  add_mount '1451 1442 0:182 / /backups rw,nosuid,nodev,noexec,relatime - tmpfs tmpfs rw'
  run bash "$SCRIPT" /backups
  [ "$status" -eq 1 ]
  [ "$output" = "volatile tmpfs" ]
}

@test "a ramfs mount is volatile" {
  add_mount '1451 1442 0:182 / /backups rw,relatime - ramfs ramfs rw'
  run bash "$SCRIPT" /backups
  [ "$status" -eq 1 ]
  [ "$output" = "volatile ramfs" ]
}

@test "nothing mounted at the folder means the container's own layer: volatile" {
  run bash "$SCRIPT" /backups
  [ "$status" -eq 1 ]
  [ "$output" = "volatile overlay" ]
}

# --- real storage: acceptable ---------------------------------------------------------------------

@test "a named volume (ext4 in the Docker VM) is acceptable" {
  add_mount '1451 1442 8:48 /data/docker/volumes/my-finances-backups-prod/_data /backups rw,relatime master:30 - ext4 /dev/sdd rw'
  run bash "$SCRIPT" /backups
  [ "$status" -eq 0 ]
  [ "$output" = "ok ext4" ]
}

@test "a bind of a host directory is acceptable" {
  add_mount '1476 1467 8:80 /tmp/tmp.q6tEgd5OW5 /backups rw,relatime - ext4 /dev/sdf rw,discard,errors=remount-ro,data=ordered'
  run bash "$SCRIPT" /backups
  [ "$status" -eq 0 ]
  [ "$output" = "ok ext4" ]
}

@test "the Windows drive through 9p (a healthy WSL2 bind mount) is acceptable" {
  add_mount '1500 1442 0:200 / /backups rw,noatime - 9p C:\134 rw,aname=drvfs;path=C:\;uid=1000;gid=1000;symlinkroot=/mnt/,cache=0x5,access=client,msize=65536,trans=fd,rfd=6,wfd=6'
  run bash "$SCRIPT" /backups
  [ "$status" -eq 0 ]
  [ "$output" = "ok 9p" ]
}

# --- which mount contains the folder --------------------------------------------------------------

@test "a folder inside a real mount takes that mount's filesystem" {
  add_mount '1451 1442 8:48 /v /backups rw,relatime - ext4 /dev/sdd rw'
  run bash "$SCRIPT" /backups/archive/2026
  [ "$status" -eq 0 ]
  [ "$output" = "ok ext4" ]
}

@test "the trailing slash and .. are resolved before matching" {
  add_mount '1451 1442 8:48 /v /backups rw,relatime - ext4 /dev/sdd rw'
  add_mount '1452 1442 0:183 / /elsewhere rw - tmpfs tmpfs rw'
  run bash "$SCRIPT" /elsewhere/../backups/
  [ "$status" -eq 0 ]
  [ "$output" = "ok ext4" ]
}

@test "the longest mount point wins: a tmpfs nested under a real mount only affects its own folder" {
  add_mount '1451 1442 8:48 /v /backups rw,relatime - ext4 /dev/sdd rw'
  add_mount '1452 1451 0:184 / /backups/cache rw - tmpfs tmpfs rw'
  run bash "$SCRIPT" /backups
  [ "$status" -eq 0 ]
  run bash "$SCRIPT" /backups/cache
  [ "$status" -eq 1 ]
  [ "$output" = "volatile tmpfs" ]
}

@test "a folder that merely starts with a mount point's name is not inside it (/backups2 is not under /backups)" {
  add_mount '1451 1442 8:48 /v /backups rw,relatime - ext4 /dev/sdd rw'
  run bash "$SCRIPT" /backups2
  [ "$status" -eq 1 ]
  [ "$output" = "volatile overlay" ]
}

@test "a tmpfs whose name starts with the folder's name is not the folder's mount (/backups2 is not /backups)" {
  add_mount '1451 1442 8:48 /v /backups rw,relatime - ext4 /dev/sdd rw'
  add_mount '1452 1442 0:184 / /backups2 rw - tmpfs tmpfs rw'
  run bash "$SCRIPT" /backups
  [ "$status" -eq 0 ]
  [ "$output" = "ok ext4" ]
}

@test "a tmpfs at a sibling does not make an unmounted folder 'tmpfs': the root layer holds it" {
  add_mount '1452 1442 0:184 / /backups2 rw - tmpfs tmpfs rw'
  run bash "$SCRIPT" /backups
  [ "$status" -eq 1 ]
  [ "$output" = "volatile overlay" ]
}

@test "an escaped space in a mount point (\\040) is matched" {
  add_mount '1452 1442 0:184 / /my\040backups rw - tmpfs tmpfs rw'
  run bash "$SCRIPT" "/my backups"
  [ "$status" -eq 1 ]
  [ "$output" = "volatile tmpfs" ]
}

@test "the default folder is BACKUP_STORAGE_DIR, then /backups" {
  add_mount '1452 1442 0:184 / /data rw - tmpfs tmpfs rw'
  BACKUP_STORAGE_DIR=/data run bash "$SCRIPT"
  [ "$status" -eq 1 ]
  add_mount '1451 1442 8:48 /v /backups rw,relatime - ext4 /dev/sdd rw'
  run bash "$SCRIPT"
  [ "$status" -eq 0 ]
  [ "$output" = "ok ext4" ]
}

# --- exemptions and failing open ------------------------------------------------------------------

@test "a configured rclone remote holds the durable copy, so a volatile folder is acceptable" {
  add_mount '1451 1442 0:182 / /backups rw - tmpfs tmpfs rw'
  BACKUP_RCLONE_REMOTE="remote:bucket" run bash "$SCRIPT" /backups
  [ "$status" -eq 0 ]
  [ "$output" = "ok remote" ]
}

@test "BACKUP_ALLOW_VOLATILE_STORAGE=1 is acceptable (the tests' escape hatch)" {
  add_mount '1451 1442 0:182 / /backups rw - tmpfs tmpfs rw'
  BACKUP_ALLOW_VOLATILE_STORAGE=1 run bash "$SCRIPT" /backups
  [ "$status" -eq 0 ]
  [ "$output" = "ok override" ]
}

@test "any other value of BACKUP_ALLOW_VOLATILE_STORAGE does not exempt" {
  add_mount '1451 1442 0:182 / /backups rw - tmpfs tmpfs rw'
  BACKUP_ALLOW_VOLATILE_STORAGE=yes run bash "$SCRIPT" /backups
  [ "$status" -eq 1 ]
}

@test "an unreadable mountinfo fails open: no false alarm where nothing can be known" {
  BACKUP_MOUNTINFO="$BATS_TEST_TMPDIR/does-not-exist" run bash "$SCRIPT" /backups
  [ "$status" -eq 0 ]
  [ "$output" = "ok unknown" ]
}
