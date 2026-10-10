# F018 backup sidecar (ADR 0015). postgres:17-alpine so pg_dump/pg_restore are exactly the server
# major version; age for encryption; rclone for the optional off-site push; bash/coreutils/jq for
# the scripts themselves (coreutils specifically for GNU date's -d parsing and %G/%V ISO-week
# formatting that busybox's date doesn't support - prune.sh/decide.sh rely on it).
FROM postgres:17-alpine

# The git SHA this image was built from, baked in at /etc/backup-build-id (ADR 0015's
# "pre-upgrade" trigger compares this to the value a previous run recorded in status.json).
ARG BUILD_ID=dev

RUN apk add --no-cache bash coreutils jq age rclone \
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
