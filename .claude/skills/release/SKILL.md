---
name: release
description: Prepares and drives a my-finances release from the CHANGELOG.md that PRs keep current. Reconciles the `[Unreleased]` section on main against the Conventional Commits merged since the last vX.Y.Z tag (or the full history for the first release, 0.1.0), suggests the SemVer bump (ADR 0009) and whether a release candidate is warranted, then on approval creates the `chore/release-vX.Y.Z` branch in a worktree and commits the CHANGELOG promotion with the version bumps. It then pushes the branch and opens the release PR, tags the release candidate when one is recommended, pushes the tags, verifies the published images and creates the GitHub release, each outward action only after the user confirms that exact command. Checkpointed — it never merges the PR; the user does, and says when. Use this whenever the user asks to prepare, draft, cut, check, continue or plan a release, a version bump, release notes, "what's unreleased" or whether the CHANGELOG is up to date, or invokes /release [version], even if they don't say "changelog". Not for building a feature (implement-feature) or fixing an audit finding (audit-and-fix) — those two write the per-PR entries this skill checks.
---

# Release

Feature and fix PRs add their own `CHANGELOG.md` entry under `## [Unreleased]` ([`docs/conventions/release.md`](../../../docs/conventions/release.md), "CHANGELOG entries"), so at release time the entry mostly exists already. This skill checks it against what actually merged, fixes the gaps, and then carries the release as far as the local repository and read-only checks go, stopping at every action only the user may take.

Invoked as `/release` (suggest the version), `/release <version>` (`X.Y.Z`; a leading `v` is accepted, but the CHANGELOG heading uses `X.Y.Z` and the tag `vX.Y.Z`), `/release --check` (reconcile only: report the gaps in `[Unreleased]` and stop — no version, no promotion, no branch), or `/release continue` (resume after a checkpoint, for example in a new session: work out the phase from the state below).

The release itself stays a deliberate decision by the user — [ADR 0007](../../../docs/adr/0007-single-shared-semver-and-changelog.md) keeps it manual on purpose, and a release tag push is the only thing that publishes the Docker images ([ADR 0032](../../../docs/adr/0032-github-flow-main-only.md), F014). The skill runs the steps, including the push, PR and release creation, but each outward one only on the user's explicit confirmation (see Hard limits), and the PR merge is always the user's.

## Phases and checkpoints

| Phase | Steps | What the skill does | Then it waits for |
|---|---|---|---|
| 1. Draft | 1–6 | reconcile, version, rc recommendation, draft shown in chat | **approval** of version, draft and rc decision |
| 2. Release PR | 7 | worktree + branch, CHANGELOG promotion, version bumps, one commit, PR text; after **confirmation**, pushes the branch and opens the PR | **the user to merge the PR** and say it is merged |
| 3. Candidate | 8–9 | tags `vX.Y.Z-rc.N` locally (only when recommended); after **confirmation**, pushes it and verifies the published images | **the user to read the verification report** and say to go on |
| 4. Release | 10 | tags `vX.Y.Z` locally; after **confirmation**, pushes it, verifies the final images, and after a second **confirmation** runs `gh release create` | nothing: it reports and ends |

A checkpoint is a stop: end the turn with what was done, the exact commands the user runs, and what you are waiting for. Continue only on the user's reply in a later turn; a reply to an earlier checkpoint never covers a later one. Without an rc, Step 9's verification runs after the final tag (Step 10), as the runbook says.

State for resuming: the branch `chore/release-vX.Y.Z` (local, on origin, merged: `gh pr list --head chore/release-vX.Y.Z --state all`) and the local and remote tags `vX.Y.Z-rc.N` / `vX.Y.Z` (`git tag -l`, `git ls-remote --tags origin`) say which phase is next. Tell the user what you inferred before acting on it.

## Hard limits

Local git, `gh`, and docker for pulling and running the published images are all this skill uses. Allowed local writes, at the step that names them:

