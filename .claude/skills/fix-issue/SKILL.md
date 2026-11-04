---
name: fix-issue
description: Fixes one my-finances GitHub issue on a branch off main with tests, verification and docs, ending in a Closes-#N commit; pushes and opens a PR only when asked. Use when the user gives an issue number or asks to fix a bug or an audit finding, "fix #N", "fix everything from the audit" or "apply the recommendations". Not for a planned feature (implement-feature) and not for finding problems (audit).
---

# Fix issue

The issue is the spec: `gh issue view <N> --comments`, and a finding the user trimmed is out of scope. A bug with no issue yet goes through `write-issue` first, so the commit can say `Closes #N`. Each stage ends where the user can redirect.

## 1. Branch

ADR 0032: branch off `main`, PR into `main`, never commit to `main`. `git status` is clean and `git fetch origin` shows `main` in sync (another PR just merged: confirm before branching). Name it `fix/`, `refactor/`, `chore/` or `docs/` plus a slug (`feature/` is FXXX work). A checkout with unrelated work in progress gets a worktree instead ([tooling.md](../../../docs/conventions/tooling.md), "Worktrees").

## 2. Fix, with tests

Read `docs/conventions/backend-testing.md` and `frontend-testing.md` (the stack you touch) first: the repo's test traps live there. Write tests beside each fix in the existing layers: unit tests on the `testsupport/Fake*Repository` fakes, `@SpringBootTest` for persistence and REST, MSW + React Testing Library on the frontend.

- **Keep extractions behavior-preserving.** When de-duplicating, check every call site for small variations (margins, disabled states, labels, dismissibility) and parameterize, or leave the outlier alone, rather than silently unifying it.
- **Run the new test red.** For a bug fix, restore the old code and confirm the new tests fail against it; a test that passes before and after guards nothing. Date/time fixes are the classic case: CI runs in UTC and hides a UTC-vs-local bug unless the test pins another zone.

## 3. Verify

`scripts/verify.sh` runs exactly what CI runs: use it for the final pass and fix failures instead of skipping them. For faster iteration, `./gradlew spotlessApply` rather than hand-formatting Java (google-java-format rewraps javadoc), and `npx prettier --write` only on files you touched. Many tests failing after one change: look for one shared root cause (a seed-name collision, a changed constructor) before fixing files one by one.

Then read `CODING_STANDARDS.md` against your diff and fix what it flags. Done when `verify.sh` is green and the diff passes that read.

## 4. Docs

- **Steering files**: a line in `docs/conventions/<topic>.md`, `CLAUDE.md` or `CODING_STANDARDS.md` only for a rule or gotcha the code cannot show and that would cost time to rediscover, written as the rule, not the story. A rule a check can enforce becomes an ArchUnit/ESLint/script check. The story belongs in the commit body and PR description.
- **`docs/features/FXXX/spec.md`**: update when observable behavior or schema of that feature changed.
- **`CHANGELOG.md`**: the entry ships in the fix commit, per [release.md](../../../docs/conventions/release.md) ("CHANGELOG entries"); an internal-only change says so in the closing message instead.
- **`docs/PRD.md`**: only when a product decision changed or was newly made.

## 5. Commit

Conventional Commits (ADR 0009), message via HEREDOC, the body explains *why* and carries `Closes #N`. Stage explicit paths (`git add -- backend frontend docs ...`), then check `git status`. Honor "don't commit yet"; amend or force-push only when asked.

## 6. Push and PR: only when asked

Approving the fix is not approval to push. When asked: `git push -u origin <branch>`, `gh pr create --base main`, the body written with the `pr` skill if available. It carries `Closes #N`, what changed and why, and a checked test plan. Return the PR URL.

## 7. Post-mortem: only when asked

Asked for how the bug got here: read [post-mortem.md](post-mortem.md).

## Closing message

One or two sentences: what changed, what is verified, the next step you offer (push/PR, or the follow-up you deferred).
