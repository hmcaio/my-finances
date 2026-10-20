# 0007. Single shared SemVer version across backend and frontend, with a root CHANGELOG

Status: Accepted
Date: 2026-09-13

## Context
The project is a monorepo (backend + frontend in one repository, one PR spans both when a change needs it) with CI already designed (F014) so that pushing a single git tag `vX.Y.Z` triggers building and publishing *both* the backend and frontend Docker images tagged with that same version. That design only works cleanly if there's one version number for the whole project — independent backend/frontend versions would mean a single release tag maps ambiguously to two different version histories.

## Decision
- One [Semantic Versioning](https://semver.org/) number for the entire project, not independent versions per side. `backend/build.gradle`'s `version` and `frontend/package.json`'s `version` are always kept equal, both set from the same release tag.
- Starting version is `0.1.0` — SemVer's major-version-zero convention ("anything may change at any time") fits a pre-1.0, actively-changing solo project; `1.0.0` is reserved for when the app is considered stable/usable end to end.
- A single `CHANGELOG.md` at the repo root, formatted per [Keep a Changelog](https://keepachangelog.com/): an `[Unreleased]` section accumulates entries as changes land; at release time, `[Unreleased]` is renamed to the new version + date, and a fresh empty `[Unreleased]` is added above it.
- Release process is manual, not tool-automated (consistent with this project's general preference for minimal tooling — see the ADR index's note on skipping `adr-tools`/`log4brains`): update `CHANGELOG.md`, bump both version fields to match, commit, tag `vX.Y.Z`, push the tag — which is the exact trigger F014's CI already keys off of.

## Consequences
- No backend/frontend version drift to track or reconcile — a version number always means the same commit on both sides.
- A change to only one side (e.g. a frontend-only bugfix) still bumps the whole project's version. Accepted overhead for the simplicity gained in a solo, single-repo project.
- No changelog-generation or version-bump tooling (e.g. `semantic-release`, `standard-version`) is introduced — plain manual edits, same as this project's ADR process itself.