- a worktree and the branch `chore/release-vX.Y.Z` off `origin/main` (Step 7);
- `CHANGELOG.md`, `backend/build.gradle`, `frontend/package.json` and `frontend/package-lock.json` in that worktree, and nothing else (Step 7);
- one commit, `chore: release vX.Y.Z` (Step 7);
- with `--check` only, approved fixes to `CHANGELOG.md` in the current checkout, unstaged;
- local annotated tags `vX.Y.Z-rc.N` and `vX.Y.Z` on the release PR's squash commit (Steps 8 and 10).

**Outward actions need a confirmation each.** These are the only ones the skill performs, and only the exact command it showed:

1. `git push -u origin chore/release-vX.Y.Z` and `gh pr create` (Checkpoint A);
2. `git push origin vX.Y.Z-rc.N` (Checkpoint B);
3. `git push origin vX.Y.Z` (Step 10);
4. `gh release create` (Step 10).

The rule: show the command (with the title, body, sha or notes it will use) and ask; run it only when the user's next message says yes to it. An earlier approval, such as the Step 6 draft or the "go on" after a report, never covers a later action. If anything the command depends on changes after the yes (the sha, the notes, the title), ask again. A "no", silence or a question means do not run it. Do the actions one at a time and report each result (the PR URL, the pushed ref).

Never, under any circumstances or even on request in passing: `gh pr merge` or any other way of merging the release PR (the user merges it), `git push --force`, moving, deleting or force-replacing a tag (they are immutable: a bad one means the next `rc.N` or patch), committing to `main`, amending after a push, or pushing anything but the release branch and the release tags. Nothing this skill proposes or runs (commit message, PR title, PR body, tag message, release notes) may contain a skip-CI marker (see Step 7).

## Step 1 — Pick the source ref

Releases come from `main` ([ADR 0032](../../../docs/adr/0032-github-flow-main-only.md)): run `git fetch origin --tags` and work from `origin/main`, whatever branch is currently checked out. Read the CHANGELOG from there too (`git show origin/main:CHANGELOG.md`), not from the working tree, which may be another branch. Say which ref and commit you used. If local `main` differs from `origin/main`, say so and stick to `origin/main`.

## Step 2 — Find the last release

```
git tag -l 'v[0-9]*.[0-9]*.[0-9]*' --sort=-v:refname
```

- **Tags exist**: the last release is the first one listed. The range is `<tag>..origin/main`. A release tag sits on a squash commit that is an ancestor of `main` (ADR 0032), so `git describe` works too. If `git log origin/main..<tag>` is non-empty, the tag is not on `main` at all (the tag guard in CI refuses to publish such a tag): warn the user.
- **No tags**: this is the **first release**. Use the full history of `origin/main`, and default the version to `0.1.0` ([ADR 0007](../../../docs/adr/0007-single-shared-semver-and-changelog.md): the starting version) unless the user passed one. Nothing was ever released before, so there is no "since": every user-visible change is new.

If the user passed a version, check it is valid `X.Y.Z`, greater than the last tag, and — separately — say whether it matches the bump the commits imply (Step 4). Their choice wins; just don't let a mismatch pass silently.

## Step 3 — Read what's already there

Parse `## [Unreleased]` from `origin/main`'s CHANGELOG. For each bullet note: its section (`Added`, `Changed`, `Fixed`, …), its key (a feature id from `**FXXX — Title**`, or an issue from `closes [#N]`), its PR link(s), and any `Upgrade:` sub-line. If `[Unreleased]` is empty or still holds the old "pre-implementation" placeholder, the per-PR entries were never written: say so, and treat every user-visible commit in the range as a missing entry (Step 5a) — the draft then follows the `release.md` "CHANGELOG entries" format from scratch.

## Step 4 — List and classify the commits

```
git log <range> --first-parent --format='%h|%s'
```

