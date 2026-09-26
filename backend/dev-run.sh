#!/bin/sh
# Container entrypoint for the `full` compose profile (ADR 0018). Runs bootRun and, in the
# background, recompiles whenever a source file changes so Spring DevTools restarts the app.
# Polls instead of `./gradlew --continuous`: Gradle's file watcher relies on inotify, which never
# fires for changes made on a Windows host through a bind mount.
set -u
STAMP=/tmp/last-compile
touch "$STAMP"

(
  while true; do
    sleep 2
    if [ -n "$(find src build.gradle -type f -newer "$STAMP" 2>/dev/null | head -n 1)" ]; then
      touch "$STAMP"
      ./gradlew classes --console=plain -q || echo "[dev-run] compile failed, waiting for next change"
    fi
  done
) &

exec ./gradlew bootRun --console=plain
