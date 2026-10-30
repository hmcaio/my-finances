#!/usr/bin/env bash
# Published-image check for a release tag (docs/conventions/release.md, "Verifying the published
# images"). Run it after the tag is pushed, against vX.Y.Z or vX.Y.Z-rc.N:
#
#   scripts/verify-release-images.sh vX.Y.Z[-rc.N] [--skip-smoke] [--no-wait]
#
# It checks what a shell can see of the published artifact:
#   1. the tag run on the tagged commit finished green (waits for it unless --no-wait);
#   2. the backend, frontend and backup images resolve anonymously (no login, straight against
#      ghcr.io) under <tag> and sha-<short>, with the same digest;
#   3. `latest`: for the newest stable release it is the same digest as the tag; for an rc it did
#      not move to the rc (it is still the newest stable release, or absent before the first one);
#   4. unless --skip-smoke, the three published images are pulled anonymously and run through
#      scripts/smoke-prod.sh from a checkout of the tagged commit (the fresh-install smoke).
# What stays manual: a UI flow, the restore drill, the upgrade test from the previous tag.
#
# Read-only against the registry and GitHub; it pushes nothing and needs no login. The pulled images
# stay in the local image store. The smoke refuses to start beside a real prod stack
# (docker-compose.prod.yml hardcodes container names), so stop it or use --skip-smoke.
# Needs git (remote `origin`), gh (authenticated: it reads the CI run), curl and docker.
# Exit 0 when every check passed, 1 otherwise.
set -uo pipefail
cd "$(dirname "$0")/.." || exit 1

REGISTRY=ghcr.io
OWNER_REPO=hmcaio/my-finances
NAMES=(backend frontend backup)

usage() { echo "usage: $0 vX.Y.Z[-rc.N] [--skip-smoke] [--no-wait]" >&2; exit 2; }
die() { echo "verify-release-images: $*" >&2; exit 2; }

tag=
skip_smoke=0
wait_run=1
for arg in "$@"; do
  case $arg in
    --skip-smoke) skip_smoke=1 ;;
    --no-wait) wait_run=0 ;;
    -*) usage ;;
    *) [ -z "$tag" ] || usage; tag=$arg ;;
  esac
done
[ -n "$tag" ] || usage
[[ $tag =~ ^v[0-9]+\.[0-9]+\.[0-9]+(-rc\.[0-9]+)?$ ]] || die "'$tag' is not vX.Y.Z or vX.Y.Z-rc.N"
for tool in git gh curl docker; do command -v "$tool" >/dev/null 2>&1 || die "$tool is not installed"; done

passed=0
failed=0
report() { # report <PASS|FAIL> <name> [detail]
  printf '%-5s %s%s\n' "$1" "$2" "${3:+  ($3)}"
  if [ "$1" = PASS ]; then passed=$((passed + 1)); else failed=$((failed + 1)); fi
}

tmp=$(mktemp -d) || die "mktemp failed"
cleanup() {
  trap - EXIT
  git worktree remove --force "$tmp/src" >/dev/null 2>&1
  rm -rf "$tmp"
}
trap cleanup EXIT
trap 'exit 130' INT TERM

# --- The tagged commit, as published on origin (an annotated tag is peeled to its commit) ---------
refs=$(git ls-remote origin "refs/tags/$tag" "refs/tags/$tag^{}") || die "cannot reach origin"
sha=$(printf '%s\n' "$refs" | awk -v r="refs/tags/$tag^{}" '$2 == r {print $1}')
[ -n "$sha" ] || sha=$(printf '%s\n' "$refs" | awk -v r="refs/tags/$tag" '$2 == r {print $1}')
[ -n "$sha" ] || die "tag $tag is not on origin: push it first"
short=${sha:0:7}
echo "verify-release-images: $tag -> $sha (sha-$short)"

# sha-<short> is one tag per commit: when the final release is tagged on the commit an rc was
# tagged on, its build replaced sha-<short>, so for the rc the comparison below no longer holds.
sha_superseded=0
if [[ $tag == *-rc.* ]]; then
  final=$(git ls-remote --tags origin 'refs/tags/v*' | awk -v s="$sha" '$1 == s {print $2}' \
    | sed 's#^refs/tags/##; s#\^{}$##' | grep -E '^v[0-9]+\.[0-9]+\.[0-9]+$' | head -n 1)
  [ -z "$final" ] || sha_superseded=1
fi

# --- 1. The tag run ---------------------------------------------------------------------------------
run_id=
for _ in $(seq 1 30); do # the run shows up a few seconds after the push
  run_id=$(gh run list --workflow ci.yml --branch "$tag" --limit 10 --json databaseId,headSha \
    -q "[.[] | select(.headSha == \"$sha\")][0].databaseId // empty" 2>/dev/null)
  [ -n "$run_id" ] && break
  sleep 10
done
if [ -z "$run_id" ]; then
  report FAIL "tag run" "no ci.yml run for $tag on $short; a skip marker in the commit message would do this"
else
  status=$(gh run view "$run_id" --json status -q .status)
  if [ "$status" != completed ] && [ "$wait_run" = 1 ]; then
    echo "verify-release-images: waiting for run $run_id ($status) ..."
    gh run watch "$run_id" --interval 30 >/dev/null 2>&1
  fi
  read -r status conclusion url < <(gh run view "$run_id" --json status,conclusion,url \
    -q '[.status, (.conclusion // "-"), .url] | @tsv')
  if [ "$conclusion" = success ]; then report PASS "tag run" "$url"
  else report FAIL "tag run" "status=$status conclusion=$conclusion $url"; fi
