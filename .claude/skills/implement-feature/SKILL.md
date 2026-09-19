---
name: implement-feature
description: Implements one feature from docs/features/<FXXX>-*/ (its spec.md + plan.md) by delegating the actual coding to a subagent on its own git branch (optionally an isolated worktree), following this repo's branching (ADR 0008), commit (ADR 0009), and versioning (ADR 0007) conventions, then verifying the docs still match what was actually built. Use this whenever the user asks to implement, build, work on, resume, or start a feature by its FXXX id or name (e.g. "implement F004", "let's build the transactions feature", "/implement-feature F007", "start on recurring templates") — prefer this over ad hoc implementation whenever a matching docs/features/<FXXX>-*/ folder exists, even if the user doesn't name the skill or say "F00X" explicitly.
---

# Implement Feature

Implements one feature end-to-end (backend + frontend, as applicable) by delegating to a subagent, following this repo's documented conventions. Invoked as `/implement-feature <FXXX>` (e.g. `/implement-feature F004`), or triggered by a request naming a feature.

## Step 1 — Locate the feature docs

Glob `docs/features/<FXXX>-*/spec.md` and `plan.md` (match the `FXXX` prefix case-insensitively — the id might arrive as `f004` or `F004`). If nothing matches, stop and tell the user, listing the `FXXX` folders that do exist under `docs/features/` so they can pick the right one.

Read both files in full before doing anything else — the whole point of this skill is that the spec/plan are the source of truth, not something to re-derive.

## Step 2 — Check dependencies

Read `plan.md`'s `**Depends on**:` line. For each `FXXX` listed there (skip if it says "none"):

- Check whether that dependency's own `plan.md` checklist is checked off, and whether the source paths its spec introduces already exist (its domain package, its Flyway migration, etc.).
- If a dependency looks unimplemented or only partially done, stop and tell the user which one and why it looks incomplete, then ask whether to proceed anyway. A feature built on a missing dependency fails confusingly deep into the work, not at the start — worth a pause here.

## Step 3 — Create the feature branch (without checking it out)

Per [ADR 0008](../../../docs/adr/0008-github-flow-with-develop-branch.md), feature branches come off `develop`. Make sure the local `develop` ref is current (fetch/pull as appropriate), then create `feature/<fxxx-lowercase>-<slug>` pointing at `develop`'s tip **as a plain branch ref, without checking it out** (e.g. `git branch feature/f004-transactions develop`) — where `<slug>` is the feature folder's name with the `FXXX-` prefix stripped (e.g. `F004-transactions` → `feature/f004-transactions`).

Leaving it uncheckout matters for Step 4: if this branch were checked out here *and* a subagent's isolated worktree also tried to check it out, git refuses the second checkout of the same branch — you'd end up with the subagent stranded on a differently-named branch and a manual fast-forward to reconcile afterward. Creating the ref without checking it out sidesteps that entirely, whichever isolation choice Step 4 lands on.

## Step 4 — Ask about isolation

Ask the user whether the subagent should work in its own isolated git worktree, or directly in the current working directory:

- **Isolated worktree** (default/recommended) — `Agent` with `isolation: "worktree"`. The subagent gets its own checkout of the feature branch; your current directory and branch stay untouched the whole time, and you're free to do other things in this session while it runs.
- **Same working directory** — check out `feature/<fxxx>-<slug>` here first, then the subagent works directly in this checkout (no `isolation` param). Simpler mechanically, but this session's working directory is "occupied" by the feature branch until the subagent finishes, and you're left on that branch afterward instead of wherever you started.

Don't assume — ask, then proceed with whichever the user picks.

## Step 5 — Delegate implementation to a subagent

Don't implement the feature in the main session yourself — delegating the real work to a subagent (isolated or not, per Step 4) is the reason this skill exists instead of just doing the task inline.

The subagent starts cold — it has no memory of this conversation. Its prompt must be fully self-contained:

1. **Paste in the full text** of this feature's `spec.md` and `plan.md` — don't just point at the paths, the subagent needs the content in front of it.
2. **Tell it to read first**: `docs/PRD.md` and `CLAUDE.md` for product/repo context (plus `backend/CLAUDE.md` and/or `frontend/CLAUDE.md` for the stack being built), and `docs/adr/README.md` for the full ADR index — then specifically:
   - `docs/adr/0004-hexagonal-ddd-tdd.md` (Hexagonal/DDD/TDD) — write tests before the implementation that satisfies them, for every checklist item `plan.md` already phrases as test-first.
   - `docs/adr/0005-single-point-uuid-generation.md` — every new entity's id comes from the single `IdGenerator` port; never `@GeneratedValue`, never an ad hoc `UUID.randomUUID()` elsewhere.
   - `docs/adr/0009-conventional-commits.md` — every commit uses Conventional Commits format (`type(scope): description`).
3. **Work order**: follow `plan.md`'s checklist top to bottom (Backend, then Frontend, then Verification) — it's already sequenced test-first for the rules-heavy items; don't reorder it.
4. **Commit discipline**: commit in small logical chunks as work progresses — roughly one commit per checklist bullet or tightly related group of bullets, not one mega-commit at the end. Conventional Commits, scoped to the feature id: `feat(F004): add Transaction domain model and tests`, `test(F004): cover closed-account rejection`. Never touch `CHANGELOG.md` or bump `version` in `build.gradle`/`package.json` — those are release-time actions (`docs/adr/0007-single-shared-semver-and-changelog.md`) that happen on `develop` → `main`, not on a feature branch.
5. **Keep the plan in sync with reality**: as each `plan.md` checklist item is completed, flip its `- [ ]` to `- [x]` in `docs/features/<FXXX>-*/plan.md` and include that edit in the *same* commit as the code it corresponds to — not a separate end-of-work doc-only commit. This is what lets the next `/implement-feature` run (on a feature that depends on this one) trust the checklist as real status in Step 2, instead of a stale template.
6. **The spec/plan are settled decisions, not a starting point for re-deriving product choices** — implement what's written. But if something the plan genuinely doesn't resolve turns out to matter (a real gap, not a trivial detail already flagged as an open implementation detail in the spec), don't silently guess on it — surface it clearly in the final report instead.
7. **Before wrapping up, verify the documentation still matches what was actually built.** Implementation always surfaces small realities the spec couldn't have predicted — a library needing a version pin, a dependency swapped for one that actually works, a package or file named slightly differently than planned. Re-read this feature's `spec.md` (and `plan.md`) against what actually landed in the code; if something's now inaccurate, fix it — and if it's a cross-cutting convention (package layout, build/lint/test commands, the stack itself), check the `CLAUDE.md` for the stack it applies to (`backend/CLAUDE.md`, `frontend/CLAUDE.md`, or the root one if it spans both). Add a bullet there only for a rule or gotcha someone couldn't work out from the code and would lose time rediscovering; the story of what this feature did and why belongs in the commit/PR body or its `spec.md`, not in CLAUDE.md, which is loaded into every session. Also check the root `README.md`: does this feature change its "Project status" (a feature just went from planned to done), its command examples, or anything else it states as current fact? Update it in the same pass if so — don't leave it describing a state the repo has since moved past. Make all of these doc fixes in the same commit as the code that caused the divergence, not a separate cleanup pass at the end. This isn't a license to write new documentation or restate what's already correct — only correct what's gone stale or note a genuine gap the plan didn't anticipate.
8. **End with a summary**: what was implemented (backend/frontend), which checklist items are now checked off, what tests were added, any ambiguities flagged, and which documentation files (if any) were corrected and why. This is what Step 6 relays back.

## Step 6 — Report back

Once the subagent finishes, tell the user:

- The branch it worked on, and the worktree path if Step 4 used isolation.
- A concise summary of what got implemented, drawn from the subagent's own summary — don't just dump its full output.
- Which `plan.md` items are now checked off vs. still open.
- Any ambiguities the subagent flagged.
- Any documentation it corrected to stay accurate, and why.
- That the branch is committed **locally only**. This skill does not push or open a PR — reviewing the diff, pushing, and opening the PR into `develop` (per ADR 0008) is the user's call.

Don't touch the release workflow (`develop` → `main`, tagging, `CHANGELOG.md`) from this skill — that's a separate, deliberate action, out of scope for implementing a single feature.
