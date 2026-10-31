#!/usr/bin/env bash
# Step 7 of the release skill (.claude/skills/release/SKILL.md): the release branch, the CHANGELOG
# promotion, the three version bumps and the one commit, locally. Nothing is pushed.
#
#   scripts/release/prepare.sh X.Y.Z <approved CHANGELOG.md> [--base <sha>] [--skip-verify]
#
#   <approved CHANGELOG.md>  the complete new CHANGELOG the user approved (CHANGELOG.proposed.md)
#   --base <sha>             the origin/main commit the draft was made from; refuses when
#                            CHANGELOG.md changed on origin/main since, because the draft would
#                            silently drop those entries
#   --skip-verify            skip `scripts/verify.sh versions` (the script's own tests use it)
#
# Creates the worktree <main checkout>/.claude/worktrees/release-vX.Y.Z on the new branch
# chore/release-vX.Y.Z off origin/main (see docs/conventions/tooling.md), copies the
# CHANGELOG over, sets `version` in backend/build.gradle, frontend/package.json and
# frontend/package-lock.json (ADR 0007), checks that nothing else changed and makes the single
# commit `chore: release vX.Y.Z` (ADR 0009), staging the files by name. On any failure after the
# worktree exists it is left for inspection (`git worktree remove --force <path>` drops it).
set -euo pipefail

die() { echo "prepare: $*" >&2; exit 1; }

version=${1:-}
proposed=${2:-}
[ $# -ge 2 ] || die "usage: $0 X.Y.Z <approved CHANGELOG.md> [--base <sha>] [--skip-verify]"
shift 2
base=
skip_verify=0
while [ $# -gt 0 ]; do
  case $1 in
    --base) base=${2:-}; [ -n "$base" ] || die "--base needs a sha"; shift 2 ;;
    --skip-verify) skip_verify=1; shift ;;
    *) die "unknown argument '$1'" ;;
  esac
done
[[ $version =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || die "'$version' is not X.Y.Z"
[ -f "$proposed" ] || die "no such file: $proposed"
proposed=$(cd "$(dirname "$proposed")" && pwd)/$(basename "$proposed")

cd "$(dirname "$0")/../.."
branch=chore/release-v$version
tag=v$version
root=$(dirname "$(git rev-parse --path-format=absolute --git-common-dir)")
wt=$root/.claude/worktrees/release-v$version

# --- Preconditions: nothing of this release exists yet, and the draft is still current ------------
git fetch origin --tags --quiet
if git rev-parse --verify --quiet "refs/heads/$branch" >/dev/null; then die "branch $branch already exists locally"; fi
if [ -n "$(git ls-remote --heads origin "$branch")" ]; then die "branch $branch already exists on origin"; fi
if git rev-parse --verify --quiet "refs/tags/$tag" >/dev/null || [ -n "$(git ls-remote --tags origin "$tag")" ]; then
  die "tag $tag already exists"
fi
[ ! -e "$wt" ] || die "$wt already exists"
if [ -n "$base" ] && ! git diff --quiet "$base" origin/main -- CHANGELOG.md; then
  die "CHANGELOG.md changed on origin/main since $base: regenerate the draft and show the difference"
fi

# --- Branch and worktree --------------------------------------------------------------------------
git worktree add --no-track -b "$branch" "$wt" origin/main >/dev/null
cd "$wt"

cp "$proposed" CHANGELOG.md

grep -qE "^version = '[^']+'$" backend/build.gradle || die "no  version = '...'  line in backend/build.gradle"
sed -i -E "s/^version = '[^']+'$/version = '$version'/" backend/build.gradle

# `npm version` fails with "Version not changed" when package.json already has the target, and it
# needs no node_modules.
if [ "$(node -p "require('./frontend/package.json').version")" != "$version" ]; then
  (cd frontend && npm version "$version" --no-git-tag-version >/dev/null)
fi

gradle_v=$(sed -nE "s/^version = '([^']+)'$/\1/p" backend/build.gradle)
package_v=$(node -p "require('./frontend/package.json').version")
lock_v=$(node -p "require('./frontend/package-lock.json').version")
[ "$gradle_v" = "$version" ] && [ "$package_v" = "$version" ] && [ "$lock_v" = "$version" ] \
  || die "versions not equal to $version: build.gradle $gradle_v, package.json $package_v, package-lock.json $lock_v"

if [ "$skip_verify" = 0 ]; then bash scripts/verify.sh versions >/dev/null || die "scripts/verify.sh versions failed"; fi

# --- Scope: only the four release files may differ ------------------------------------------------
allowed=(CHANGELOG.md backend/build.gradle frontend/package.json frontend/package-lock.json)
changed=()
while IFS= read -r line; do [ -n "$line" ] && changed+=("${line:3}"); done < <(git status --porcelain)
for f in "${changed[@]}"; do
  case " ${allowed[*]} " in *" $f "*) ;; *) die "unexpected change: $f (only ${allowed[*]} may change)" ;; esac
done
case " ${changed[*]} " in *" CHANGELOG.md "*) ;; *) die "CHANGELOG.md is identical to origin/main's: nothing to release" ;; esac

# --- The one commit, files staged by name ---------------------------------------------------------
git add -- "${changed[@]}"
git commit -q -m "chore: release v$version"

committed=$(git show --name-only --format= HEAD | sort | tr '\n' ' ')
expected=$(printf '%s\n' "${changed[@]}" | sort | tr '\n' ' ')
[ "$committed" = "$expected" ] || die "the commit holds [$committed], expected [$expected]"
[ -z "$(git status --short)" ] || die "the working tree is not clean after the commit"

echo "prepare: $branch at $(git rev-parse --short HEAD) in $wt"
git show --stat --format='%h %s' HEAD