`--first-parent` because `main` is squash-merged (no merge commits, so this normally lists everything) and it keeps any stray merge commit from duplicating commits. For each commit that could matter, read its body (`git show -s --format=%B <hash>`) for a `BREAKING CHANGE:` footer and `Closes #N`, and take the PR number from the `(#N)` suffix of the subject (older direct commits have none — leave the reference out rather than guess). A commit's key is its scope's feature id (`feat(F001,F014)` counts for both), else its PR number or closed issue.

Classify by type with the [ADR 0009](../../../docs/adr/0009-conventional-commits.md) mapping:

| Type | CHANGELOG section | SemVer bump |
|---|---|---|
| `feat` | Added (Changed if it extends existing behavior) | MINOR |
| `fix` | Fixed | PATCH |
| `!` or `BREAKING CHANGE:` footer | Changed, marked **BREAKING** | MAJOR |
| `docs` `chore` `ci` `build` `style` `test` `refactor` `perf` | none, unless user-visible | none |

Things to handle by judgment, and to tell the user about instead of deciding silently:

- **Non-conventional commits** (anything from before ADR 0009, e.g. "Add PRD, README…"): don't force a type onto them and don't let them affect the bump. List them once as "not classified".
- **`feat`/`fix` that only touch tooling or docs** (check `git show --name-only`: only `.claude/`, `docs/`, `.github/`, `CLAUDE.md`…) **or add only dev-time tooling** (a test runner, linter, formatter): not user-visible, so they need no entry — list dev-tooling ones as borderline rather than dropping them silently. Mention that the mechanical bump would count them, and what it would be without them.
- **`refactor`/`perf`** with a visible effect (a loading state, a speedup) need an entry under Changed. Borderline ones go in a "considered, left out" list so the user can pull them in.
- **Breaking change while the project is 0.x**: the ADR 0009 rule says MAJOR, but ADR 0007 reserves `1.0.0` for "stable end to end". Give the mechanical answer, flag the conflict, and let the user choose.

**Suggested bump**: from the highest-impact type among the user-visible commits (breaking → MAJOR, any `feat` → MINOR, only `fix` → PATCH). The commit types are the authority, not the section an entry sits in. None user-visible → say no release seems warranted and ask. For the first release, the suggestion is `0.1.0` regardless. Also compare `version` in `backend/build.gradle` and `frontend/package.json`: they must be equal to each other (ADR 0007); report both and whether either already equals the target version (it will for `0.1.0`).

## Step 5 — Reconcile `[Unreleased]` against the commits

Check each of these and collect the findings; propose a concrete fix for each, never apply one silently.

- **a. Missing entry**: a user-visible commit whose key matches no bullet (no feature id, PR link or issue in any bullet). Draft the bullet per the `release.md` "CHANGELOG entries" format: `**F004 — Transactions** — <one-line, user-visible summary> ([#6](…/pull/6))`, title from the first heading of `docs/features/FXXX-*/spec.md`, substance from its Summary plus the commit body; several PRs for one feature id are one bullet with all links. An unscoped fix/change is a plain bullet citing the issue (`closes [#N](…)`) and the PR. PR links use the repo URL from `gh repo view --json url -q .url`, and go under the section from the table above.
- **b. Missing PR link**: a bullet with a feature id or issue but no `([#N](…))`. Find the PR from the `(#N)` suffix of the matching commit and propose adding it. (PR links are filled here, at release time, not per PR, so a missing link is the normal case for recent entries.)
- **c. Stray entry**: a bullet whose feature id, issue or PR isn't in the range — already released, or never merged. Flag it; don't delete it.
- **d. Section mismatch**: a bullet under `Added` whose commit is `fix`, a `feat` filed under `Fixed`, a breaking change not marked **BREAKING**. Flag it. The bump still comes from commit types.
- **e. Missing upgrade note.** Look for changes that affect how someone runs or upgrades the stack and check the bullet has an `Upgrade:` sub-line — including in `chore`/`ci`/`build` commits that are otherwise left out:
  `git diff --stat <last-tag> origin/main -- 'docker-compose*.yml' .env.example 'backend/src/main/resources/application*.yml' 'backend/src/main/resources/db/migration' '**/Dockerfile' frontend/nginx.conf .github/workflows`
  (first release: nothing to diff against, so list the migrations, `.env.example` keys, compose volumes and services as they stand). Cover new or changed Docker volumes, env vars, compose services, config keys, image or nginx changes, and Flyway migrations (they run automatically on startup — say so, and note any that can fail on existing data, such as a new `UNIQUE` constraint). A note belongs only where a person running the stack has something to do or know; a new optional variable with a sane default is one line. On the **first release** there is no earlier version to upgrade from, so keep only notes that matter to someone with an existing dev/`latest` install.