fi

# --- 2. Digests, anonymously, straight from the registry -------------------------------------------
# digest <name> <tag>: the registry's digest for the tag, empty when it does not resolve. A token is
# requested without credentials, so a private package fails here exactly as it would for a stranger.
digest() {
  local repo=$OWNER_REPO-$1 token
  token=$(curl -fsS "https://$REGISTRY/token?scope=repository:$repo:pull" 2>/dev/null \
    | sed -n 's/.*"token" *: *"\([^"]*\)".*/\1/p')
  [ -n "$token" ] || return 0
  curl -fsSI -H "Authorization: Bearer $token" \
    -H 'Accept: application/vnd.oci.image.index.v1+json, application/vnd.docker.distribution.manifest.list.v2+json, application/vnd.oci.image.manifest.v1+json, application/vnd.docker.distribution.manifest.v2+json' \
    "https://$REGISTRY/v2/$repo/manifests/$2" 2>/dev/null \
    | awk 'tolower($1) == "docker-content-digest:" {gsub(/\r/, ""); print $2}'
}

stable_tags=$(git ls-remote --tags origin 'refs/tags/v*' | awk '{print $2}' | sed 's#^refs/tags/##' \
  | grep -E '^v[0-9]+\.[0-9]+\.[0-9]+$' | sort -V)
newest=$(printf '%s\n' "$stable_tags" | tail -n 1)

declare -A tag_digest
for name in "${NAMES[@]}"; do
  d_tag=$(digest "$name" "$tag")
  d_sha=$(digest "$name" "sha-$short")
  tag_digest[$name]=$d_tag
  if [ -z "$d_tag" ]; then
    report FAIL "$name :$tag" "does not resolve anonymously (missing, or the GHCR package is private)"
  elif [ "$sha_superseded" = 1 ]; then
    report PASS "$name :$tag resolves" "$d_tag; sha-$short not compared, $final was tagged on this commit and rebuilt it"
  elif [ -z "$d_sha" ]; then
    report FAIL "$name :sha-$short" "does not resolve anonymously"
  elif [ "$d_tag" != "$d_sha" ]; then
    report FAIL "$name :$tag vs :sha-$short" "$d_tag != $d_sha"
  else
    report PASS "$name :$tag = :sha-$short" "$d_tag"
  fi

  # --- 3. latest ---------------------------------------------------------------------------------
  d_latest=$(digest "$name" latest)
  if [[ $tag != *-rc.* ]]; then
    if [ "$tag" != "$newest" ]; then
      echo "note  $name :latest not checked: $tag is not the newest release ($newest)"
    elif [ -n "$d_tag" ] && [ "$d_latest" = "$d_tag" ]; then report PASS "$name :latest = :$tag"
    else report FAIL "$name :latest = :$tag" "latest=${d_latest:-none} tag=${d_tag:-none}"; fi
  elif [ -z "$newest" ]; then
    if [ -z "$d_latest" ]; then report PASS "$name :latest absent (no release yet)"
    else report FAIL "$name :latest moved by an rc" "latest=$d_latest but no stable release exists"; fi
  else
    d_newest=$(digest "$name" "$newest")
    if [ -n "$d_newest" ] && [ "$d_latest" = "$d_newest" ] && [ "$d_latest" != "$d_tag" ]; then
      report PASS "$name :latest unmoved by the rc (= :$newest)"
    else report FAIL "$name :latest unmoved by the rc" "latest=${d_latest:-none} $newest=${d_newest:-none} rc=${d_tag:-none}"; fi
  fi
done

# --- 4. Fresh-install smoke on the published images ------------------------------------------------
if [ "$skip_smoke" = 1 ]; then
  echo "note  smoke skipped (--skip-smoke)"
else
  mkdir "$tmp/docker-config" # empty: the user's own `docker login` is neither used nor removed
  pulled=1
  for name in "${NAMES[@]}"; do
    if ! DOCKER_CONFIG=$tmp/docker-config docker pull -q "$REGISTRY/$OWNER_REPO-$name:$tag" >/dev/null 2>"$tmp/pull.err"; then
      report FAIL "anonymous pull $name :$tag" "$(head -n 1 "$tmp/pull.err")"
      pulled=0
    fi
  done
  if [ "$pulled" = 1 ]; then
    report PASS "anonymous pull of the three images"
    git cat-file -e "$sha^{commit}" 2>/dev/null || git fetch -q origin "refs/tags/$tag"
    if git worktree add --detach -q "$tmp/src" "$sha"; then
      if (cd "$tmp/src" \
        && SMOKE_BACKEND_IMAGE=$REGISTRY/$OWNER_REPO-backend:$tag \
          SMOKE_FRONTEND_IMAGE=$REGISTRY/$OWNER_REPO-frontend:$tag \
          SMOKE_BACKUP_IMAGE=$REGISTRY/$OWNER_REPO-backup:$tag \
          bash scripts/smoke-prod.sh); then
        report PASS "prod smoke on the published images"
      else
        report FAIL "prod smoke on the published images" "see its output above"
      fi
    else
      report FAIL "prod smoke on the published images" "cannot check out $short"
    fi
  fi
fi

echo
echo "verify-release-images: $tag: $passed passed, $failed failed"
echo "Still manual: one UI flow, the restore drill, the upgrade test from the previous tag."
[ "$failed" -eq 0 ]
