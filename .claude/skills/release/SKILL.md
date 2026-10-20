---
name: release
description: Prepares a my-finances release from the CHANGELOG.md that PRs keep current. Reconciles the `[Unreleased]` section on develop against the Conventional Commits merged since the last vX.Y.Z tag (or the full history for the first release, 0.1.0) — missing entries, missing PR links, entries in the wrong section, missing upgrade notes — suggests the SemVer bump from commit types (ADR 0009), and drafts the promotion of `[Unreleased]` to `## [X.Y.Z] - date`. Also lists the manual release steps that remain (ADR 0007/0008, F014). Draft only — it never bumps versions in backend/build.gradle or frontend/package.json, never tags, pushes or opens a PR, and asks before writing CHANGELOG.md or creating any branch or commit. Use this whenever the user asks to prepare, draft, cut, check or plan a release, a version bump, release notes, "what's unreleased" or whether the CHANGELOG is up to date, or invokes /release [version], even if they don't say "changelog". Not for building a feature (implement-feature) or fixing an audit finding (audit-and-fix) — those two write the per-PR entries this skill checks.
---

# Release

Feature and fix PRs add their own `CHANGELOG.md` entry under `## [Unreleased]` (root `CLAUDE.md`, "CHANGELOG entries ship with the PR"), so at release time the entry mostly exists already. This skill checks it against what actually merged, fixes the gaps, and drafts the promotion to a versioned section for the user to edit and approve.

Invoked as `/release` (suggest the version), `/release <version>` (e.g. `/release 0.2.0`; a leading `v` is accepted, but the CHANGELOG heading uses `0.2.0` and the tag `v0.2.0`), or `/release --check` (reconcile only: report the gaps in `[Unreleased]` and stop — no version, no promotion — for keeping `develop` current between releases).

This skill **drafts**. The release itself (version bump, tag, push, PRs) is a deliberate manual action by the user — [ADR 0007](../../../docs/adr/0007-single-shared-semver-and-changelog.md) keeps it manual on purpose, and a tag push is what publishes the Docker images (F014).

## Hard limits

Read-only git/gh only (`fetch`, `log`, `show`, `diff`, `tag -l`, `rev-parse`, `gh repo view`). Never, under any circumstances or on the user's behalf:

- edit `version` in `backend/build.gradle` or `frontend/package.json`;
- `git tag`, `git push`, `gh pr create` or anything else that publishes;
- create a branch or a commit without asking first — and even then only what the user names. Nothing this skill proposes (commit message, PR title, PR body) may contain a skip-CI marker (see Step 7).

The only file it may write is `CHANGELOG.md`, and only after the user has approved the draft (Step 6).

## Step 1 — Pick the source ref

Releases come from `develop` ([ADR 0008](../../../docs/adr/0008-github-flow-with-develop-branch.md)): run `git fetch origin --tags` and work from `origin/develop`, whatever branch is currently checked out. Read the CHANGELOG from there too (`git show origin/develop:CHANGELOG.md`), not from the working tree, which may be another branch. Say which ref and commit you used. If local `develop` differs from `origin/develop`, say so and stick to `origin/develop`.

## Step 2 — Find the last release

```
git tag -l 'v[0-9]*.[0-9]*.[0-9]*' --sort=-v:refname
```

- **Tags exist**: the last release is the first one listed. The range is `<tag>..origin/develop`. Don't use `git describe` from `develop`: a release is a merge commit on `main` (ADR 0008), so the tag is not necessarily an ancestor of `develop`, but its second parent is — the `<tag>..origin/develop` range still comes out right. If `git log origin/develop..<tag>` is non-empty, the tag carries commits `develop` never got (a hotfix that wasn't merged back); warn the user, because those fixes are missing from the next `develop` → `main` promotion, and expect a CHANGELOG conflict when they are merged back.
- **No tags**: this is the **first release**. Use the full history of `origin/develop`, and default the version to `0.1.0` ([ADR 0007](../../../docs/adr/0007-single-shared-semver-and-changelog.md): the starting version) unless the user passed one. Nothing was ever released before, so there is no "since": every user-visible change is new.

