#!/usr/bin/env bash
# One-time, idempotent: point git at the versioned hooks in .githooks/ (see .githooks/pre-commit).
# `core.hooksPath` lives in the repo's shared .git/config, so it applies to EVERY worktree of this
# clone, not just the one you run this in. `scripts/setup-hooks.sh --uninstall` unsets it again.
set -eu
cd "$(dirname "$0")/.."

if [ "${1:-}" = "--uninstall" ]; then
  git config --unset core.hooksPath 2>/dev/null || true
  echo "setup-hooks: unset core.hooksPath (git hooks disabled for all worktrees of this clone)"
else
  git config core.hooksPath .githooks
  echo "setup-hooks: set core.hooksPath=.githooks (repo-wide, shared by all worktrees): pre-commit runs spotlessCheck / prettier / eslint on staged files"
fi
