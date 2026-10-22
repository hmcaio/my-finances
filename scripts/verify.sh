#!/usr/bin/env bash
# The one definition of "the checks" (issue #73): CI's jobs call this script, and so do you, so a
# green local run means a green CI run. Usage:
#
#   scripts/verify.sh [stage...]     stages: versions backend frontend e2e backup scan smoke   (default: versions backend frontend)
#
#   scripts/verify.sh                everything CI's unit-test jobs run
#   scripts/verify.sh frontend       just the frontend checks
#   scripts/verify.sh e2e            Playwright layout checks (needs `npx playwright install chromium` once)
#   E2E_SHARD=1/3 scripts/verify.sh e2e
#                                    only that third of the Playwright tests (CI runs the three shards in parallel)
#   scripts/verify.sh backup         F018 backup sidecar: bats unit tests + the Docker e2e test
#   scripts/verify.sh scan           builds the three images and scans them for fixable HIGH/CRITICAL
#                                    vulnerabilities (Trivy; not in the default run: it builds images)
#   SCAN_IMAGES="ref ..." scripts/verify.sh scan
#                                    scans those images instead (pulled if not local), without building;
#                                    this is how CI's build-and-push job gates publishing
#   scripts/verify.sh smoke          builds the three images and starts docker-compose.prod.yml against
#                                    them behind a throwaway login (scripts/smoke-prod.sh): healthy
#                                    services, loopback ports, login, nginx header/cache matrix, backup
#                                    status, clean backend log (not in the default run: it builds images).
#                                    Refuses to run beside a real prod stack (hardcoded container names)
#   SMOKE_BACKEND_IMAGE=ref SMOKE_FRONTEND_IMAGE=ref SMOKE_BACKUP_IMAGE=ref scripts/verify.sh smoke
#                                    smoke-tests those images instead, without building (CI's
#                                    build-and-push job, before it pushes)
#
# Every stage runs even if an earlier one fails (like Gradle's --continue), and the exit code is
# non-zero if any failed. Backend integration tests need Docker running (Testcontainers, ADR 0010).
# `backup` needs Docker running and `bats` on PATH (bats-core; not installed by this script -
# see docs/conventions/tooling.md for how CI and local dev get it). Dependencies are not installed here: run
# `npm ci`/`npm install` in frontend/ first.
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
  # E2E_SHARD=i/n runs one slice (Playwright --shard); unset runs everything. Unquoted on purpose:
  # empty expands to no argument.
  run "frontend: e2e (Playwright)" in_dir frontend npm run e2e -- ${E2E_SHARD:+--shard="$E2E_SHARD"}
}

stage_backup() {
  run "backup: bats unit tests" bats backup/test
  run "backup: Docker end-to-end test" bash backup/test/e2e.sh
}

# Image vulnerability scan (docs/conventions/tooling.md). Trivy runs from the pinned image named in
# scripts/trivy/Dockerfile against a `docker save` tarball, so no Docker socket is mounted into it
# and the same command works on Linux runners and Windows/macOS Docker Desktop. Fails (exit 1) on a
# fixable HIGH or CRITICAL finding in OS packages or app libraries; unfixed ones are not blockers.
# .trivyignore is the reviewed allowlist.
scan_image() {
  local image=$1 trivy tmp src rc
  trivy=$(awk '$1 == "FROM" { print $2; exit }' scripts/trivy/Dockerfile)
  if [ -z "$trivy" ]; then echo "no FROM line in scripts/trivy/Dockerfile" >&2; return 1; fi
  docker image inspect "$image" >/dev/null 2>&1 || docker pull "$image" || return 1
  tmp=$(mktemp -d) || return 1
  cp .trivyignore "$tmp/.trivyignore"
  # Git Bash on Windows: docker.exe needs a Windows path for both `save -o` and the bind mount, and
  # the caller's MSYS_NO_PATHCONV may be set or not, so hand it a path that is already native (MSYS
  # leaves those alone either way) instead of relying on its /tmp conversion. The container-side
  # paths are protected from mangling by MSYS_NO_PATHCONV=1 on the run.
  src=$tmp
  if command -v cygpath >/dev/null 2>&1; then src=$(cygpath -w "$tmp"); fi
  docker save -o "$src/image.tar" "$image" || { rm -rf "$tmp"; return 1; }
  MSYS_NO_PATHCONV=1 docker run --rm \
    -v "$src:/scan:ro" -v my-finances-trivy-cache:/root/.cache/trivy \
    "$trivy" image --input /scan/image.tar --ignorefile /scan/.trivyignore \
    --scanners vuln --pkg-types os,library --severity HIGH,CRITICAL --ignore-unfixed \
    --exit-code 1 --no-progress
  rc=$?
  rm -rf "$tmp"
  return $rc
}

stage_scan() {
  local images=() image
  read -ra images <<< "${SCAN_IMAGES:-}"
  if [ ${#images[@]} -eq 0 ]; then
    # No images given: build the three exactly as CI's build-and-push job does (the backup image
    # without BUILD_ID, which only labels it).
    for image in backend frontend backup; do
      run "scan: build $image image" docker build -t "my-finances-$image:scan" "$image"
      images+=("my-finances-$image:scan")
    done
  fi
  for image in "${images[@]}"; do
    run "scan: $image" scan_image "$image"
  done
}

# Production smoke test (scripts/smoke-prod.sh): the prod compose stack against locally built images.
# With no SMOKE_*_IMAGE given the three images are built as CI's build-and-push job builds them.
stage_smoke() {
  local image
  if [ -z "${SMOKE_BACKEND_IMAGE:-}${SMOKE_FRONTEND_IMAGE:-}${SMOKE_BACKUP_IMAGE:-}" ]; then
    for image in backend frontend backup; do
      run "smoke: build $image image" docker build -t "my-finances-$image:smoke" "$image"
    done
  elif [ -z "${SMOKE_BACKEND_IMAGE:-}" ] || [ -z "${SMOKE_FRONTEND_IMAGE:-}" ] || [ -z "${SMOKE_BACKUP_IMAGE:-}" ]; then
    echo "smoke: set all of SMOKE_BACKEND_IMAGE, SMOKE_FRONTEND_IMAGE and SMOKE_BACKUP_IMAGE, or none" >&2
    failed+=("smoke: incomplete SMOKE_*_IMAGE")
    return
  fi
  run "smoke: production compose stack" bash scripts/smoke-prod.sh
}

stages=("$@")
[ ${#stages[@]} -eq 0 ] && stages=(versions backend frontend)

for stage in "${stages[@]}"; do
  case $stage in
    versions|backend|frontend|e2e|backup|scan|smoke) "stage_$stage" ;;
    *) echo "unknown stage '$stage' (versions backend frontend e2e backup scan smoke)" >&2; exit 2 ;;
  esac
done

if [ ${#failed[@]} -ne 0 ]; then
  echo >&2
  echo "verify: ${#failed[@]} step(s) failed:" >&2
  printf '  - %s\n' "${failed[@]}" >&2
  exit 1
fi
echo "verify: all steps passed"