If the user passed a version, check it is valid `X.Y.Z`, greater than the last tag, and — separately — say whether it matches the bump the commits imply (Step 4). Their choice wins; just don't let a mismatch pass silently.

## Step 3 — Read what's already there

Parse `## [Unreleased]` from `origin/develop`'s CHANGELOG. For each bullet note: its section (`Added`, `Changed`, `Fixed`, …), its key (a feature id from `**FXXX — Title**`, or an issue from `closes [#N]`), its PR link(s), and any `Upgrade:` sub-line. If `[Unreleased]` is empty or still holds the old "pre-implementation" placeholder, the per-PR entries were never written: say so, and treat every user-visible commit in the range as a missing entry (Step 5a) — the draft then follows the root `CLAUDE.md` format from scratch.

## Step 4 — List and classify the commits

```
git log <range> --first-parent --format='%h|%s'
```

`--first-parent` because `develop` is squash-merged (no merge commits, so this normally lists everything) and it keeps a merged-back `main` from duplicating commits. For each commit that could matter, read its body (`git show -s --format=%B <hash>`) for a `BREAKING CHANGE:` footer and `Closes #N`, and take the PR number from the `(#N)` suffix of the subject (older direct commits have none — leave the reference out rather than guess). A commit's key is its scope's feature id (`feat(F001,F014)` counts for both), else its PR number or closed issue.

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

- **a. Missing entry**: a user-visible commit whose key matches no bullet (no feature id, PR link or issue in any bullet). Draft the bullet per the root `CLAUDE.md` format: `**F004 — Transactions** — <one-line, user-visible summary> ([#6](…/pull/6))`, title from the first heading of `docs/features/FXXX-*/spec.md`, substance from its Summary plus the commit body; several PRs for one feature id are one bullet with all links. An unscoped fix/change is a plain bullet citing the issue (`closes [#N](…)`) and the PR. PR links use the repo URL from `gh repo view --json url -q .url`, and go under the section from the table above.
- **b. Missing PR link**: a bullet with a feature id or issue but no `([#N](…))`. Find the PR from the `(#N)` suffix of the matching commit and propose adding it. (Entries are written before their PR exists, so this is the normal case for recent ones.)
- **c. Stray entry**: a bullet whose feature id, issue or PR isn't in the range — already released, or never merged. Flag it; don't delete it.
- **d. Section mismatch**: a bullet under `Added` whose commit is `fix`, a `feat` filed under `Fixed`, a breaking change not marked **BREAKING**. Flag it. The bump still comes from commit types.
- **e. Missing upgrade note.** Look for changes that affect how someone runs or upgrades the stack and check the bullet has an `Upgrade:` sub-line — including in `chore`/`ci`/`build` commits that are otherwise left out:
  `git diff --stat <last-tag> origin/develop -- 'docker-compose*.yml' .env.example 'backend/src/main/resources/application*.yml' 'backend/src/main/resources/db/migration' '**/Dockerfile' frontend/nginx.conf .github/workflows`
  (first release: nothing to diff against, so list the migrations, `.env.example` keys, compose volumes and services as they stand). Cover new or changed Docker volumes, env vars, compose services, config keys, image or nginx changes, and Flyway migrations (they run automatically on startup — say so, and note any that can fail on existing data, such as a new `UNIQUE` constraint). A note belongs only where a person running the stack has something to do or know; a new optional variable with a sane default is one line. On the **first release** there is no earlier version to upgrade from, so keep only notes that matter to someone with an existing dev/`latest` install.
- **f. Entry that shouldn't exist**: a bullet for a docs/chore/test/dev-tooling-only change. Flag it as a candidate to drop.

If everything matches, say so plainly; don't invent findings.

**`/release --check` stops here**: report the findings from Step 5 with their proposed fixes, plus what's unreleased so far (commit counts by type since the last release), and end. No version, no promotion, no manual-steps list. Writing the fixes still needs the user's approval and follows Step 6's rules.

## Step 6 — Show the draft and wait

Reply in chat with, in this order:

