# F018 backup sidecar (ADR 0015, base amended in #204). Plain Alpine plus only what the scripts
# call, not the postgres server image: that image declared `VOLUME /var/lib/postgresql/data`
# (an anonymous volume per sidecar container that nothing used), carried a whole Postgres server
# the sidecar never runs, and bundled its own gosu with Go-stdlib CVEs (#177).
#
# Pins (root CLAUDE.md: a tool version is pinned in every file that names it; scripts/
# check-versions.sh fails on drift):
# - alpine:<full patch tag>: the same Alpine release as the other images' bases (postgres,
#   nginx and eclipse-temurin `-alpine`), so one Alpine CVE fix lands across all four.
# - POSTGRES_MAJOR: pg_dump/pg_restore must match the server's major, which is the one of the
#   postgres tag in docker-compose.prod.yml; check-versions.sh compares the two. Alpine ships
#   the client as `postgresql<major>-client` at the latest minor of that major, which is
#   compatible with any server of the same major (so it can run ahead of the server's patch tag).
#
# Packages: postgresql-client (pg_dump, pg_restore, psql), age (encryption, and age-keygen),
# rclone (optional off-site push), gosu (entrypoint.sh's drop to BACKUP_UID:BACKUP_GID, from
# Alpine, rebuilt with a current Go), and bash/coreutils/findutils/jq for the scripts: coreutils
# for GNU date's -d parsing and %G/%V ISO-week formatting that busybox's date doesn't support
# (prune.sh/decide.sh rely on it), findutils for GNU find's -printf (busybox's find, Alpine's
# default, doesn't support it at all; backup.sh's local-prune listing needs it to strip the
# directory prefix down to a bare filename). TLS roots for rclone come from the base image's
# ca-certificates-bundle.
FROM alpine:3.24.3

ARG POSTGRES_MAJOR=18

# The git SHA this image was built from, baked in at /etc/backup-build-id (ADR 0015's
# "pre-upgrade" trigger compares this to the value a previous run recorded in status.json).
ARG BUILD_ID=dev

RUN apk add --no-cache "postgresql${POSTGRES_MAJOR}-client" bash coreutils findutils jq age rclone gosu \
  && printf '%s' "$BUILD_ID" >/etc/backup-build-id

COPY scripts/backup.sh scripts/entrypoint.sh scripts/prune.sh scripts/decide.sh scripts/restore.sh scripts/backup-keygen /usr/local/bin/
RUN chmod +x /usr/local/bin/backup.sh /usr/local/bin/entrypoint.sh /usr/local/bin/prune.sh \
  /usr/local/bin/decide.sh /usr/local/bin/restore.sh /usr/local/bin/backup-keygen

WORKDIR /backups

# No fixed ENTRYPOINT: `docker compose up` runs the default CMD (the long-running sidecar loop),
# while `docker compose run --rm --no-deps backup restore.sh <file> --yes` (or backup-keygen)
# replaces CMD entirely and runs directly. The empty ENTRYPOINT keeps that override idiom the
# postgres base image used to provide, without its server-specific entrypoint logic.
ENTRYPOINT []
CMD ["entrypoint.sh"]
