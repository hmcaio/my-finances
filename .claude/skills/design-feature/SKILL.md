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

### Ids

`git fetch origin`, then read the next free id(s) from `origin/develop`, which is where the docs branch starts: `git ls-tree --name-only origin/develop docs/features/ docs/adr/` — the highest `F`-prefixed `FXXX` folder (`_template` is not an id) and, if writing an ADR, the highest ADR number. Never reuse or guess a number — collisions silently shadow another feature's folder.

### Workspace

Create the docs workspace before the first file is written, so every file lands on the docs branch:

- **Default: a sibling worktree** — `git worktree add -b docs/<short-slug> ../my-finances-<id>-docs origin/develop` (`<id>` lowercase, e.g. `f024`; the first id of a split pair). Use it whenever the current checkout is not a clean `develop` (another branch checked out, or uncommitted work, including an `implement-feature` branch in progress). The checkout and its branch stay untouched. Run Phase 3 and Phase 4 inside the worktree.
- **Clean `develop`** (`git status` clean, `git branch --show-current` is `develop`, local `develop` equal to `origin/develop`): `git checkout -b docs/<short-slug>` here.

The branch is `docs/`-prefixed per [ADR 0008](../../../docs/adr/0008-github-flow-with-develop-branch.md): docs-only work, the same prefix `audit-and-fix` uses for doc-only changes. The `feature/` prefix belongs to the `implement-feature` run that builds this later.

### Template

Copy `docs/features/_template/spec.md` and `plan.md` into `docs/features/FXXX-slug/`: the template is the concrete format, so the headings, their order and the per-section guidance in its comments are the contract, not a format recalled from memory. A frontend-only feature drops `## Backend` and keeps `## Non-goals`.

Read a finished feature's spec only for the sections the new feature shares with it, not in full:

| New feature is | Read |
|---|---|
| Rules-heavy domain/schema work | F027 `spec.md` `## Backend` (Domain, Application, Persistence, API) and its `plan.md` test-first items |
| Cross-cutting infrastructure | F031 or F036 `spec.md` (one `###` per mechanism) |
| Frontend-only | F019 `spec.md` (`## Decisions`, `## Structure`, `## Testing`, `## Non-goals`) |

Match each section's level of specificity (file paths, package names, exact migration/table shapes, exact REST verbs/paths) — a spec vague about *where* code goes is far less useful to whoever runs `implement-feature` against it later.

### Verify library claims

Before the first spec, ADR or PRD sentence is written, list **every** claim the design makes about how a library, framework or tool behaves — each statement of the form "X can/cannot/does/requires Y" about a dependency, a framework option, a Docker/compose/Gradle/npm feature (a Spring Security setting, an axios option, a Flyway rule). Give each claim one disposition:

- **Verified**, with its evidence: the dependency's own source or javadoc (the sources jar under `~/.gradle/caches/modules-2/files-2.1/<group>/<artifact>`, or `node_modules/<pkg>`), or the official docs page fetched with `WebFetch` (cite the URL). A background sub-agent can run the lookups, the way Phase 1 does for repo facts.
- **Unverified**: written verbatim as `unverified - confirm during implementation: <claim>` under the spec's `## Open questions`, and any decision whose "Why" rests on it points there instead of stating the claim as fact.

An unlisted claim counts as unverified. Close Phase 3 with a summary to the user: the files written, then the full claim list, one line per claim (`claim — verified: <evidence>` or `claim — unverified, in Open questions`), or the sentence "no library-behavior claims" when the design has none. The F036 design stated that a wildcard CORS `allowedOriginPatterns` cannot be combined with credentials; it can, and the implementation had to correct the spec.

Write, in this order:
1. The ADR, if Phase 2 decided one's needed (`docs/adr/NNNN-slug.md`, the `## Context`/`## Decision`/`## Consequences` template already at the bottom of `docs/adr/README.md`), plus its index row.
2. `docs/PRD.md` edits, if Phase 2 decided they're needed.
3. Each feature's `docs/features/FXXX-slug/spec.md` and `plan.md` (plan.md's checklist items all start unchecked `- [ ]` — this skill only plans, `implement-feature` does the work and ticks them), cross-linking a split pair's `**Depends on**` lines to each other.
4. `docs/features/README.md`'s table (one row per new feature, in id order) and, if the feature introduces a new cross-cutting convention, the "Cross-cutting conventions" section below it.
5. Root `README.md`'s "Project status" — append to the "Documented and next up" sentence (new features aren't built yet; don't add them to the "Built" list).

Phase 3 is done when `grep -nE '<!--|FXXX|NNNN' docs/features/<FXXX>-*/*.md` is empty and the summary above, with its claim list, has gone to the user.

## 4. Ship

Ship from the Phase 3 workspace (its branch is already `docs/<short-slug>`).

1. Stage exactly the files this skill touched — explicit paths, never `-A`/`.` (`git status` after to confirm nothing stray got swept in).
2. Commit with Conventional Commits (ADR 0009), type `docs`, scope the feature id(s) (`docs(F028-F029): ...`), **and append `[skip ci]` to the end of the commit subject line**. GitHub Actions natively skips both `push`- and `pull_request`-triggered runs when the head commit message carries that marker — no `ci.yml` change needed, and this repo's workflow has no path filter that would otherwise skip a docs-only diff on its own (`.github/workflows/ci.yml` runs on every push/PR unconditionally). No `CHANGELOG.md` entry: `docs` commits aren't user-visible changes (ADR 0009's type table).
3. **Push and open the PR only when the user says so** — same rule `audit-and-fix` follows; confirming the design in Phase 1 is not the same as approving a push. When asked: `git push -u origin docs/<slug>` and `gh pr create --base develop` with a summary of what was added (ADR / which `FXXX` folders / PRD sections) and a test plan noting this is docs-only (nothing to run; `implement-feature <FXXX>` is the next step). Return the PR URL.
4. **Clean up after the merge**, when the user says the docs PR is merged or `gh pr view` reports `MERGED`, and the workspace was a worktree. From the original checkout: `git worktree remove ../my-finances-<id>-docs`, `git branch -D docs/<slug>` (a squash-merge leaves the branch unmerged in git's view, so `-d` refuses), `git worktree prune`. Done when `git worktree list` shows no entry for `my-finances-<id>-docs`.

## Closing message

One or two sentences after Phase 1's confirmation and again after Phase 4: what got written (or shipped) and the next concrete step (`implement-feature <FXXX>`, or push/PR if not done yet, or the worktree cleanup once merged).
