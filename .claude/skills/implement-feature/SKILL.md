---
name: implement-feature
description: Implements one feature from docs/features/<FXXX>-*/ (its spec.md + plan.md) by delegating the actual coding to an isolated subagent on its own git branch/worktree, following this repo's branching (ADR 0008), commit (ADR 0009), and versioning (ADR 0007) conventions. Use this whenever the user asks to implement, build, work on, resume, or start a feature by its FXXX id or name (e.g. "implement F004", "let's build the transactions feature", "/implement-feature F007", "start on recurring templates") — prefer this over ad hoc implementation whenever a matching docs/features/<FXXX>-*/ folder exists, even if the user doesn't name the skill or say "F00X" explicitly.
---

# Implement Feature

Implements one feature end-to-end (backend + frontend, as applicable) by delegating to an isolated subagent, following this repo's documented conventions. Invoked as `/implement-feature <FXXX>` (e.g. `/implement-feature F004`), or triggered by a request naming a feature.

## Step 1 — Locate the feature docs

Glob `docs/features/<FXXX>-*/spec.md` and `plan.md` (match the `FXXX` prefix case-insensitively — the id might arrive as `f004` or `F004`). If nothing matches, stop and tell the user, listing the `FXXX` folders that do exist under `docs/features/` so they can pick the right one.

Read both files in full before doing anything else — the whole point of this skill is that the spec/plan are the source of truth, not something to re-derive.

## Step 2 — Check dependencies

Read `plan.md`'s `**Depends on**:` line. For each `FXXX` listed there (skip if it says "none"):

- Check whether that dependency's own `plan.md` checklist is checked off, and whether the source paths its spec introduces already exist (its domain package, its Flyway migration, etc.).
- If a dependency looks unimplemented or only partially done, stop and tell the user which one and why it looks incomplete, then ask whether to proceed anyway. A feature built on a missing dependency fails confusingly deep into the work, not at the start — worth a pause here.

## Step 3 — Create the feature branch

Per [ADR 0008](../../../docs/adr/0008-github-flow-with-develop-branch.md), feature branches come off `develop`:

- Check the current branch. If not already on `develop`, check it out and pull the latest.
- Create `feature/<fxxx-lowercase>-<slug>` off it, where `<slug>` is the feature folder's name with the `FXXX-` prefix stripped (e.g. `F004-transactions` → `feature/f004-transactions`).
- Do this in the main working directory. This branch is what the subagent's isolated worktree (Step 4) will check out — you're just creating the ref here, not doing any implementation work on it yourself.

## Step 4 — Delegate implementation to a subagent

Spawn an `Agent` with `isolation: "worktree"` so the subagent gets its own checkout on `feature/<fxxx>-<slug>`, leaving the main session's working directory untouched. Don't implement the feature in the main session yourself — isolating the real work in its own worktree is the reason this skill exists instead of just doing the task inline.

The subagent starts cold — it has no memory of this conversation. Its prompt must be fully self-contained:

1. **Paste in the full text** of this feature's `spec.md` and `plan.md` — don't just point at the paths, the subagent needs the content in front of it.
2. **Tell it to read first**: `docs/PRD.md` and `CLAUDE.md` for product/repo context, and `docs/adr/README.md` for the full ADR index — then specifically:
   - `docs/adr/0004-hexagonal-ddd-tdd.md` (Hexagonal/DDD/TDD) — write tests before the implementation that satisfies them, for every checklist item `plan.md` already phrases as test-first.
   - `docs/adr/0005-single-point-uuid-generation.md` — every new entity's id comes from the single `IdGenerator` port; never `@GeneratedValue`, never an ad hoc `UUID.randomUUID()` elsewhere.
   - `docs/adr/0009-conventional-commits.md` — every commit uses Conventional Commits format (`type(scope): description`).
3. **Work order**: follow `plan.md`'s checklist top to bottom (Backend, then Frontend, then Verification) — it's already sequenced test-first for the rules-heavy items; don't reorder it.
4. **Commit discipline**: commit in small logical chunks as work progresses — roughly one commit per checklist bullet or tightly related group of bullets, not one mega-commit at the end. Conventional Commits, scoped to the feature id: `feat(F004): add Transaction domain model and tests`, `test(F004): cover closed-account rejection`. Never touch `CHANGELOG.md` or bump `version` in `build.gradle`/`package.json` — those are release-time actions (`docs/adr/0007-single-shared-semver-and-changelog.md`) that happen on `develop` → `main`, not on a feature branch.
5. **Keep the plan in sync with reality**: as each `plan.md` checklist item is completed, flip its `- [ ]` to `- [x]` in `docs/features/<FXXX>-*/plan.md` and include that edit in the *same* commit as the code it corresponds to — not a separate end-of-work doc-only commit. This is what lets the next `/implement-feature` run (on a feature that depends on this one) trust the checklist as real status in Step 2, instead of a stale template.
6. **The spec/plan are settled decisions, not a starting point for re-deriving product choices** — implement what's written. But if something the plan genuinely doesn't resolve turns out to matter (a real gap, not a trivial detail already flagged as an open implementation detail in the spec), don't silently guess on it — surface it clearly in the final report instead.
7. **End with a summary**: what was implemented (backend/frontend), which checklist items are now checked off, what tests were added, and any ambiguities flagged. This is what Step 5 relays back.

## Step 5 — Report back

Once the subagent finishes, tell the user:

- The worktree path and branch name it worked on.
- A concise summary of what got implemented, drawn from the subagent's own summary — don't just dump its full output.
- Which `plan.md` items are now checked off vs. still open.
- Any ambiguities the subagent flagged.
- That the branch is committed **locally only**, in that worktree. This skill does not push or open a PR — reviewing the diff, pushing, and opening the PR into `develop` (per ADR 0008) is the user's call.

Don't touch the release workflow (`develop` → `main`, tagging, `CHANGELOG.md`) from this skill — that's a separate, deliberate action, out of scope for implementing a single feature.