- **f. Entry that shouldn't exist**: a bullet for a docs/chore/test/dev-tooling-only change. Flag it as a candidate to drop.
- **g. Release candidate.** Recommend an `-rc.N` (runbook step 3) when the range touches what only a real publish exercises: `git diff --stat <last-tag> origin/main -- .github/workflows '**/Dockerfile' 'docker-compose*.yml'` is non-empty (this is also "the first release after any of them changed"), or there is no earlier tag. Otherwise say it is optional and not recommended: the PR-time image build, scan and prod smoke cover the rest. Always give the reason, naming the files. The user decides.

If everything matches, say so plainly; don't invent findings.

**`/release --check` stops here**: report the findings from Step 5 with their proposed fixes, plus what's unreleased so far (commit counts by type since the last release), and end. No version, no promotion, no branch, no tags. Writing the fixes still needs the user's approval; they go into `CHANGELOG.md` in the current checkout only, unstaged and uncommitted, and `git diff CHANGELOG.md` is shown.

## Step 6 — Show the draft and wait

Reply in chat with, in this order:

1. **Version**: suggested (or the one the user passed), the last tag and range used, and why. Both `version` fields' current values.
2. **Reconciliation findings**, grouped a–g, each with the proposed fix. "No gaps" is a valid answer.
3. **The result**, as a fenced markdown block exactly as it would appear in `CHANGELOG.md` after the fixes and the promotion: a fresh empty `## [Unreleased]`, then `## [X.Y.Z] - YYYY-MM-DD` (today's date from `date +%F`; it must be the day the tag is pushed, so say so and let the user change it) holding the entries, and the link footer: `[Unreleased]` re-pointed to `compare/vX.Y.Z...HEAD` and a new `[X.Y.Z]: …/compare/v<previous>...vX.Y.Z` line above the older ones (the first release has no `compare` base: `…/releases/tag/vX.Y.Z`, as `[0.1.0]` does). Sections that have no content are omitted.
4. **Left out**: counts of commits by type, then the borderline commits and any non-conventional or tooling-only ones, each with the reason.
5. **Release candidate**: recommended or not, with the Step 5g reason, and that the user may flip it.
6. **Warnings**: breaking-while-0.x conflict, a version/bump mismatch, a tag that is not on `main`, a skip-CI marker (Step 7), anything that looked wrong.

Then ask for edits or approval of **the version, the draft and the rc decision**. Iterate until the user approves. **Write nothing before that**: the version is decided here, and it is what the branch is named after. Approval authorizes Step 7 and nothing past its checkpoint.

## Step 7 — Branch, bump, commit (phase 2)

Only after the Step 6 approval. The release is prepared in its own worktree so the user's checkout, whatever branch it is on and whatever it holds, is never switched or written. A dirty `CHANGELOG.md` in that checkout (an earlier draft, say) is not read and not touched: this step works from `origin/main`; mention it so the user can discard or keep it.

1. **Preconditions.** `git fetch origin --tags`. If `origin/main` moved since Step 1, list the new commits; if any is user-visible, redo Steps 4–5 for them and show the delta before going on. The branch must not exist: `git rev-parse --verify --quiet refs/heads/chore/release-vX.Y.Z` and `git ls-remote --heads origin chore/release-vX.Y.Z` both empty, and neither must the tag `vX.Y.Z`. If one exists, stop and ask (resume, or pick another version).
2. **Branch** (ADR 0032, never `main`): `git worktree add --no-track -b chore/release-vX.Y.Z ../my-finances-release-vX.Y.Z origin/main`, so the branch has no upstream until it is pushed at Checkpoint A. Everything below happens in that directory.
3. **CHANGELOG**: apply the approved fixes and the promotion exactly as shown in Step 6, footer included. Leave the file's header and earlier releases untouched.
4. **Version bump** to `X.Y.Z`, equal in all three (ADR 0007): the `version = '…'` line of `backend/build.gradle`, and `npm version X.Y.Z --no-git-tag-version` in `frontend/`, which rewrites `package.json` and `package-lock.json` (it fails with "Version not changed" when they already equal the target, as for `0.1.0`; then edit nothing for that file). No `npm install`: the worktree has no `node_modules` and the command does not need one. Then `bash scripts/verify.sh versions`.
5. **Scope check.** `git status --short` must list exactly `CHANGELOG.md`, `backend/build.gradle`, `frontend/package.json` and `frontend/package-lock.json` (fewer only when a version already matched). Anything else means something went wrong: stop and show it. `README.md`, `docs/operations.md` and `.env.example` hold no release version and are never edited.
6. **Skip-CI check** (below) on the commit message and the PR text before committing.
7. **Commit**: one commit, `chore: release vX.Y.Z` ([ADR 0009](../../../docs/adr/0009-conventional-commits.md)), no skip marker. The local pre-commit hook may run a format check; if it fails, fix what it names and commit again (nothing is pushed, so there is nothing to protect).
8. **PR text.** Load the `pr` skill when it is available and write the body to a file in the scratchpad directory (drafting only); the title is `chore: release vX.Y.Z`. Check both for a skip marker.

### Skip-CI check

A `vX.Y.Z` (or `vX.Y.Z-rc.N`) tag push is the only thing that triggers CI's `build-and-push` to publish images (ADR 0032, F014), and GitHub skips workflow runs entirely when the relevant commit message carries a marker such as `[skip ci]`, `[ci skip]`, `[no ci]`, `[skip actions]`, `[actions skip]` or a `skip-checks: true` trailer. A release that carries one silently publishes nothing. Check and report:

- the current `origin/main` tip (the release PR's base) and the release PR's head commit, which its PR runs key off; the squash commit that becomes the tagged commit takes its message from the PR title and body;
- the release commit and the PR title and body from this step — none may contain a marker;
- history: `git log <range> -i --grep='skip ci'` (and the other spellings) — informational only, since only the head/tag commit matters, but name any hit so the user knows the marker is there and the tag must not land on it.

If the tip carries one, tell the user to add a commit or reword before releasing. Never suggest `[skip ci]` in anything you propose here. Step 8 repeats the check on the real squash commit before any tag.

### Checkpoint A — the release PR

Report what the commit holds (`git show --stat`), then show the PR title, the body (from the file) and the two commands, and ask for confirmation to run them:

```
git -C ../my-finances-release-vX.Y.Z push -u origin chore/release-vX.Y.Z
gh pr create --base main --head chore/release-vX.Y.Z --title "chore: release vX.Y.Z" --body-file <path to the PR body>
```

On the yes, run them in that order and give the PR URL. Then say what to wait for before merging: the required checks, including `Build images and scan` and `Prod smoke`, which run because the version files match the `images` filter (scan locally with `bash scripts/verify.sh scan` only if the PR turns out docs-only), and that the merge is a **squash** (ADR 0032) with a title and body free of skip markers. The user merges. If the tag will be pushed on a different day than the CHANGELOG date, the date needs a docs-only PR first (runbook gotcha), so ask to be told about a delay.

Then stop and wait for the user to say the PR is merged. `gh pr view <N> --json state,mergeCommit` confirms it before Step 8 goes on.

## Step 8 — Tag the release candidate (phase 3)

On the user's "merged":

1. `git fetch origin --tags`, then find the squash commit: `gh pr list --head chore/release-vX.Y.Z --state merged --json number,mergeCommit` (or `gh pr view <N> --json mergeCommit -q .mergeCommit.oid`). Call it `<sha>`. Never use "whatever `main` is now": merges that landed in between must not leak in.
2. Check the commit and report each result: `git merge-base --is-ancestor <sha> origin/main` (the tag guard needs it); `git show <sha>:backend/build.gradle`, `:frontend/package.json` and `:frontend/package-lock.json` carry `X.Y.Z`; `git show <sha>:CHANGELOG.md` has `## [X.Y.Z] - <date>` and no missing PR links; the full message (`git show -s --format=%B <sha>`) has no skip-CI marker; `gh run list --commit <sha>` shows the checks green. Any failure: stop and say what it implies (a bad commit is fixed by an ordinary PR into `main`, and `<sha>` becomes that PR's squash commit).
3. **No rc recommended** (Step 5g, as the user left it): say so and go to Step 10.
4. **rc recommended**: the number is one above the highest existing `vX.Y.Z-rc.N` (`git tag -l 'vX.Y.Z-rc.*'`), `1` when none. Create it locally on `<sha>`, annotated like the released tags: `git tag -a vX.Y.Z-rc.N -m vX.Y.Z-rc.N <sha>`, and confirm with `git rev-parse vX.Y.Z-rc.N^{commit}`. It publishes `vX.Y.Z-rc.N` and `sha-<short>` and never moves `latest`.

### Checkpoint B — push the rc tag

Show the tag, its commit and the command, and ask for confirmation:

```
git push origin vX.Y.Z-rc.N
```

On the yes, push it and go straight to Step 9. Without one, stop.

## Step 9 — Verify the published images (phase 3, or phase 4 without an rc)

Runs against the tag just pushed, `<T>`. Everything that can be checked from a shell is checked here; what cannot is listed at the end for the user. Never run it where a real prod stack exists: `docker-compose.prod.yml` hardcodes container and volume names, and `scripts/smoke-prod.sh` refuses to start beside them. Check `docker ps -a --filter name=my-finances` first and ask if anything shows up.

1. **The tag run.** `gh run list --workflow ci.yml --branch <T> --json databaseId,headSha,status,conclusion`; the run's `headSha` must be the tagged commit. Wait with `gh run watch <id> --exit-status` (long: run it in the background). A failure: report the failing job and stop. A transient failure (registry or network blip) is re-run from the Actions UI by the user; a workflow, Dockerfile or scan failure burns the tag, so the next candidate is `rc.N+1` (or the next patch) after an ordinary fix PR into `main` (runbook, "A publish-only failure burns the version").
2. **Tags and digests, anonymously.** Use `export DOCKER_CONFIG=$(mktemp -d)` for these commands only, so the user's own `docker login` is neither used nor removed. For each of `backend`, `frontend` and `backup` (`ghcr.io/hmcaio/my-finances-<name>`): `docker buildx imagetools inspect <image>:<T>` and `<image>:sha-<short>` must both resolve, with the same `Digest:`. A failure to resolve means the GHCR package is private or missing. For a stable tag, `:latest` must have the same digest as `:vX.Y.Z`; for an rc, `:latest` must **not** have moved (it still equals the previous release's digest, or does not exist before the first release).
3. **Fresh-install smoke on the published images.** `docker pull` the three `<image>:<T>` with that same empty `DOCKER_CONFIG`, then from a checkout of the tag (`git worktree add --detach ../my-finances-release-verify <T>`, removed afterwards with `git worktree remove`) run `SMOKE_BACKEND_IMAGE=… SMOKE_FRONTEND_IMAGE=… SMOKE_BACKUP_IMAGE=… bash scripts/verify.sh smoke` with those refs. It asserts all four services healthy, ports on loopback only, the login and a wrong password, nginx's header and cache matrix, the backup status and a clean backend log, and tears its throwaway stack down itself. Its output is the evidence for the runbook lines it covers (healthy, loopback ports, login, logging hygiene on the smoke's own data).
4. **Cleanup.** Remove the verify worktree and the scratch `DOCKER_CONFIG`; confirm `docker ps -a --filter name=my-finances` shows nothing the run left behind.

### Report

A table with one row per check (tag run, three images × tags, digests, `latest`, smoke), each pass or fail with the evidence (run URL, digests, smoke summary line). Then the runbook items this skill **cannot** do, for the user: one UI flow end to end (an account and a transaction, and the backup banner clearing after the first backup), the restore drill (wrong key, corrupt file, good restore) and, from the second release on, the upgrade test from the previous tag ([`docs/conventions/release.md`](../../../docs/conventions/release.md), "Verifying the published images"), each against `<T>`'s images. Say plainly what failed or was not run; do not call the candidate good on partial evidence.

For an rc, this is **Checkpoint C**: stop and wait for the user to confirm the report (and that their manual checks, if any, passed). A defect found during the rc is an ordinary fix PR into `main` with its CHANGELOG bullet under the existing `## [X.Y.Z]` section (re-dated to the day of the final tag), then `rc.N+1` and the final tag on that later commit; on "fixed and merged", repeat from Step 8 with that PR's squash commit.

## Step 10 — Tag the release (phase 4)

On the user's go-ahead after Checkpoint C (or straight from Step 8 when no rc was recommended):

1. `<sha>` is the release PR's squash commit (after an rc fix cycle, the last fix's squash commit), never the current `main`. Repeat the Step 8 checks on it, plus: the CHANGELOG date equals `date +%F` (it is the day the tag is pushed; if not, stop: a docs-only PR into `main` fixes the date and the tag goes on that later commit), and `vX.Y.Z` does not exist locally or on origin.
2. Create the tag locally, annotated: `git tag -a vX.Y.Z -m vX.Y.Z <sha>`; confirm with `git rev-parse vX.Y.Z^{commit}`. Its message has no skip marker.
3. Write the release notes to a file in the scratchpad directory: the body of the `## [X.Y.Z]` CHANGELOG section (intro paragraph and the subsections, up to the next `## `), as the earlier releases do. The release is titled `vX.Y.Z`.
4. Show the tag, its commit, the notes file's contents and the command, and ask for confirmation to push it:

```
git push origin vX.Y.Z
```

On the yes, push it and run Step 9 against `vX.Y.Z` (the tag run, all three images carry `vX.Y.Z` and `sha-<short>`, `latest` has the same digest as `vX.Y.Z`, the smoke) and report. Without a yes, stop.

5. **GitHub release.** Only when that report is clean (a failed run or check means no release: say what it implies). Show the command and the notes and ask for confirmation again, as the push yes does not cover it:

```
gh release create vX.Y.Z --verify-tag --latest --title vX.Y.Z --notes-file <absolute path of the notes file>
```

On the yes, run it and give the release URL.

Last, list what remains for the user: the manual checks of Step 9's report when there was no rc, pinning `IMAGE_TAG=vX.Y.Z` in the prod `.env` for a reproducible deploy, and ticking any plan item the release proved (for the first release, the F014 publish item). Offer to remove the release worktree (`git worktree remove ../my-finances-release-vX.Y.Z`) once the user is done with it; do not remove it unasked.
