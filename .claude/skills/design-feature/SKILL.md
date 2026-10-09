---
name: design-feature
description: Turns a rough feature idea into this repo's planning docs — an interview that stress-tests the idea into a shared design, then an ADR (if the design warrants one) and docs/features/<FXXX>-*/{spec.md,plan.md}, with the PRD and every index file kept in sync — committed on its own docs/ branch with a skip-ci marker and pushed/PR'd only when asked. Use whenever the user wants to design, plan, spec out or scope a new feature before building it ("let's design a feature for X", "help me plan X", "spec out X") — not for building a feature that's already specified (use implement-feature) and not for a cross-cutting bug/gap fix (use audit-and-fix).
---

# Design Feature

Turns an idea into the planning docs `implement-feature` later consumes. Four phases: interview, research, write, ship. Each ends at a point the user can redirect — don't collapse them.

## 1. Interview (grill it)

Call the `grilling` skill (`Skill({ skill: "grilling", args: "<the user's feature idea>" })`) and run it to completion — rounds of numbered questions with a recommended answer each, frontier recomputed after every answer, until the design tree is fully settled. Don't skip this by jumping straight to writing docs from the user's first description: the value of this skill over ad hoc planning is that it surfaces the branches the user hasn't thought through yet (direction/scope ambiguities, edge cases, which existing entities/queries the change touches) before anything is written down as settled.

Any fact findable from the repo (does this concept already exist, how is the related data modeled, where else would a change ripple) is this skill's job to look up, via a sub-agent (`Explore` for anything under ~3 searches, otherwise a general-purpose `Agent`) dispatched in the background while the next round of *decision* questions goes to the user — never a question the user shouldn't have to answer themselves. The grilling skill already says this; it matters enough here to repeat, since a wrong assumption about existing behavior (e.g. assuming an entity stores a running balance when it's actually computed on read) produces a confidently wrong design.

Stop and summarize the settled design back to the user in plain terms once the frontier is empty. Wait for explicit confirmation before Phase 2 — "looks good" or equivalent, not silence.

## 2. Decide the shape of the docs

Three things to settle before writing anything, by reading the repo rather than guessing:

- **One feature or several?** Every existing `docs/features/FXXX` bundles backend and frontend in one spec with explicit `## Backend`/`## Frontend` sections (`docs/features/README.md`'s own description of the convention). Default to that. Split into separate `FXXX`/`FYYY` folders (one per layer, or along whatever other seam) only when the user asks for it explicitly, as this skill's naming (`F028`/`F029`) in a past run did — don't split on your own initiative, since it's a deviation from the established pattern.
- **Does this need an ADR?** Read `docs/adr/README.md`'s index and skim two or three recent ADRs (e.g. the latest, plus one for a feature similar in shape to this one). The signal isn't "is this a new feature" — plenty of features (F002–F004, F006, F007...) have none. It's "does this introduce a genuinely new architectural tradeoff or pattern" (a new way of modeling something, a reversal of an earlier decision, a new cross-cutting rule). If yes, the ADR is written *first* and the feature spec implements it (every recent investment feature — F020, F021, F022, F023's ADR-less refactor is the exception, F024, F025, F026, F027 — follows this ordering). If the design is closer to "CRUD on a new taxonomy entity" with no new pattern, skip the ADR.
- **Does the PRD need updating?** `docs/PRD.md` is the authoritative spec (root `CLAUDE.md`) and has grown a new `§5.x`/new bullets under `§6.x` for nearly every past feature that touched the data model or added a user-facing capability (check git history / the existing `§5`/`§6` numbering for the highest current subsection before picking the next one). Non-goals (`§3`) and Future Directions (`§9`) get a line too when the design explicitly narrows scope or defers something (e.g. "no cash-in-lieu handling for v1" belongs in `§9`, not silently unmentioned). Skip only for a change with zero product-facing surface (pure internal tooling, following F019/F020's own PRD-untouched precedent).

State these three decisions to the user as part of the Phase 1 summary (or a short follow-up) rather than silently deciding — they're product/documentation-scope calls, not implementation details.

## 3. Write the docs

Find the next free id(s) first: `ls docs/features` for the highest `FXXX`, `ls docs/adr` for the highest ADR number (if writing one). Never reuse or guess a number — collisions silently shadow another feature's folder.

Before writing, **read one recent, similarly-shaped feature's actual `spec.md`/`plan.md`** (and its ADR, if it has one) in full as the template — don't write from a mental template of the format. A frontend-only feature should look like F019's spec (no `## Backend` section, a `## Non-goals` section); a feature with real domain/schema work should look like F026/F027's shape (`Summary`/`Scope`/`Decisions`/`Backend`/`Frontend`/`Dependencies`). Match structure, heading names and the level of specificity (file paths, package names, exact migration/table shapes, exact REST verbs/paths) — a spec vague about *where* code goes is far less useful to whoever runs `implement-feature` against it later.

Write, in this order:
1. The ADR, if Phase 2 decided one's needed (`docs/adr/NNNN-slug.md`, the `## Context`/`## Decision`/`## Consequences` template already at the bottom of `docs/adr/README.md`), plus its index row.
2. `docs/PRD.md` edits, if Phase 2 decided they're needed.
3. Each feature's `docs/features/FXXX-slug/spec.md` and `plan.md` (plan.md's checklist items all start unchecked `- [ ]` — this skill only plans, `implement-feature` does the work and ticks them), cross-linking a split pair's `**Depends on**` lines to each other.
4. `docs/features/README.md`'s table (one row per new feature, in id order) and, if the feature introduces a new cross-cutting convention, the "Cross-cutting conventions" section below it.
5. Root `README.md`'s "Project status" — append to the "Documented and next up" sentence (new features aren't built yet; don't add them to the "Built" list).

## 4. Ship

Per [ADR 0008](../../../docs/adr/0008-github-flow-with-develop-branch.md), work branches off `develop`. This is docs-only, so it gets the `docs/` prefix the `audit-and-fix` skill already uses for doc-only work, not `feature/` (that prefix is for the `implement-feature` run that actually builds this later, on its own branch per feature id).

1. `git status` clean, `git fetch origin`, confirm local `develop` is current.
2. `git checkout -b docs/<short-slug>` (e.g. `docs/investment-splits`) off `develop`.
3. Stage exactly the files this skill touched — explicit paths, never `-A`/`.` (`git status` after to confirm nothing stray got swept in).
4. Commit with Conventional Commits (ADR 0009), type `docs`, scope the feature id(s) (`docs(F028-F029): ...`), **and append `[skip ci]` to the end of the commit subject line**. GitHub Actions natively skips both `push`- and `pull_request`-triggered runs when the head commit message carries that marker — no `ci.yml` change needed, and this repo's workflow has no path filter that would otherwise skip a docs-only diff on its own (`.github/workflows/ci.yml` runs on every push/PR unconditionally). No `CHANGELOG.md` entry: `docs` commits aren't user-visible changes (ADR 0009's type table).
5. **Push and open the PR only when the user says so** — same rule `audit-and-fix` follows; confirming the design in Phase 1 is not the same as approving a push. When asked: `git push -u origin docs/<slug>` and `gh pr create --base develop` with a summary of what was added (ADR / which `FXXX` folders / PRD sections) and a test plan noting this is docs-only (nothing to run; `implement-feature <FXXX>` is the next step). Return the PR URL.

## Closing message

One or two sentences after Phase 1's confirmation and again after Phase 4: what got written (or shipped) and the next concrete step (`implement-feature <FXXX>`, or push/PR if not done yet).
