#!/usr/bin/env bats
# Tests for backup/scripts/decide.sh (F018 spec "Sidecar/Scripts: entrypoint.sh", ADR 0015's
# catch-up trigger and forced-pre-upgrade rule, plan.md Phase 1). decide.sh is a pure function: the
# current build id (arg 1), the max-age-in-hours threshold (arg 2) and "now" (arg 3, UTC ISO 8601)
# in, plus the status marker's JSON on stdin, print exactly one of "forced" / "stale" / "fresh" on
# stdout - no filesystem access, so the marker is always supplied as a string, never a path.
#
# "forced" wins whenever the marker's buildId doesn't match the id this sidecar was built with
# (including a missing/blank buildId) - a Flyway migration on upgrade is the likeliest data-loss
# event (ADR 0015), so a build-id change always re-backs-up and tags the result pre-upgrade,
# regardless of how fresh the last success was. A missing or unparseable marker can't be compared
# at all, so it falls back to "stale" (back up now) rather than "forced": it's simply unknown
# history, not evidence of an upgrade.

setup() {
  SCRIPT="$BATS_TEST_DIRNAME/../scripts/decide.sh"
  BUILD_ID="abc123"
  MAX_AGE_HOURS=20
  NOW="2026-03-15T12:00:00Z"
}

run_decide() {
  local marker=$1
  run bash "$SCRIPT" "$BUILD_ID" "$MAX_AGE_HOURS" "$NOW" <<<"$marker"
}

@test "a missing marker (empty stdin) means back up now (stale)" {
  run_decide ""
  [ "$status" -eq 0 ]
  [ "$output" = "stale" ]
}

@test "a corrupt marker (invalid JSON) means back up now (stale)" {
  run_decide "{not json"
  [ "$status" -eq 0 ]
  [ "$output" = "stale" ]
}

@test "a marker with a different buildId is forced, even if the last success was recent" {
  run_decide '{"buildId":"old-build","lastSuccessAt":"2026-03-15T11:00:00Z"}'
  [ "$status" -eq 0 ]
  [ "$output" = "forced" ]
}

@test "a marker with no buildId field at all is forced" {
  run_decide '{"lastSuccessAt":"2026-03-15T11:00:00Z"}'
  [ "$status" -eq 0 ]
  [ "$output" = "forced" ]
}

@test "same buildId, no lastSuccessAt yet, is stale" {
  run_decide '{"buildId":"abc123"}'
  [ "$status" -eq 0 ]
  [ "$output" = "stale" ]
}

@test "same buildId, last success older than the max-age threshold, is stale" {
  # 2026-03-14T12:00:00Z is 24h before "now"; threshold is 20h.
  run_decide '{"buildId":"abc123","lastSuccessAt":"2026-03-14T12:00:00Z"}'
  [ "$status" -eq 0 ]
  [ "$output" = "stale" ]
}

@test "same buildId, last success within the max-age threshold, is fresh" {
  # 2026-03-15T06:00:00Z is 6h before "now"; threshold is 20h.
  run_decide '{"buildId":"abc123","lastSuccessAt":"2026-03-15T06:00:00Z"}'
  [ "$status" -eq 0 ]
  [ "$output" = "fresh" ]
}

@test "last success exactly at the max-age boundary is still fresh ('older than', not 'at least')" {
  # 2026-03-14T16:00:00Z is exactly 20h before "now".
  run_decide '{"buildId":"abc123","lastSuccessAt":"2026-03-14T16:00:00Z"}'
  [ "$status" -eq 0 ]
  [ "$output" = "fresh" ]
}

@test "one second past the max-age boundary is stale" {
  run_decide '{"buildId":"abc123","lastSuccessAt":"2026-03-14T15:59:59Z"}'
  [ "$status" -eq 0 ]
  [ "$output" = "stale" ]
}

@test "a previous failure (lastError set) does not override the build-id/staleness decision" {
  # decide.sh only decides *whether* to run a backup, not whether the last one succeeded - that's
  # the backend's state derivation (BackupState), a separate concern.
  run_decide '{"buildId":"abc123","lastSuccessAt":"2026-03-15T06:00:00Z","lastError":"disk_full"}'
  [ "$status" -eq 0 ]
  [ "$output" = "fresh" ]
}

@test "missing arguments is a usage error" {
  run bash "$SCRIPT" "$BUILD_ID" "$MAX_AGE_HOURS"
  [ "$status" -ne 0 ]
}
