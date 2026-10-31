---
name: release
description: Prepares and drives a my-finances release (CHANGELOG promotion, version bump, release PR, rc and final tags, published-image check, GitHub release); every push or publish waits for the user's explicit confirmation. Use for /release [X.Y.Z], /release --check (reconcile only), /release continue (resume), or any ask to prepare, cut, check or plan a release, a version bump, release notes or "what's unreleased". Not for building a feature (implement-feature) or fixing an audit finding (audit-and-fix).
---

# Release

Feature and fix PRs add their own `CHANGELOG.md` entry under `## [Unreleased]` ([`release.md`](../../../docs/conventions/release.md)), so a release reconciles that section against what merged and then carries the release as far as the user confirms. The runbook, with the reasons and the gotchas, is [`docs/conventions/release.md`](../../../docs/conventions/release.md); this file is the procedure and the limits. [ADR 0007](../../../docs/adr/0007-single-shared-semver-and-changelog.md) keeps the release manual, and a release tag push is the only thing that publishes images ([ADR 0032](../../../docs/adr/0032-github-flow-main-only.md)).

Forms: `/release` (suggest the version) · `/release <version>` (`X.Y.Z`, a leading `v` is accepted; heading `X.Y.Z`, tag `vX.Y.Z`; check it is greater than the last release and say whether it matches the implied bump — their choice wins) · `/release --check` (Steps 1–5 only: report the findings and stop; approved fixes go unstaged into the current checkout's `CHANGELOG.md`) · `/release continue` (resume).

## Phases and checkpoints

| Phase | Steps | The skill | Then waits for |
|---|---|---|---|
| 1. Draft | 1–6 | reconciles, picks the version, recommends an rc, shows the draft | **approval** of version, draft and rc decision |
| 2. Release PR | 7 | worktree, branch, CHANGELOG, bumps, one commit; after **confirmation** pushes and opens the PR | **the user to merge** and say so |
| 3. Candidate | 8–9 | after **confirmation** tags and pushes `vX.Y.Z-rc.N` (if recommended), verifies the images | **the user to read the report** and say go on |
| 4. Release | 10 | after **confirmation** tags and pushes `vX.Y.Z`, verifies; after a second **confirmation** `gh release create` | nothing: it reports and ends |

A checkpoint is a stop: end the turn with what was done, the exact commands, and what you wait for. Continue only on the user's reply in a later turn. Without an rc, Step 9 runs after the final tag.

**Resuming** needs nothing from the conversation: the version is in the branch name `chore/release-vX.Y.Z`, the rc decision and tag day in the PR body's `Release candidate:` and `Tag day:` lines (Step 7), the release commit in the PR's merge commit, the tags in `git ls-remote --tags origin 'vX.Y.Z*'`. State which phase you inferred before acting, and ask when the pieces disagree.

## Hard limits

Local writes, each at the step that names it: the worktree, branch and commit of Step 7 (only `CHANGELOG.md`, `backend/build.gradle`, `frontend/package.json`, `frontend/package-lock.json`); with `--check` the unstaged CHANGELOG fixes; the annotated tags `vX.Y.Z-rc.N` and `vX.Y.Z` on the release PR's squash commit, created in the same confirmed action as their push.

**Outward actions need a confirmation each**, and these four are the only ones: (1) `git push -u origin chore/release-vX.Y.Z` with `gh pr create`; (2) the rc tag and its push; (3) the final tag and its push; (4) `gh release create`. Show the exact command with what it uses (title, body, sha, notes) and run it only when the user's next message says yes to it. An earlier approval never covers a later action; if the sha, notes or title change after the yes, ask again; a "no", silence or a question means do not run it. One action at a time, each result reported (PR URL, pushed ref).

**Look before acting**, so a retry is safe: before each action check whether it already happened (`git ls-remote --heads|--tags origin`, `gh pr list --head … --state all`, `gh release view`) and resume from where it stopped. A local tag that is not on origin is reused only when it points at the intended `<sha>`; otherwise ask, never delete it.

Never: `gh pr merge` (the user merges), `git push --force`, moving or deleting a tag (immutable: a bad one means the next `rc.N` or patch), committing to `main`, amending after a push, pushing anything but the release branch and release tags, or a skip-CI marker in anything proposed or run ([CLAUDE.md](../../../CLAUDE.md), Workflow).

## Steps 1–5 — Reconcile

```
node scripts/release/reconcile.mjs [--ref origin/main] [--since vX.Y.Z] [--no-gh]
```

Read-only. It fetches, works from `origin/main` (never the working tree: the CHANGELOG is read from the ref), takes the last **stable** tag (an `-rc.N` is not a release), lists the range's commits classified by [ADR 0009](../../../docs/adr/0009-conventional-commits.md), matches `[Unreleased]` bullets to commits, and prints the findings a–h plus the version fields, the mechanical bump, the upgrade-sensitive diff, the rc files and skip markers. Say which ref and commit it used. No tag means the first release (default `0.1.0`, every user-visible change is new). A tag not on `main`, or unequal `version` fields, is a warning to pass on.

| Commit type | CHANGELOG section | Bump |
|---|---|---|
| `feat` | Added (Changed if it extends existing behaviour) | MINOR |
| `fix` | Fixed | PATCH |
| `!` or `BREAKING CHANGE:` | Changed, marked **BREAKING** | MAJOR |
| `docs` `chore` `ci` `build` `style` `test` `refactor` `perf` | none, unless user-visible | none |

The script lists facts; **you judge** these, and tell the user instead of deciding silently:

- **Borderline commits.** A `feat`/`fix` that only touches docs or tooling, or adds dev-time tooling, needs no entry (list it; say what the bump would be without it). A `refactor`/`perf` with a visible effect needs a Changed entry. Non-conventional commits (before ADR 0009) are listed as "not classified", never forced into a type.
- **a. Missing entry:** draft the bullet in the format of [`release.md`](../../../docs/conventions/release.md) (CHANGELOG entries); title from the first heading of `docs/features/FXXX-*/spec.md`; PR links need the repo URL (`gh repo view --json url -q .url`).
- **b. Missing PR link:** apply the script's proposals; place the *ambiguous* ones (one tracking issue, several bullets) by reading the PRs.
- **c. Stray / d. section mismatch / f. entry that shouldn't exist:** flag, never delete.
- **e. Upgrade notes:** every changed volume, env var, compose service, config key, image or nginx setting and Flyway migration in the script's diff needs an `Upgrade:` line (a migration runs on startup; say when one can fail on existing data). A note belongs only where a person running the stack has something to do. On the first release keep only what matters to an existing dev/`latest` install.
- **g. Release candidate:** the script recommends one when workflow, Dockerfile or compose files changed ([`release.md`](../../../docs/conventions/release.md), step 3). Give the reason; the user decides.
- **h. Section order:** reorder in the draft and say so.
- **Breaking while 0.x:** ADR 0009 says MAJOR, ADR 0007 reserves `1.0.0`: give the mechanical answer, flag the conflict, let the user choose. No user-visible commit: say no release seems warranted and ask.

"No gaps" is a valid result; do not invent findings.

## Step 6 — Show the draft and wait

Reply with, in order: **version** (suggested or given, last release and range, why; both `version` fields); **findings** a–h with the proposed fix; **the result**; **left out** (counts by type, then each borderline commit with the reason); **release candidate** (recommended or not, with the reason); **warnings**.

**The result** is the complete new `CHANGELOG.md` written to `CHANGELOG.proposed.md` in the scratchpad directory, shown as its path plus a compact view against `origin/main`'s: the new heading and intro paragraph in full, the footer lines, the reordered sections, and one line per changed bullet saying how (`+ PR links #221`). Pasting a release section whole is unreadable; do it only when it is short or asked. The file holds a fresh empty `## [Unreleased]`, then `## [X.Y.Z] - YYYY-MM-DD`, an intro paragraph, the entries, and the link footer (`[Unreleased]` re-pointed to `compare/vX.Y.Z...HEAD`, a new `[X.Y.Z]: …/compare/v<previous>...vX.Y.Z` line; the first release uses `…/releases/tag/vX.Y.Z` as `[0.1.0]` does). The date is the day the **final** tag will be pushed ([`release.md`](../../../docs/conventions/release.md), date gotcha): ask for it, normally today without an rc and later with one.

Ask for edits or approval of **version, draft and rc decision**, and write nothing before it: the version names the branch. Approval authorizes Step 7 and nothing past its checkpoint.

## Step 7 — Branch, bump, commit (phase 2)

```
bash scripts/release/prepare.sh X.Y.Z <CHANGELOG.proposed.md> --base <the origin/main commit of Step 1>
```

It creates the worktree `<root>/.claude/worktrees/release-vX.Y.Z` (`<root>` is the main checkout; [`tooling.md`](../../../docs/conventions/tooling.md)) on the branch `chore/release-vX.Y.Z` off `origin/main`, so the user's checkout is never switched or written (a dirty `CHANGELOG.md` there is neither read nor touched: say so), and refuses when the branch, the remote branch or the tag exists or when `origin/main`'s CHANGELOG changed since `--base` (then redo Steps 4–5 for the delta and show the difference). It copies the approved file, bumps the three versions, runs `verify.sh versions`, requires that only those four files changed, stages them by name and makes the one commit `chore: release vX.Y.Z`. On any failure it stops and leaves the worktree: show the message. The pre-commit hook skips the format check in a worktree without `node_modules`; only version lines changed.

Then check that the commit, and the PR text you draft, carry no skip-CI marker, and name any marker in the range or on the `origin/main` tip (the script's report); if the tip has one, tell the user to add a commit or reword first. Draft the PR with the `pr` skill when available: title `chore: release vX.Y.Z`, body in a scratchpad file, with two plain lines the skill reads back on resume, `Release candidate: rc recommended (<reason>)` or `Release candidate: not recommended (<reason>)`, and `Tag day: YYYY-MM-DD`.

### Checkpoint A — the release PR

Report `git show --stat`, show the PR title, body and the two commands, and ask:

```
git -C <root>/.claude/worktrees/release-vX.Y.Z push -u origin chore/release-vX.Y.Z
gh pr create --base main --head chore/release-vX.Y.Z --title "chore: release vX.Y.Z" --body-file <PR body file>
```

On the yes run them in order and give the PR URL. Say what to wait for: the required checks (`Build images and scan` and `Prod smoke` run because the version files match the `images` filter; scan locally with `bash scripts/verify.sh scan` only if the PR is docs-only), a **squash** merge ([ADR 0032](../../../docs/adr/0032-github-flow-main-only.md)) whose title and body have no skip marker, and to say if the tag day will move (the date then needs a docs-only PR first). Then stop until the user says it is merged; `gh pr view <N> --json state,mergeCommit` confirms it.

## Step 8 — Release candidate (phase 3)

On "merged":

1. `git fetch origin --tags`; the squash commit is `gh pr view <N> --json mergeCommit -q .mergeCommit.oid` (or `gh pr list --head chore/release-vX.Y.Z --state merged`). Call it `<sha>`: never "whatever `main` is now", so merges that landed since do not leak in.
2. Check and report: `git merge-base --is-ancestor <sha> origin/main`; the three version files at `<sha>` carry `X.Y.Z`; its CHANGELOG has `## [X.Y.Z] - <date>` and no bullet without a PR link; its full message has no skip marker; the push-to-`main` run on `<sha>` (`gh run list --workflow ci.yml --branch main --commit <sha>`) **finished green** (right after a merge it is usually running: `gh run watch <id> --exit-status` in the background, never call a pending run green). Any failure: stop and say what it implies; a bad commit is fixed by an ordinary PR, and that PR's squash commit becomes `<sha>`.
3. Compare the CHANGELOG date with the `Tag day:` line: if the final tag will not be pushed that day, say now that a docs-only re-date PR comes before the final tag.
4. Read the `Release candidate:` line (the user's word in chat wins). Not recommended: go to Step 10. Recommended: the number is one above the highest `vX.Y.Z-rc.N` on origin (`git ls-remote --tags origin 'vX.Y.Z-rc.*'`), `1` when none. Nothing is created yet.

### Checkpoint B — create and push the rc tag

Show the tag, `<sha>` with its subject, and ask:

```
git tag -a vX.Y.Z-rc.N -m vX.Y.Z-rc.N <sha>
git push origin vX.Y.Z-rc.N
```

On the yes run both, confirm `git rev-parse vX.Y.Z-rc.N^{commit}` prints `<sha>`, and go to Step 9. Without one, stop; nothing was created.

## Step 9 — Verify the published images

Against the tag just pushed, `<T>`, from a checkout that has the script:

```
bash scripts/verify-release-images.sh <T> [--skip-smoke]
```

It waits for the tag run, then checks the three images resolve anonymously under `<T>` and `sha-<short>` with equal digests, that `latest` is right (the tag for a release, unmoved for an rc), and runs the fresh-install smoke on the pulled images; exit 0 only when every check passed. Run it in the background. First `docker ps -a --filter name=my-finances`: a real prod stack makes the smoke refuse to start. Never stop anyone's containers; ask the user to stop it, or use `--skip-smoke` and say so in the report. A failing tag run: report the job and stop (a transient registry blip is re-run by the user in the Actions UI; a workflow, Dockerfile or scan failure burns the tag: [runbook](../../../docs/conventions/release.md)).

**Report:** the script's `PASS`/`FAIL` lines as a table with the evidence, then the runbook items it cannot do, for the user ([`release.md`](../../../docs/conventions/release.md)): one UI flow, the restore drill and, from the second release on, the upgrade test from the previous tag. Say plainly what failed or was not run, a `--skip-smoke` included; never call the candidate good on partial evidence.

For an rc this is **Checkpoint C**: wait for the user to confirm the report. A defect found is an ordinary fix PR with its bullet under the existing `## [X.Y.Z]` (re-dated), then `rc.N+1`; on "fixed and merged" repeat from Step 8 with that PR's squash commit.

## Step 10 — Tag the release (phase 4)

On the go-ahead after Checkpoint C (or straight from Step 8 without an rc):

1. `<sha>` is the release PR's squash commit (after an rc fix cycle, the last fix's). Repeat Step 8's checks, plus: the CHANGELOG date equals `date +%F` (if not, stop: a docs-only PR fixes the date and the tag goes on that later commit) and `vX.Y.Z` is not on origin.
2. Write the release notes to a scratchpad file: the body of the `## [X.Y.Z]` section of `git show <sha>:CHANGELOG.md`, up to the next `## `. The release is titled `vX.Y.Z`.
3. Show the tag, `<sha>` with its subject, the notes and the commands, and ask:

```
git tag -a vX.Y.Z -m vX.Y.Z <sha>
git push origin vX.Y.Z
```

On the yes run both, confirm `git rev-parse vX.Y.Z^{commit}` prints `<sha>`, and run Step 9 against `vX.Y.Z`. Without a yes, stop.

4. Only when that report is clean, show the command and ask again (the push yes does not cover it):

```
gh release create vX.Y.Z --verify-tag --latest --title vX.Y.Z --notes-file <notes file>
```

On the yes run it and give the release URL. Last, list what remains for the user: Step 9's manual checks when there was no rc, `IMAGE_TAG=vX.Y.Z` in the prod `.env`, and ticking any plan item the release proved (for the first release, F014's publish item). Offer to remove the release worktree (`git worktree remove <root>/.claude/worktrees/release-vX.Y.Z`); not unasked.
