# 0009. Use Conventional Commits, mapped to CHANGELOG categories and SemVer bump type

Status: Accepted
Date: 2026-09-13

## Context
ADR 0007 established a manual `CHANGELOG.md` and shared SemVer bump for backend + frontend, but didn't specify how an individual commit signals what kind of change it is, or what version bump it implies. Without a convention, deciding "is this release a patch or a minor" at release time is guesswork, and CHANGELOG entries end up freeform and inconsistent.

## Decision
All commit messages follow [Conventional Commits](https://www.conventionalcommits.org/): `<type>(<scope>): <description>`, optionally with a `BREAKING CHANGE:` footer or `!` after the type/scope.

Types used, and what each implies for the next release's CHANGELOG/SemVer bump:

| Type | Meaning | CHANGELOG section | SemVer bump |
|---|---|---|---|
| `feat` | new capability | Added (or Changed, if extending existing behavior) | MINOR |
| `fix` | bug fix | Fixed | PATCH |
| `!` / `BREAKING CHANGE:` footer | incompatible change | Changed, called out explicitly as breaking | MAJOR |
| `docs`, `chore`, `ci`, `build`, `style`, `test`, `refactor`, `perf` | internal/non-user-facing | usually none (add one only if user-visible, e.g. a `perf` fix a user would notice) | none |

`scope` is free-form and optional — typically a feature id (`feat(F004): ...`) or area (`backend`, `frontend`, `ci`) — no enforced scope list, given the project's size.

At release time (ADR 0007's process), the version bump is read mechanically off the highest-impact commit type merged since the last release (any `feat` → at least MINOR, any breaking change → MAJOR, otherwise PATCH), rather than decided from scratch.

No enforcement tooling (`commitlint`, a `husky` pre-commit hook, etc.) is introduced — consistent with this project's preference for minimal tooling ([ADR 0006](0006-separate-prod-packaging-from-dev.md)'s dev-simplicity choice, and skipping `adr-tools`/`log4brains` per the ADR index). Discipline is self-enforced; add tooling later if drift becomes a real problem.

## Consequences
- Commit history becomes self-documenting and scannable by type, without needing a separate log of "what changed" beyond `git log`.
- The release-time SemVer decision (ADR 0007) follows mechanically from commit types instead of being re-litigated per release.
- No automated enforcement — a stray non-conventional commit doesn't block anything, it just has to be caught by eye (or reworded) before it affects a release's version bump.
- Squash-merges ([ADR 0008](0008-github-flow-with-develop-branch.md)'s feature workflow) should use a conventional-commit-formatted squash message summarizing the whole PR, since that's the commit that ends up in `develop`'s (and eventually `main`'s) history.
