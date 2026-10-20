# F018 backup sidecar (ADR 0015). postgres:17.11-alpine - same major as the server (pg_dump/
# pg_restore must match it) and the exact same full patch tag docker-compose.prod.yml pins for
# the postgres service itself (root CLAUDE.md: a tool version is pinned in every file that names
# it; scripts/check-versions.sh fails if this drifts from docker-compose.prod.yml/
# TestcontainersConfiguration) - plus age for encryption, rclone for the optional off-site push,
# and bash/coreutils/findutils/jq for the scripts themselves: coreutils for GNU date's -d parsing
# and %G/%V ISO-week formatting that busybox's date doesn't support (prune.sh/decide.sh rely on
# it), findutils for GNU find's -printf (busybox's find applet - Alpine's default - doesn't
# support it at all; backup.sh's local-prune listing needs it to strip the directory prefix down
# to a bare filename), and gosu for entrypoint.sh's drop to BACKUP_UID:BACKUP_GID.
#
# gosu comes from Alpine's package, not from the one the postgres image bundles at
# /usr/local/bin/gosu: that upstream binary is built with Go 1.24.6 and
# carries fixable Go-stdlib CVEs the image scan (ADR 0031) fails on, with no newer
# postgres 17 tag to bump to. Alpine rebuilds its package with current Go. /usr/local/bin precedes
# /usr/bin in PATH, so the bundled binary is removed or it would keep winning.
FROM postgres:17.11-alpine

# The git SHA this image was built from, baked in at /etc/backup-build-id (ADR 0015's
# "pre-upgrade" trigger compares this to the value a previous run recorded in status.json).
ARG BUILD_ID=dev

RUN apk add --no-cache bash coreutils findutils jq age rclone gosu \
  && rm -f /usr/local/bin/gosu \
  && printf '%s' "$BUILD_ID" >/etc/backup-build-id

COPY scripts/backup.sh scripts/entrypoint.sh scripts/prune.sh scripts/decide.sh scripts/restore.sh scripts/backup-keygen /usr/local/bin/
RUN chmod +x /usr/local/bin/backup.sh /usr/local/bin/entrypoint.sh /usr/local/bin/prune.sh \
  /usr/local/bin/decide.sh /usr/local/bin/restore.sh /usr/local/bin/backup-keygen

WORKDIR /backups

# No fixed ENTRYPOINT: `docker compose up` runs the default CMD (the long-running sidecar loop),
# while `docker compose run --rm --no-deps backup restore.sh <file> --yes` (or backup-keygen)
# replaces CMD entirely and runs directly - same override idiom the base postgres image itself
# uses, but without its postgres-server-specific entrypoint logic, which this image has no use for.
ENTRYPOINT []
CMD ["entrypoint.sh"]
