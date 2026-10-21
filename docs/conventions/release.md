# Cutting a release

Read when a change is user-visible (its `CHANGELOG.md` entry, [below](#changelog-entries)), when promoting `develop` to `main` or when tagging a version. The decisions are in [ADR 0007](../adr/0007-single-shared-semver-and-changelog.md) (one SemVer, manual process), [ADR 0008](../adr/0008-github-flow-with-develop-branch.md) (branches, merge methods, rulesets) and [ADR 0015](../adr/0015-automated-encrypted-backups-sidecar.md) (the forced `pre-upgrade` backup); the `release` skill (`.claude/skills/release/SKILL.md`) drafts the CHANGELOG promotion. This file is the order of steps as run for v0.1.0, with the checks that proved the published images. A hotfix follows ADR 0008 only. Only the person with push rights pushes tags.

## CHANGELOG entries

Every PR with a user-visible change adds its bullet under `## [Unreleased]` in `CHANGELOG.md`, in the same commit as the code, so the release only reconciles and promotes what is already there ([ADR 0007](../adr/0007-single-shared-semver-and-changelog.md)).

- **Which changes.** `feat`, `fix`, a breaking change, and a `refactor`/`perf` that users would notice. `docs`, `chore`, `test`, `ci`, `build`, `style` and internal refactors need no entry; say so in the PR or the closing summary.
- **Format.** One bullet per feature id: `**F004 — Transactions** — <user-visible summary>`, the title from the feature's `spec.md` heading. A change with no feature id is a plain bullet citing its issue: `closes [#N](…)`, or `part of [#N](…)` when the issue tracks several PRs.
- **Section.** Keep a Changelog order: Added, Changed, Deprecated, Removed, Fixed, Security (as in the 0.1.0 and 0.1.1 sections). A breaking change goes under Changed, marked **BREAKING**.
- **`Upgrade:` sub-line.** A nested `- Upgrade: …` line when the change alters how someone runs or upgrades the stack: compose files, volumes, env vars, config keys, migrations (say when one can fail on existing data).
- **PR links.** The PR number does not exist while the entry is written, and a link added afterwards would cost a commit and a push per PR. The release step fills every missing `([#N](…))` from the merged PRs (the `release` skill's reconcile, step 5b in `.claude/skills/release/SKILL.md`), so entries are merged without one.
- **Release time.** The version bump, renaming `[Unreleased]` and tagging happen when releasing (below, [ADR 0007](../adr/0007-single-shared-semver-and-changelog.md)); the SemVer bump is read off the commit types ([ADR 0009](../adr/0009-conventional-commits.md)), not off the section an entry sits in.

## Steps

- [ ] **1. Freeze scope.** No feature PR merges into `develop` until the release is tagged. Dependabot and version bumps wait until after it.
- [ ] **2. Promote the CHANGELOG on `develop`** (PR from `chore/release-vX.Y.Z`, squash-merged). Fill every missing `([#N](…))` PR link from the merged PRs (the `release` skill's reconcile does it), rename `[Unreleased]` to `## [X.Y.Z] - <date>` with a fresh empty `[Unreleased]` above it; the date is the day the tag will be pushed. Set `version` to `X.Y.Z` in `backend/build.gradle`, `frontend/package.json` and `frontend/package-lock.json` (equal, ADR 0007; already so for 0.1.0; `npm version X.Y.Z --no-git-tag-version` in `frontend/` does both npm files), and update the release-tag references (`vX.Y.Z`) in `README.md` and `.env.example`. A CHANGELOG-only PR is docs-only and skips the test jobs; touching the version files runs them.
- [ ] **2b. Scan the images before promoting** (`bash scripts/verify.sh scan` on `develop`, [tooling.md](tooling.md#image-vulnerability-scan-adr-0031)). The scan gates `build-and-push`, so a fixable HIGH/CRITICAL finding discovered on `main` publishes nothing and blocks the release; clear it first (base image or dependency bump, or a dated `.trivyignore` entry) on `develop`.
- [ ] **3. PR `develop` to `main`, merged with a merge commit.** Never squash (`protect-main` allows `merge` only). The merge push publishes `:latest` and `:sha-<short>` for all three images, if the scan passes.
- [ ] **4. Merge `main` back into `develop`** (PR `main` to `develop`, merge commit), after every promotion. `protect-main` has `strict_required_status_checks_policy: true` and the promotion's merge commit leaves `main` ahead of `develop`, so without this the next promotion shows "out-of-date" and is blocked. A failed or skipped "Build and push images" on that PR is `main`'s own earlier commit status, not new: the four required checks decide.
- [ ] **5. Optional release candidate**: tag `vX.Y.Z-rc.N` on the `main` HEAD. Tags are immutable (`protect-release-tags` forbids delete, move and update), so a bad candidate means `rc.N+1`, never a retag.
- [ ] **6. Verify the published images** (below), from a clean directory.
- [ ] **7. Tag `vX.Y.Z` on the `main` HEAD** and push it. Wait for the tag run; check all three images (`backend`, `frontend`, `backup`) carry `vX.Y.Z` and that `latest` has the same digest. The tagged commit's message must not carry a skip-CI marker.
- [ ] **8. GitHub release** with the notes taken from the CHANGELOG section: `gh release create vX.Y.Z --verify-tag --latest`.
- [ ] **9. Docs**: tick any plan item the release proved (F014 for the first one), then reopen `develop` to dependabot and feature work.

## Verifying the published images

Work in an empty directory holding only `docker-compose.prod.yml` and `.env.example` (from the tag, as in the README's [Running the published images](../../README.md#running-the-published-images-production)), not a checkout, so nothing local leaks in. The prod compose hardcodes `container_name`s and the `my-finances-logs-prod` volume, so use a unique `-p` project name, a scratch `BACKUP_DIR` and tear everything down afterwards; do not run it beside a stack you care about.

- [ ] Anonymous pull of the three images works (set `DOCKER_CONFIG` to an empty scratch directory for the pull, so your own `docker login` is neither used nor removed); `up -d` reaches healthy.
- [ ] Published ports are bound to `127.0.0.1` only.
- [ ] Login works, one UI flow end to end (create an account, a transaction), and the backup banner shows nothing once the first backup is in.
- [ ] Restore drill (README, [Backups](../../README.md#backups)): a wrong key and a corrupt file fail before the database is touched; a good restore brings the data back.
- [ ] Logging hygiene: grep `docker compose logs` and the `my-finances-logs-prod` volume for the test amounts, descriptions and names; none may appear (ADR 0011, [logging.md](logging.md)).
- [ ] From the second release on, the upgrade test from the previous tag: a forced `pre-upgrade` backup (`decision: forced`, file `myfinances-<ts>.pre-upgrade.dump.age`) lands before the backend restarts; Flyway reports "up to date" or migrates; the data is byte-identical; rollback works (restore the `pre-upgrade` backup, run the old tag).

## Gotchas hit in 0.1.0

- **`backend/gradlew` exec bit.** The first `main` push failed in the backend image build: `build-and-push` runs only on `main`/tags and, unlike the test job, did not `chmod` the wrapper. Fixed in #160 (mode 100755 and `COPY --chmod=755`); details in [tooling.md](tooling.md). A change to `backend/Dockerfile` or the wrapper is not proven by the PR's checks.
- **A failing scan blocks the publish (ADR 0031).** `build-and-push` scans before it pushes, so a red scan on the `main` push means no images for that commit, and the tag push fails the same way. Fix it before tagging, even though step 1 freezes version bumps: the fix is a bump (or a dated `.trivyignore` entry) PR into `develop`, then promote again (step 3) and merge back (step 4). Tags are immutable (`protect-release-tags`), so tag only a `main` commit whose "Build and push images" run is green, and re-check the CHANGELOG date if the delay crosses a day.
- **Merge-back is mandatory** (step 4): the strict-checks ruleset turns a forgotten one into a blocked next release.
- **GHCR packages are public**: confirm anonymous pull (step 6) each release, since a package created private would break the README's no-login quickstart.
- **The CHANGELOG date must match the tag day.** Date it when you are about to tag; if the tag slips a day, fix the date in a docs-only PR before tagging (after the tag, the tagged commit keeps the wrong date for good).
