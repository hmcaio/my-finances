---
name: audit-and-fix
description: Runs a cross-cutting audit of the my-finances codebase (schema constraints, transaction boundaries, frontend duplication, missing tests, doc drift, or any "review X for gaps" request), reports ranked findings, files a GitHub issue, then fixes it on a branch off develop with tests, verification, CLAUDE.md updates, and a Closes-#N commit — pushing and opening a PR only when asked. Use this whenever the user asks to audit, review, or check the codebase/schema/frontend/backend for gaps, risks, missing pieces or reuse candidates, or says "fix everything from the audit", "create an issue for this", or "apply the recommendations", even if they don't say "audit". Not for building a planned feature from docs/features/FXXX (use implement-feature for that).
---

# Audit and fix

Cross-cutting findings (a class of bug repeated across many files, a missing constraint, duplicated UI) don't belong to any single `docs/features/FXXX` folder, so they don't fit `implement-feature`. This skill is the workflow for them: find, report, track, fix, verify, document, ship. Each stage ends at a point where the user can redirect, so don't skip ahead of them.

## 1. Audit (read-only)

Scope the audit from the user's request and read the actual code — don't infer from filenames. Two habits that matter here:

- **Verify every "missing" finding by reading the file it's supposedly missing from.** An empty grep is weak evidence (a mistyped pattern looks identical to a true absence). In a past audit, "amount positivity isn't enforced in the domain layer" came from a broken grep; the domain classes already checked it, and only the DB layer was actually missing. Reporting a false gap wastes the user's trust and the fix effort.
- **Check what already exists before proposing to add it.** Look at how sibling aggregates/pages handled the same concern (e.g. whether F004's `Transaction` already has `requireValidAmount`) so the finding is scoped to the real gap.

Also read `docs/features/` for not-yet-built features when the audit concerns conventions — fixing a spec now is cheaper than a retrofit migration later.

## 2. Report

Reply in chat with findings ranked by severity. For each: file and line, the concrete failure scenario (inputs/state → what breaks), and the proposed fix. Say explicitly what you looked at and decided *not* to change, with the reason — a deliberate non-fix (e.g. a self-healing job where wrapping it in a transaction would be worse) is a finding too, and it stops the user re-asking. Ask about genuine product decisions instead of assuming (e.g. "should `accounts.name` be globally unique?") — use AskUserQuestion when the answer changes the schema or behavior.

## 3. File an issue

When the user asks: `gh issue create` with a title, a summary, one section per finding (files, scenario, fix), and an "out of scope / deliberately not changed" section. Bugs and audit findings live in GitHub issues; planned features stay in `docs/features/FXXX` — don't create a feature spec for a fix or an issue for a planned feature. Keep the issue number for the commit and PR.

Wait for the user's go-ahead before starting fixes; they may want to trim scope.

## 4. Branch

Per ADR 0008, work branches off `develop`, and PRs target `develop` (never commit to `develop` or `main` directly).

1. `git status` must be clean and `git fetch origin` shows `develop` in sync. If another PR just merged, confirm before branching.
2. `git checkout -b <type>/<short-slug>` with `fix/`, `refactor/`, `chore/` or `docs/` (`feature/` is for FXXX work).

## 5. Fix, with tests

Write tests alongside each fix, following the existing layers: unit tests against the `testsupport/Fake*Repository` fakes for application logic, `@SpringBootTest` adapter/controller tests for persistence and REST, and MSW + React Testing Library on the frontend. Keep extractions behavior-preserving — when de-duplicating, check each call site for small variations (margins, disabled states, labels, dismissibility) and parameterize or leave the outlier alone rather than silently unifying it.

Before writing tests, re-read the "Testing" sections of `backend/CLAUDE.md` and `frontend/CLAUDE.md` (for whichever stack you're touching) and the cross-stack conventions in the root `CLAUDE.md` — the repo-specific traps live there, so they apply to every task, not only audits. The ones these audits hit most: transaction-boundary tests must *not* be `@Transactional` themselves (the general rule says the opposite), V2 seed rows collide with new `UNIQUE` constraints in real-DB fixtures, a new 409 needs a frontend `conflictMessage` because the backend never sends exception text, and frontend tests have no Node types (run `npm run build`, not just `npm test`).

One habit worth keeping on top of those: for a bug fix, temporarily restore the old code and confirm the new tests fail against it. A test that passes both before and after guards nothing — date/time fixes are the classic case, since CI runs in UTC and hides a UTC-vs-local bug unless the test pins another zone.

## 6. Verify

Run everything the CI runs, and fix rather than skip failures. `scripts/verify.sh` (repo root; stages `backend`, `frontend`, `versions`) runs exactly what CI runs, so use it for the final pass; the per-stack commands below are for faster iteration:

- Backend (from `backend/`): `./gradlew spotlessApply` then `./gradlew spotlessCheck test integrationTest`. Use `spotlessApply` instead of hand-formatting Java — google-java-format rewraps javadoc.
- Frontend (from `frontend/`): `npm run lint`, `npm run format:check`, `npm test`, `npm run build`. Run `npx prettier --write` only on files you touched; don't reformat pre-existing offenders you didn't change.

If many tests fail after one change, look for one shared root cause (a seed-name collision, a changed constructor) before fixing files individually.

## 7. Docs

- `CLAUDE.md` files (root for rules that span both stacks, `backend/CLAUDE.md` or `frontend/CLAUDE.md` for one): add a bullet only if the work produced a *rule or gotcha* that can't be derived from the code and would cost time to rediscover — and phrase it as the rule, not the story. What changed, why, and any deliberate non-fix belong in the commit body and PR description (and the feature's `spec.md` if behavior changed). Narrative paragraphs in CLAUDE.md are what bloated it to 33 KB of mostly-history, and it loads into every session.
- `docs/features/FXXX/spec.md`: update when observable behavior or schema for that feature changed. Not needed for pure defense-in-depth constraints that only the migration header documents.
- `CHANGELOG.md`: per the root `CLAUDE.md` rule "CHANGELOG entries ship with the PR", a fix or change users would notice gets one plain bullet under `## [Unreleased]` (`### Fixed` / `### Changed`, citing the issue as `closes [#N](…)`), plus an `Upgrade:` sub-line if it changes how someone runs or upgrades the stack (a new migration or constraint that can fail on existing data, a compose/env change). No entry for defense-in-depth-only, test-only or purely internal work — say so in the closing message instead. Include the edit in the fix commit (step 8), and leave the PR link off for now (step 9 adds it).
- `docs/PRD.md`: only when a product decision changed or was newly made. `README.md`: rarely.

## 8. Commit

Conventional Commits (ADR 0009), message via HEREDOC, staging by explicit path (`git add -A -- backend frontend CLAUDE.md`, then check `git status`). Explain the *why* in the body and put `Closes #N` in it. Honor an explicit "don't commit yet" from the user. Never amend or force-push unless asked.

## 9. Push and PR — only when asked

Pushing is visible to others, so wait for the user to say so (approving the fix is not approval to push). Then `git push -u origin <branch>` and `gh pr create --base develop` with: `Closes #N`, a summary of what changed and why, and a checked test plan listing the commands from step 6. If step 7 added a `CHANGELOG.md` entry, add its `([#N](…))` PR link now that the number exists and push that one follow-up commit (`docs: link PR in CHANGELOG entry`; the squash-merge folds it in). Return the PR URL.

## Closing message

One or two sentences: what changed, what's verified, and the next step you're offering (push/PR, or the follow-up you deliberately deferred).
