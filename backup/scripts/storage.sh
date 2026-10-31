#!/usr/bin/env bash
# storage.sh — does the folder the sidecar keeps its backups in survive the container? (issue
# #239, ADR 0015.) After a Docker Desktop restart a bind mount can come up as an in-memory
# directory when its host drive is not mounted yet: the sidecar then "backs up" into memory,
# reports success, and loses every file when the container is recreated.
#
#   storage.sh [dir]        dir defaults to $BACKUP_STORAGE_DIR, then /backups
#
# Prints one line and exits 0 ("ok <why>": acceptable) or 1 ("volatile <fstype>": refuse). It
# finds the mount that contains dir (the longest mount point that prefixes it) in
# /proc/self/mountinfo ($BACKUP_MOUNTINFO overrides the path, for the tests) and calls it volatile
# when its filesystem is tmpfs or ramfs, or when it is the container's own `/` (nothing is mounted
# at dir: the folder sits in the container's writable layer). Named volumes and real binds
# (ext4, 9p, virtiofs, nfs, ...) are acceptable. Acceptable without looking:
#   - BACKUP_RCLONE_REMOTE is set: the remote holds the durable copy ("ok remote");
#   - BACKUP_ALLOW_VOLATILE_STORAGE=1: for tests whose host /tmp is itself a tmpfs ("ok override").
# Where nothing can be known (no readable mountinfo) it fails open ("ok unknown"): a false alarm
# would be worse than the silence it replaces. The output carries a filesystem type, never a path
# (ADR 0011).
set -uo pipefail

DIR=${1:-${BACKUP_STORAGE_DIR:-/backups}}
MOUNTINFO=${BACKUP_MOUNTINFO:-/proc/self/mountinfo}

if [ -n "${BACKUP_RCLONE_REMOTE:-}" ]; then echo "ok remote"; exit 0; fi
if [ "${BACKUP_ALLOW_VOLATILE_STORAGE:-}" = "1" ]; then echo "ok override"; exit 0; fi
if [ ! -r "$MOUNTINFO" ]; then echo "ok unknown"; exit 0; fi

# readlink -m resolves .., symlinks and a trailing slash without requiring dir to exist.
real=$(readlink -m -- "$DIR" 2>/dev/null) || real=$DIR

# mountinfo escapes a space, tab, newline and backslash in a mount point as \040 \011 \012 \134.
unescape() {
  local s=$1
  s=${s//\\040/ }
  s=${s//\\011/$'\t'}
  s=${s//\\012/$'\n'}
  s=${s//\\134/\\}
  printf '%s' "$s"
}

best_mount="" best_fs=""
while IFS= read -r line; do
  # "id parent major:minor root mountpoint options [optional fields] - fstype source superoptions"
  case $line in *" - "*) ;; *) continue ;; esac
  pre=${line%% - *}
  post=${line#* - }
  read -r -a fields <<<"$pre"
  [ "${#fields[@]}" -ge 5 ] || continue
  mount=$(unescape "${fields[4]}")
  fs=${post%% *}
  if [ "$mount" = "/" ] || [ "$real" = "$mount" ] || [[ $real == "$mount"/* ]]; then
    if [ "${#mount}" -ge "${#best_mount}" ]; then best_mount=$mount best_fs=$fs; fi
  fi
done <"$MOUNTINFO"

if [ -z "$best_fs" ]; then echo "ok unknown"; exit 0; fi

case $best_fs in
tmpfs | ramfs) echo "volatile $best_fs"; exit 1 ;;
esac
if [ "$best_mount" = "/" ]; then echo "volatile $best_fs"; exit 1; fi
echo "ok $best_fs"
exit 0