1. **Version**: suggested (or the one the user passed), the last tag and range used, and why. Both `version` fields' current values.
2. **Reconciliation findings**, grouped a–f, each with the proposed fix. "No gaps" is a valid answer.
3. **The result**, as a fenced markdown block exactly as it would appear in `CHANGELOG.md` after the fixes and the promotion: a fresh empty `## [Unreleased]`, then `## [X.Y.Z] - YYYY-MM-DD` (today's date from `date +%F`; the user can change it to the actual release day) holding the entries. Sections that have no content are omitted.
4. **Left out**: counts of commits by type, then the borderline commits and any non-conventional or tooling-only ones, each with the reason.
5. **Warnings**: breaking-while-0.x conflict, a version/bump mismatch, an unmerged hotfix, a skip-CI marker (Step 7), anything that looked wrong.

Then ask for edits or approval. Iterate until the user approves. **Do not write `CHANGELOG.md` before that.** On approval, edit only `CHANGELOG.md`: apply the approved fixes to the `[Unreleased]` entries, then rename it to `## [X.Y.Z] - date` with a fresh empty `## [Unreleased]` above it, leaving the file's header and earlier releases untouched. Show `git diff CHANGELOG.md`. Don't stage or commit it. If the checked-out branch isn't one the user expects for this (`develop` or a release branch), say so before writing.

## Step 7 — Skip-CI check

The `vX.Y.Z` tag push is what triggers CI's `build-and-push` to publish the version-pinned images (F014), and GitHub skips workflow runs entirely when the relevant commit message carries a marker such as `[skip ci]`, `[ci skip]`, `[no ci]`, `[skip actions]`, `[actions skip]` or a `skip-checks: true` trailer. A release that carries one silently publishes nothing. Check and report:

- the current `origin/develop` tip (it becomes the head commit of the `develop` → `main` PR, and PR runs key off it);
- the release commit you propose, and the PR titles and bodies you propose — none may contain a marker;
- history: `git log <range> -i --grep='skip ci'` (and the other spellings) — informational only, since only the head/tag commit matters, but name any hit so the user knows the marker is there and the tag must not land on it.

If the tip carries one, tell the user to add a commit or reword before releasing. Never suggest `[skip ci]` in anything you propose here.

## Step 8 — Remaining manual steps

End by listing what is left for the user, in this order (from ADR 0007, ADR 0008 and F014's Release Process; those documents describe the steps but leave the exact sequencing loose, so this is the order that keeps the tag on a commit that contains the CHANGELOG and matching versions):

1. Branch `chore/release-vX.Y.Z` off an up-to-date `develop` (ADR 0008; never commit to `develop` or `main` directly).
2. Apply the approved CHANGELOG changes (already in the working tree if Step 6 wrote them) **and** set `version` to `X.Y.Z` in both `backend/build.gradle` and `frontend/package.json` — equal, per ADR 0007.
3. One commit, e.g. `chore(release): vX.Y.Z` (ADR 0009) — no skip-CI marker. PR into `develop`, squash-merge.
4. PR `develop` → `main` with a **merge commit**, not squashed (ADR 0008). Its merge triggers `build-and-push` for the `:latest` / `:<sha>` images. Keep skip markers out of the PR title/body too.
5. Tag `vX.Y.Z` on the resulting `main` commit and push the tag; that publishes the `:vX.Y.Z` images. Confirm the tagged commit's message has no skip marker first.
6. Check the CI run and that the images exist in GHCR; pin `IMAGE_TAG=vX.Y.Z` in the prod `.env` for a reproducible deploy.
7. On the **first release**, also tick the one open F014 plan item ("confirm CI publishes `:latest` and `:<sha>` … and the version tag") in `docs/features/F014-*/plan.md`, once verified — it could not be checked before the first `main` push.

Offer, without doing, to create the branch and commit ("say the word and I'll create `chore/release-vX.Y.Z` and commit the CHANGELOG"). Note that ADR 0007 wants the CHANGELOG and the version bump in one commit, so a commit made by this skill would hold only the CHANGELOG unless the user does the bump first.
