# 0032. Plain GitHub Flow: `main` is the only long-lived branch, and images publish on release tags only

Status: Proposed (supersedes [0008](0008-github-flow-with-develop-branch.md); amends the publish trigger of [0006](0006-separate-prod-packaging-from-dev.md) and [0031](0031-image-vulnerability-scan-gates-publishing.md)). Becomes Accepted when the owner merges this PR; the cutover then follows the [Migration plan](#migration-plan).
Date: 2026-10-21

## Context
[ADR 0008](0008-github-flow-with-develop-branch.md) added a long-lived `develop` branch to plain GitHub Flow so that several features could be batched into a deliberate release, and so that cross-dependent features had an integration branch. Its own last consequence flagged the risk: "revisit if the extra branch proves to be ceremony without payoff". The first two releases (0.1.0 and 0.1.1, both on 2026-10-20) are that evidence:

- **Eight ceremony PRs for two releases.** Every release needs a `develop` to `main` promotion PR (merge commit) and a `main` to `develop` merge-back PR. 0.1.0 took three promotions (#148, #161, #166) and three merge-backs (#162, #164, #167); 0.1.1 took one of each (#183, #186). None adds code of its own beyond promoting what already merged; each waits on the four required checks. The merge-back exists only because `protect-main` sets `strict_required_status_checks_policy: true`: a merge-commit promotion leaves `main` ahead of `develop`, so without the merge-back the next promotion reads "out-of-date" and is blocked (`docs/conventions/release.md`, step 4).
- **Issues close late.** GitHub closes `closes #N` issues only when the PR merges into the default branch (`main`). #181 and #169 were fixed by #188 (merged into `develop` 2026-10-21 01:21Z; the issue timeline shows only a `referenced` event) and stayed open until closed by hand at 02:25Z.
- **A publish failure needed another cycle.** The first `main` push failed in `build-and-push` (the `gradlew` exec bit, fixed in #160). The fix went through `develop` and a second promotion (#161) plus its merge-back (#162), because the image build is gated to `main` and tags and so is not exercised by the PR that changes it.
- **Dependabot targets `develop`** (`target-branch` on all six update blocks), so every bump lands one promotion away from the branch that releases, and its configuration is read from the default branch (`main`), not from `develop`.

ADR 0008's rationale was batching (a long-lived `develop` "fits 'batch several features into a deliberate release' better than 'every merge to main is a release'") and an integration branch for cross-dependent features. Both are weak here. The project is solo and every PR is squash-merged, so batching is already achieved by choosing when to tag: a release is a tag plus a release PR, not a branch promotion. Feature plans declare their `Dependencies` and are built in dependency order, so each PR already lands on a base that holds its prerequisites; `main` provides that as well as `develop` did. Nothing is continuously deployed, but that is an argument for tagging deliberately, not for a second branch. The ceremony cost recurs on every release; the benefit has not been used.

## Decision

1. **Branches.** `main` is the only long-lived branch. Feature, fix, docs and chore branches are short-lived, branch off `main`, and are PR'd into `main`, squash-merged with a Conventional Commit message ([ADR 0009](0009-conventional-commits.md)) and deleted. There is no `develop`. A hotfix is an ordinary fix PR into `main`, followed by a patch release (rule 3); there is no `hotfix/` flow and no merge-back. Nothing is committed to `main` directly.

2. **Publishing.** A push to `main` runs the test jobs and publishes nothing. The `build-and-push` job runs only on `vX.Y.Z` and `vX.Y.Z-rc.N` tag pushes: it builds the three images, scans them ([ADR 0031](0031-image-vulnerability-scan-gates-publishing.md)'s gate, unchanged), then pushes. A stable tag publishes `vX.Y.Z`, `sha-<short>` and `latest`; a release-candidate tag publishes `vX.Y.Z-rc.N` and `sha-<short>` and never moves `latest`. So `latest` always means the newest release, never an unreleased merge. The `sha-<short>` tag names the tagged commit.

   Publish-on-`main` was evaluated and rejected (see [Alternatives](#alternatives-considered)): nothing consumes images of unreleased commits, and it is the reason `latest` is ambiguous. The one real risk of tag-only publishing is that the first full publish run happens on an immutable tag (`protect-release-tags` forbids delete and move), so a publish-only failure burns that version, as the exec-bit failure would have. Rule 4 moves that failure class to pull-request time, and a `vX.Y.Z-rc.N` tag runs the identical publish pipeline as a rehearsal.

3. **Releases.** A release is a PR `chore/release-vX.Y.Z` into `main`, squash-merged, that holds the CHANGELOG promotion (`[Unreleased]` becomes `## [X.Y.Z] - <date>` under a fresh `[Unreleased]`), `version` set to `X.Y.Z` in `backend/build.gradle`, `frontend/package.json` and `frontend/package-lock.json` ([ADR 0007](0007-single-shared-semver-and-changelog.md)), and the `vX.Y.Z` references in `README.md` and `.env.example`. After it merges, the owner tags **the release PR's squash commit** (not whatever `main` is by then, so merges that land in between do not leak in), pushes the tag, waits for the tag run, and publishes the GitHub release (`gh release create vX.Y.Z --verify-tag --latest`). Only the owner pushes tags; `protect-release-tags` stays unchanged. Release candidates `vX.Y.Z-rc.N` are unchanged: optional, immutable, a bad one means `rc.N+1`. There is no scope freeze: the tag pins the commit.

4. **Verification before tagging moves to pull-request time and the tag run.** Two new jobs, specified and built as follow-up PRs, not by this ADR's cutover ([Follow-up work](#follow-up-work-not-part-of-this-adrs-cutover)):
   - `build-images` on pull requests, path-filtered to `backend/**`, `frontend/**`, `backup/**`, `docker-compose*.yml`, `.github/workflows/**` and `scripts/trivy/**`: builds the three images without pushing and runs the scan gate (`bash scripts/verify.sh scan`). The filter goes through the `changes` job (a second output, `images`) and a job-level `if:`, never `paths:` or `paths-ignore`, so a required check that is skipped still counts as passed (`docs/conventions/tooling.md`).
   - a prod smoke job that starts `docker-compose.prod.yml` against the images just built, with a throwaway `.env`, and checks that every service is healthy, that login works, the nginx security-header matrix (`docs/conventions/tooling.md`) and `/api/backup-status`. It runs on pull requests behind `build-images` and again in the tag run before the push steps, so a failing smoke blocks publishing.
   - at release time, the upgrade test from the previous tag (forced `pre-upgrade` backup, migrations, data intact, rollback), kept in `docs/conventions/release.md` and run against the `-rc.N` images.

5. **Rulesets** (files under `.github/rulesets/`, applied by the owner with `scripts/apply-rulesets.sh`):
   - `protect-develop.json` is deleted from the repo, and the `protect-develop` ruleset (id 25432305 at the time of writing) is deleted on GitHub by hand: the script only upserts, it never removes a ruleset.
   - `protect-main.json` changes in exactly one place: `allowed_merge_methods` goes from `["merge"]` to `["squash"]`. It keeps the pull-request rule (0 approvals, thread resolution required, admin bypass only through a PR), `deletion`, `non_fast_forward`, the four required checks (`Backend tests`, `Frontend tests`, `Frontend e2e (Playwright)`, `Backup sidecar tests (F018)`) and `strict_required_status_checks_policy: true`, which now only means "the branch is current with `main`" and no longer forces a merge-back. `merge` is not kept: its only users were the promotion and the merge-back, both gone, and keeping it would let a merge commit into `main`, which breaks the linear history the release skill's `git log --first-parent` and `git describe` rely on. `rebase` is already off in the ruleset. The `build-images` and smoke job names are added to `required_status_checks` in the PR that introduces each job, so the required name and the job ship together.
   - `protect-release-tags.json` is unchanged.

6. **Dependabot.** The six `target-branch: develop` lines in `.github/dependabot.yml` are deleted, so updates target the default branch (`main`). Dropping the line is preferred to writing `target-branch: main`: it leaves one fewer branch name to keep in sync. Dependabot closes the open PRs that target `develop` and opens new ones against `main` once the file reaches `main`; `@dependabot recreate` forces it.

Unchanged: [ADR 0007](0007-single-shared-semver-and-changelog.md) (one SemVer, manual release; tags and CHANGELOG still drive it) and [ADR 0009](0009-conventional-commits.md) (the squash message is now the commit that lands on `main`).

## Migration plan

The order keeps a valid target branch for every open and new PR at each moment, and puts the owner's admin actions after the content they depend on. `develop` still exists through step 5.

| # | Step | Who |
|---|---|---|
| 0 | Settle open PRs: merge or close #190 (it edits `CLAUDE.md`, which the cutover also edits); leave Dependabot #184 and #185 open or close them (they are recreated against `main` after step 3). | owner |
| 1 | Merge this ADR PR into `develop` (squash), after flipping `Status` to `Accepted` in the ADR and the index row. | owner merges |
| 2 | One **cutover PR** into `develop` (squash), `ci(github-flow): cut over to main-only` with the changes below. `develop` is still the valid target, and required check names are unchanged, so it merges normally. Freeze other PRs into `develop` from here. | agent writes, owner merges |
| 3 | A **final promotion PR** `develop` to `main` with a merge commit (still the only allowed method). No CHANGELOG promotion, no tag, no merge-back (`develop` is deleted in step 6). It carries the new `ci.yml`, so its `main` push runs tests only and publishes nothing; `latest` stays on the `v0.1.1` image. This step is what makes the new workflow and `dependabot.yml` take effect: both are read from `main`. | owner (opens and merges; an agent may open it on request) |
| 4 | **Rulesets and repo settings.** `bash scripts/apply-rulesets.sh` (updates `protect-main` to squash only); `gh api -X DELETE repos/hmcaio/my-finances/rulesets/<protect-develop id>` (list ids with `gh api repos/hmcaio/my-finances/rulesets`); in repository settings turn on "Automatically delete head branches" and, once step 6 is done, turn off "Allow merge commits" and "Allow rebase merging". This must follow step 3, because the promotion needs `merge`, and precede step 6, because `protect-develop` has a `deletion` rule. | owner (admin) |
| 5 | **Retarget open PRs**: `gh pr edit <n> --base main` for each non-Dependabot PR (then `gh pr update-branch <n>` so it is current with `main`); Dependabot PRs are recreated automatically or with `@dependabot recreate`. Check `gh pr list --base develop` is empty. | owner, or an agent when asked |
| 6 | **Delete `develop`**: `git push origin --delete develop`, `git branch -D develop` (after `git switch main`), `git fetch --prune`; remove any worktree on it. | owner |
| 7 | **First release under the new flow, 0.1.2**, following the new `docs/conventions/release.md`, with a `v0.1.2-rc.1` rehearsal. Ideally after the `build-images` follow-up has landed; if not, the rc is mandatory. | owner tags, agent drafts the release PR |

### Cutover PR (step 2): every file that changes

Found with `grep -rn develop` over `CLAUDE.md`, `README.md`, `docs/`, `.claude/skills/`, `.github/`, `scripts/` and the Dockerfiles, plus a search for the publish-on-`main` wording.

**Workflow and config**

| File | Change |
|---|---|
| `.github/workflows/ci.yml` | Header comment (lines 3-7) and the comment above `build-and-push` (line ~234): images are published on tags only. `build-and-push` `if:` becomes `github.event_name == 'push' && startsWith(github.ref, 'refs/tags/v')`. In the three `docker/metadata-action` steps: `type=raw,value=latest,enable=` becomes true only for a stable tag (`startsWith(github.ref, 'refs/tags/v') && !contains(github.ref, '-')`) and `flavor: latest=false` is added so the action's own `latest` rule cannot add it for a release candidate. Comment at line ~138 ("Docker image build on main/tags") becomes "on tags". `on.push.branches: [main]` stays (tests run on `main`). Recommended addition: on tag runs, check out with `fetch-depth: 0` and fail unless the tagged commit is an ancestor of `origin/main`. |
| `.github/dependabot.yml` | Delete the six `target-branch: develop` lines (26, 34, 42, 54, 59, 71). |
| `.github/rulesets/protect-main.json` | `allowed_merge_methods`: `["merge"]` to `["squash"]`. |
| `.github/rulesets/protect-develop.json` | Delete the file. |
| `scripts/apply-rulesets.sh` | Comment only (line 4): "ADR 0008" to "ADR 0032". |
| `backend/Dockerfile`, `frontend/Dockerfile` | Header comment (line 2): "Built by CI on pushes to main and on version tags" becomes "on `vX.Y.Z` tags". |
| `docker-compose.prod.yml` | Comment (lines 13-15): every `vX.Y.Z` or `-rc.N` tag push publishes that version, `sha-<short>` and (stable only) `latest`; `main` pushes publish nothing. |
| `.env.example` | Comment above `IMAGE_TAG` (lines 42-44): "a short git SHA or `latest` from a main push" becomes "`vX.Y.Z` (pinned, reproducible), `vX.Y.Z-rc.N`, or `latest` (the newest release)". The default `latest` stays. |

**Docs**

| File | Change |
|---|---|
| `CLAUDE.md` | Line 23: "Branch off `develop` and PR into `develop` (ADR 0008); never commit to `develop` or `main` directly" becomes "Branch off `main` and PR into `main`, squash-merged (ADR 0032); never commit to `main` directly". Line 24: "(rulesets, ADR 0008)" becomes "(rulesets, ADR 0032)", and "on a release commit or tag it publishes no images" stays true. Net line count unchanged. |
| `README.md` | Line 197 (Workflow): "GitHub Flow with a long-lived `develop` branch ... (features branch off `develop`, releases promote to `main`)" becomes "GitHub Flow on `main` only ... (short-lived branches squash-merged into `main`, releases are a release PR plus a `vX.Y.Z` tag)", link to ADR 0032. Line 68 ("develop on it", "not `latest`") needs no change. |
| `docs/conventions/release.md` | Rewritten to the new flow. Intro and the ADR references. Step 1 (freeze) removed. Step 2: release PR into `main`, squash. Step 2b: scan locally or rely on the PR-time scan (once the follow-up lands). Steps 3 and 4 (promotion, merge-back) removed. Step 5: the rc is tagged on the release PR's commit. Step 7: tag that commit, not "`main` HEAD". Step 9: no "reopen `develop`". Gotchas: the `gradlew` item now says the failure is found by the PR-time image build, or by the rc; the failing-scan item says the fix is a PR into `main` and a new release PR; the merge-back item is deleted; a new item: a publish-only failure on a tag burns that version, re-running failed jobs from the Actions UI works for transient errors but a workflow defect needs the next tag. How a fix found during an rc is handled: see Open question 1. |
| `docs/conventions/tooling.md` | Line 15: images are built and pushed on `vX.Y.Z` tags only; the job triggers on PRs, `main` pushes and tags, and runs the push steps on tags. Line 19: `(ADR 0008)` to `(ADR 0032)`, add that `apply-rulesets.sh` never deletes a ruleset (remove one with `gh api -X DELETE`), and that renaming a job means editing `protect-main.json` only (`protect-develop.json` is gone). Line 33 and the "Not in the default `verify.sh` run" bullet (line 36) stay accurate until the `build-images` follow-up lands; that PR rewrites them. |
| `docs/conventions/README.md` | Line 14: trigger "promoting `develop` to `main`, tagging a version" becomes "cutting a release PR, tagging a version". |
| `docs/features/F014-cicd-production-packaging/spec.md` | Lines 11, 50-51 and 57 describe publish-on-`main` and `latest` from `main`; add "(amended by ADR 0032: tags only)" at each, since the spec states built behaviour. `plan.md` is a history of what was done and is left alone. |
| `CHANGELOG.md` | One `Changed` bullet under `[Unreleased]` with an `Upgrade:` sub-line: the `latest` and `sha-<short>` image tags are now published only with a release tag, so `latest` is the newest release and a `main` merge no longer publishes. It changes how someone running `IMAGE_TAG=latest` or a `sha-` tag upgrades (root `CLAUDE.md`, CHANGELOG rule). |

**Skills (`.claude/skills/`)**

| File | Change |
|---|---|
| `release/SKILL.md` | Frontmatter description and intro (lines 3, 10): "on develop" becomes "on main". Step 1 (line 26): source ref `origin/main`. Step 2 (lines 34-35): range `<tag>..origin/main`; delete the "tag is not an ancestor of `develop`" and "unmerged hotfix" reasoning (the tag is now an ancestor of `main`, `git describe` works); first-release wording uses `origin/main`. Step 3 (line 41) and Step 5e (line 78): `origin/main`. Step 4 (line 49): `--first-parent` note drops the merge-back reasoning. Step 6 (line 96): "`develop` or a release branch" becomes "`main` or a release branch". Step 7 (line 102): the head to check is the release PR's commit (and `origin/main` tip), not "the head of the `develop` to `main` PR". Step 8 (lines 112-118): rewrite to the new order (branch off `main`, one commit, PR into `main`, squash, tag the release commit, publish the release); delete the promotion and merge-back items; the rc item says the rc is tagged on the release commit. |
| `implement-feature/SKILL.md` | Frontmatter (ADR 0008 reference), line 25 (Step 3: branch ref from `main`, "local `main` ref is current", `git branch feature/f004-transactions main`), line 68 (PR into `main`, "per ADR 0032"), line 70 (release workflow wording: "release PR, tag"). |
| `audit-and-fix/SKILL.md` | Frontmatter description (line 3, "a branch off develop"), line 31 (branches off and PRs target `main`, ADR 0032), line 33 ("`origin` shows `main` in sync"), line 68 (`gh pr create --base main`), line 79 (branch fresh off `main`). Its line 68 `CHANGELOG` PR-link follow-up sentence is the subject of #190; rebase over it. |
| `design-feature/SKILL.md` | Line 32 (`origin/main` for next free ids), line 38 (`git worktree add ... origin/main`; "not a clean `main`"), line 39 ("Clean `main`" and `git branch --show-current` is `main`), line 41 (ADR 0032 reference), line 81 (`gh pr create --base main`). |
| `run-app/SKILL.md` | No change. |

**Changes not made, deliberately.** The `CHANGELOG.md` history; the bodies of ADRs 0001-0031 (only the `Status` lines of 0006, 0008 and 0031 change, in step 1); feature `spec.md`/`plan.md` text that records history ("each off `develop`", "already merged to `develop`" in F015, F016, F021, F025); the Javadoc in `AuditEntityType.java` ("landed on `develop`"); `.githooks/pre-commit` (no branch logic).

## Follow-up work (not part of this ADR's cutover)

Each is its own PR, and the first two warrant a short spec (a `docs/features/FXXX` folder) or an amendment of ADR 0031, whose last decision bullet ("The scan runs only in `build-and-push`, not on PRs") they reverse for path-filtered PRs.

1. `build-images` job on PRs (build without push, scan gate), the `changes` job's `images` output, required-check entry in `protect-main.json`, and the `tooling.md` and ADR 0031 wording.
2. Prod smoke job (PR-time behind `build-images`, and in the tag run before the push steps): throwaway `.env`, `docker-compose.prod.yml` against the built images, healthy services, login, header matrix, `/api/backup-status`; required-check entry.
3. Optional: script the release-time upgrade test from the previous tag, which stays a manual checklist item in `release.md` until then.

Land 1 and 2 before the first release under the new flow when possible; without them the `-rc.N` rehearsal is the only pre-tag proof of the publish path.

## Consequences

**Improves**
- A release is one release PR plus a tag: the promotion and merge-back PRs (eight across two releases) and their "out-of-date" blocking disappear.
- `closes #N` closes the issue at merge, and Dependabot needs no `target-branch`.
- One definition of "released": a `vX.Y.Z` tag. `latest` means the newest release; images of unreleased commits do not exist in GHCR.
- No second branch to protect, keep in sync, or forget to merge back.

**Given up**
- An integration branch. `main` must be releasable at every merge: unfinished work stays on its branch until it is done, and a multi-PR feature merges in slices that each leave the app coherent (F021's "each shippable" rule), instead of piling up on `develop`.
- Batching by branch. Choosing the release moment (when to open the release PR) is now the only batching lever.
- Images of unreleased commits (`:latest` and `:sha-<short>` per `main` push). No current consumer uses them: the README tells users to pin a release.

**Risks and mitigations**
- *Every merged PR becomes the next release candidate.* A bad merge is undone with a revert PR, not by holding it back on `develop`. A release cut mid-feature ships the finished slices, so cut releases between features and keep each CHANGELOG bullet true of what is on `main`. Mitigation: required checks, the PR-time image build and smoke jobs (follow-up), release PRs that show the CHANGELOG, and `/release --check` between releases.
- *No staging.* There was none before either (`develop` was never deployed). The prod smoke job and the rc rehearsal stand in for it.
- *A publish-only failure burns a tag* (tags are immutable). Mitigation: the PR-time image build and scan, the smoke job in the tag run before any push, `-rc.N` rehearsal, and re-running failed jobs for transient errors; a workflow defect costs the next patch number.
- *Strict "current with `main`" costs a click.* With two open PRs the second needs `gh pr update-branch` and a fresh check run after the first merges. Accepted for a solo project; the setting is what keeps squash-merged `main` equal to what was tested.
- *Push-to-`main` test runs are largely redundant* (a strict, squash-merged PR already tested the same tree). They stay: they are cheap, show whether `main` is green, and catch an admin-bypass merge.
- *A hotfix ships whatever else is on `main`.* Accepted because `main` is releasable by rule. If that ever fails, cut a `release/X.Y` branch from the tag for that one fix (with its own ruleset); it is not pre-built.
- *A new CVE can block a PR that touches `backend/**`* once the PR-time scan lands (ADR 0031 kept PRs unscanned to avoid exactly this). The fix is a bump in or before that PR, or a dated `.trivyignore` entry.

## Alternatives considered

- **Keep `develop` and cut the friction.** Set `strict_required_status_checks_policy: false` on `main` so no merge-back is needed. Rejected: it removes one PR of eight, weakens the "tested against current `main`" guarantee, and leaves late issue closure, the Dependabot target, and a publish path exercised only after the promotion.
- **Make `develop` the default branch.** Fixes `closes #N` and Dependabot targeting, not the promotion ceremony or the merge-back. Rejected.
- **Publish on every `main` push as well as on tags** (sha-only on `main`, `latest` only on tags). Pros: the tag run could retag by digest the exact image already published, a smoke job could pull a real artifact, and a build failure would show at merge. Cons: every merge pays for build, scan and push of images nobody runs; GHCR fills with `sha-` tags of unreleased commits; a new CVE reddens a `main` push whose PR was green (as today); and the "what is published" answer stays two-valued. The PR-time build and scan give the same early signal without publishing, and the tag run scans the exact image it pushes (rebuilds are not bit-identical because of `apk upgrade`, so a retag-by-digest scheme would also need to scan at tag time). Chosen: tags only. No blocking reason for publish-on-`main` was found.
- **Release automation** (`release-please`, `semantic-release`, a tag-on-merge workflow). Rejected as in [ADR 0007](0007-single-shared-semver-and-changelog.md): manual release stays, and a tag is the owner's deliberate act (`protect-release-tags`).
- **A `release/X.Y` branch per release.** Gives hotfix isolation at the price of the same second-branch ceremony. Deferred to the case where a hotfix cannot ship from `main`.

## Open questions for the owner

1. **Where does an rc sit, and how is a fix found during it released?** Recommendation: tag `vX.Y.Z-rc.1` on the release PR's squash commit (versions already `X.Y.Z`). If the rc shows a defect, fix it in an ordinary PR whose CHANGELOG bullet goes under the existing `## [X.Y.Z]` section (and re-date it), then tag `rc.2` and finally `vX.Y.Z` on that later commit. `release.md` states this.
2. **Should the PR-time scan be a required check?** The owner's direction is to run the gate on PRs. Recommendation: required (it is what moves a failure off the immutable tag), accepting that a freshly published CVE blocks backend and frontend PRs until a bump or a dated allowlist entry; revisit if that happens more than about once a quarter.
3. **Extra paths for the `images` filter.** Recommendation: add `.trivyignore` and `scripts/verify.sh` to the owner's list, since both change what the scan gate does.
4. **Repo settings.** Recommendation: turn on "Automatically delete head branches" now, and turn off "Allow merge commits" and "Allow rebase merging" after step 6, so the repo settings match the ruleset (admin action).
5. **Tag guard.** Recommendation: include in the cutover PR a tag-run check that the tagged commit is an ancestor of `origin/main`, since only the owner pushes tags but nothing else stops a tag on a stray branch commit.
6. **Keep the tests in the tag run?** They re-test a commit `main` already tested. Recommendation: keep (`build-and-push` needs them; simplest, and a few minutes per release).
7. **Status wording.** This ADR uses `Proposed`, which the template does not list. Recommendation: flip it to `Accepted` in the same PR once reviewed (ADR file and index row); no change to the template.
