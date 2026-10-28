#!/bin/sh
# Container entrypoint for the `full` compose profile (ADR 0018). Runs bootRun and, in the
# background, recompiles whenever a source file changes so Spring DevTools restarts the app.
# Polls instead of `./gradlew --continuous`: Gradle's file watcher relies on inotify, which never
# fires for changes made on a Windows host through a bind mount.
set -u
# Build output and Gradle's project cache live in a named volume outside the ./backend bind mount
# (docker-compose.yml), so Docker never creates root-owned mountpoints inside the host's backend/.
# GRADLE_BUILD_DIR (compose) is read by the init script; the project cache is passed per call.
GRADLE_ARGS="-I gradle/relocate-build-dir.init.gradle --project-cache-dir /gradle-out/project-cache --console=plain"
STAMP=/tmp/last-compile
touch "$STAMP"

(
  while true; do
    sleep 2
    if [ -n "$(find src build.gradle -type f -newer "$STAMP" 2>/dev/null | head -n 1)" ]; then
      touch "$STAMP"
      ./gradlew $GRADLE_ARGS classes -q || echo "[dev-run] compile failed, waiting for next change"
    fi
  done
) &

exec ./gradlew $GRADLE_ARGS bootRun
