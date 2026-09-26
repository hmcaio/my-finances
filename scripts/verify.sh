#!/usr/bin/env bash
# The one definition of "the checks" (issue #73): CI's jobs call this script, and so do you, so a
# green local run means a green CI run. Usage:
#
#   scripts/verify.sh [stage...]     stages: versions backend frontend e2e   (default: versions backend frontend)
#
#   scripts/verify.sh                everything CI's unit-test jobs run
#   scripts/verify.sh frontend       just the frontend checks
#   scripts/verify.sh e2e            Playwright layout checks (needs `npx playwright install chromium` once)
#
# Every stage runs even if an earlier one fails (like Gradle's --continue), and the exit code is
# non-zero if any failed. Backend integration tests need Docker running (Testcontainers, ADR 0010).
# Dependencies are not installed here: run `npm ci`/`npm install` in frontend/ first.
set -u
cd "$(dirname "$0")/.."

failed=()

# One labelled step. Under GitHub Actions its output is folded into a collapsible group.
run() {
  local label=$1; shift
  [ -n "${GITHUB_ACTIONS:-}" ] && echo "::group::$label" || echo "==> $label"
  "$@"
  local rc=$?
  [ -n "${GITHUB_ACTIONS:-}" ] && echo "::endgroup::"
  if [ $rc -ne 0 ]; then
    echo "FAILED: $label" >&2
    failed+=("$label")
  fi
}

in_dir() { local dir=$1; shift; (cd "$dir" && "$@"); }

stage_versions() {
  run "versions: pinned tool versions agree" bash scripts/check-versions.sh
}

stage_backend() {
  # `test` (fast tier) and `integrationTest` (Spring + Testcontainers) both run and --continue keeps
  # the second going when the first fails. jacocoTestReport is in the same invocation because it
  # depends on both (report only, no thresholds). --no-daemon on CI: nothing reuses the daemon.
  run "backend: spotlessCheck + tests + coverage report" \
    in_dir backend ./gradlew spotlessCheck test integrationTest jacocoTestReport --continue ${CI:+--no-daemon}
}

stage_frontend() {
  if [ ! -d frontend/node_modules ]; then
    echo "frontend/node_modules missing: run 'npm ci' in frontend/ first" >&2
    failed+=("frontend: dependencies not installed")
    return
  fi
  run "frontend: lint" in_dir frontend npm run lint
  run "frontend: format:check" in_dir frontend npm run format:check
  # tsc -b: Vitest and ESLint don't type-check, so a broken type (e.g. a duplicate operation id in
  # the generated API schema) would otherwise only fail in the Docker image build.
  run "frontend: type-check and build" in_dir frontend npm run build
  # Same pass/fail as `npm test`, and the coverage report is informational (no thresholds).
  run "frontend: tests with coverage" in_dir frontend npm run test:coverage
}

stage_e2e() {
  run "frontend: e2e (Playwright)" in_dir frontend npm run e2e
}

stages=("$@")
[ ${#stages[@]} -eq 0 ] && stages=(versions backend frontend)

for stage in "${stages[@]}"; do
  case $stage in
    versions|backend|frontend|e2e) "stage_$stage" ;;
    *) echo "unknown stage '$stage' (versions backend frontend e2e)" >&2; exit 2 ;;
  esac
done

if [ ${#failed[@]} -ne 0 ]; then
  echo >&2
  echo "verify: ${#failed[@]} step(s) failed:" >&2
  printf '  - %s\n' "${failed[@]}" >&2
  exit 1
fi
echo "verify: all steps passed"
