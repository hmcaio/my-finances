#!/usr/bin/env bash
# Upserts every .github/rulesets/*.json into the GitHub repo, matching by ruleset name.
# The ruleset name must equal the file's basename. Needs `gh` logged in with admin rights
# on the repo. Run it after editing a ruleset file; the files are the source of truth (ADR 0032).
#
# It only creates and updates: it never deletes a ruleset, so removing a file here leaves the
# ruleset on GitHub. The retired `protect-develop` ruleset (ADR 0032, migration step 4) is removed
# by hand, after the final develop -> main promotion and before the develop branch is deleted:
#   gh api repos/hmcaio/my-finances/rulesets --jq '.[] | [.id, .name] | @tsv'   # find the id
#   gh api -X DELETE repos/hmcaio/my-finances/rulesets/<protect-develop id>     # 25432305 when written
set -euo pipefail

cd "$(dirname "$0")/.."
repo="$(gh repo view --json nameWithOwner -q .nameWithOwner)"

for file in .github/rulesets/*.json; do
  name="$(basename "$file" .json)"
  id="$(gh api "repos/$repo/rulesets" --jq ".[] | select(.name == \"$name\") | .id")"
  if [ -n "$id" ]; then
    gh api -X PUT "repos/$repo/rulesets/$id" --input "$file" --silent
    echo "updated $name (#$id)"
  else
    gh api -X POST "repos/$repo/rulesets" --input "$file" --silent
    echo "created $name"
  fi
done
