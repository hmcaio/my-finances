---
name: dependabot
description: Settles the open Dependabot PRs of my-finances, folding sibling pins into one PR and merging green ones one at a time. Use when the user says "handle", "merge" or "clear" the dependabot PRs or bumps. Not for a major bump of postgres, eclipse-temurin or node (by hand, docs/conventions/tooling.md "Pinned versions").
---

# Dependabot

Dependabot edits one file per PR, so a bump of a tool pinned in several files fails `scripts/check-versions.sh` until the siblings move too. The sibling lists are in [tooling.md](../../../docs/conventions/tooling.md) ("Pinned versions"). Pushing to a Dependabot branch and merging are outward-facing: the user's request to handle the PRs covers both; "just look" or "don't merge" does not.

## 1. List compactly

`gh pr list --author app/dependabot --json number,title,headRefName`, then per PR `gh pr checks <n> | grep -vE "	pass	|skipping"`. Never request `statusCheckRollup`: it is ~2k tokens per PR. Done when every PR is green, failing with a named cause, or pending.

## 2. Diagnose each red PR

`gh run view --job <id> --log-failed | grep MISMATCH`. Sort the cause:

- **`MISMATCH`**: a sibling pin. Step 3.
- **`429` from Maven Central or npm** (`Could not GET ... Too Many Requests`): a flake. `gh run rerun <run> --failed`.
- **Anything else**: a real failure from the bump; report it and leave the PR.

## 3. Fold siblings into one branch

Two PRs bumping the same tool (the temurin pair spans `docker` and `docker-compose`) become one: pick one branch, `git checkout` it, `git merge origin/main` (a merge, not a rebase: no force-push), apply the other PR's diff by hand (`gh pr diff <n>`), run `scripts/verify.sh versions`, commit (Conventional Commits, `chore(deps)`), `git push`. Then `gh pr close <other> -c "Folded into #<kept> (sibling pin; check-versions.sh)."`. Done when `verify.sh versions` passes and the duplicate is closed. A file the diff misses shows up in `grep -rn "<old-version>" --exclude-dir=node_modules --exclude-dir=.git .`; a hit in `CHANGELOG.md` under a released version is history, not a sibling.

## 4. Merge one at a time

The ruleset requires a branch up to date with `main`, so each merge makes every other open PR stale and costs it a CI cycle (~5 min). For each PR in turn: `gh pr update-branch <n>`, wait for CI (`sleep 280`, then count `pending|fail` in `gh pr checks <n>`), `gh pr merge <n> --squash --delete-branch`. Check `gh pr view <n> --json state -q .state` is `MERGED` before the next. Done when `gh pr list --author app/dependabot` is empty or each leftover has a stated reason.

## 5. Report

One line per PR: merged, folded into, or left and why.
