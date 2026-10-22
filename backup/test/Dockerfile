# Runs the sidecar's bats unit tests (scripts/verify.sh backup builds this and runs `bats test` in
# it), so nobody needs bats-core on their machine. Built from the same pinned Alpine as the
# sidecar and with the same bash/coreutils/findutils/jq packages, so the tests exercise the scripts
# on the userland they ship on (prune.sh/decide.sh rely on GNU date and find, which busybox and BSD
# lack). The bats version is Alpine's; the tests use only core bats features. Never published, never
# part of the sidecar image.
#
# scripts/check-versions.sh requires this tag to equal backup/Dockerfile's, so one Alpine bump
# (Dependabot opens one PR per directory) cannot leave the tests on another release.
FROM alpine:3.24.3

RUN apk add --no-cache bats bash coreutils findutils jq

WORKDIR /work
