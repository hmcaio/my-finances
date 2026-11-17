#!/usr/bin/env bash
# One-time per clone, idempotent: install the git hooks from .pre-commit-config.yaml (ADR 0036).
#   pre-commit: Betterleaks secret scan of the staged diff + the staged-aware format check
#   pre-push:   Semgrep (metrics off)
# `scripts/setup-hooks.sh --uninstall` removes them. Bypass in an emergency with --no-verify.
set -eu
cd "$(dirname "$0")/.."

command -v pre-commit >/dev/null || {
  echo "setup-hooks: pre-commit not found. Install it: uv tool install pre-commit (or pipx install pre-commit)" >&2
  exit 1
}

if [ "${1:-}" = "--uninstall" ]; then
  pre-commit uninstall -t pre-commit -t pre-push
  exit 0
fi

# The old setup pointed git at .githooks/ (repo-wide, shared by all worktrees). pre-commit refuses
# to install while core.hooksPath is set, so drop that one value and nothing else.
hooks_path="$(git config --get core.hooksPath || true)"
case "$hooks_path" in
  .githooks | */.githooks)   # relative or absolute, depending on how it was set
    git config --unset core.hooksPath
    echo "setup-hooks: unset core.hooksPath=$hooks_path (replaced by pre-commit)"
    ;;
esac

pre-commit install --install-hooks
echo "setup-hooks: installed (shared by all worktrees of this clone)"
