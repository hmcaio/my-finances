#!/usr/bin/env bash
# decide.sh — the start-time/hourly catch-up decision (F018 spec "Sidecar/Scripts: entrypoint.sh",
# ADR 0015). Pure function, no filesystem access: the current build id (arg 1), the max-age
# threshold in hours (arg 2) and "now" (arg 3, UTC ISO 8601) in, plus the status marker's raw JSON
# text on stdin (empty string for "no marker yet"); prints exactly one of:
#
#   forced   the marker's buildId doesn't match arg 1 (including missing/blank) - a Flyway
#            migration on upgrade is the likeliest data-loss event, so this always backs up and
#            is tagged pre-upgrade, regardless of how fresh the last success was.
#   stale    marker missing, unparseable, or its buildId matches but lastSuccessAt is absent or
#            older than the max-age threshold - back up now, not tagged pre-upgrade.
#   fresh    marker's buildId matches and lastSuccessAt is within the threshold - nothing to do.
#
# entrypoint.sh runs backup.sh (tagging the result .pre-upgrade when the decision was "forced")
# whenever the decision is "forced" or "stale", and does nothing for "fresh".

set -euo pipefail

BUILD_ID="${1:-}"
MAX_AGE_HOURS="${2:-}"
NOW_ARG="${3:-}"
if [ -z "$BUILD_ID" ] || [ -z "$MAX_AGE_HOURS" ] || [ -z "$NOW_ARG" ]; then
  echo "usage: decide.sh <build-id> <max-age-hours> <now-iso8601> < marker-json" >&2
  exit 2
fi

MARKER_JSON="$(cat)"

if [ -z "$MARKER_JSON" ] || ! echo "$MARKER_JSON" | jq empty >/dev/null 2>&1; then
  echo "stale"
  exit 0
fi

MARKER_BUILD_ID=$(echo "$MARKER_JSON" | jq -r '.buildId // ""')
LAST_SUCCESS_AT=$(echo "$MARKER_JSON" | jq -r '.lastSuccessAt // ""')

if [ -z "$MARKER_BUILD_ID" ] || [ "$MARKER_BUILD_ID" != "$BUILD_ID" ]; then
  echo "forced"
  exit 0
fi

if [ -z "$LAST_SUCCESS_AT" ]; then
  echo "stale"
  exit 0
fi

if ! LAST_SUCCESS_EPOCH=$(date -u -d "$LAST_SUCCESS_AT" +%s 2>/dev/null); then
  # Unparseable timestamp is just as unknown as a missing one.
  echo "stale"
  exit 0
fi
NOW_EPOCH=$(date -u -d "$NOW_ARG" +%s)

AGE_SECONDS=$((NOW_EPOCH - LAST_SUCCESS_EPOCH))
MAX_AGE_SECONDS=$((MAX_AGE_HOURS * 3600))

if [ "$AGE_SECONDS" -gt "$MAX_AGE_SECONDS" ]; then
  echo "stale"
else
  echo "fresh"
fi
exit 0
