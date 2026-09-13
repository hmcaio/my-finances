# 0008. Branching strategy: GitHub Flow with an added long-lived `develop` branch

Status: Accepted
Date: 2026-09-13

## Context
Standard GitHub Flow assumes `main` is continuously deployed and every merge to it is potentially shippable. This project is a local, on-demand, single-user app with no continuously-deployed production environment to protect (PRD §7.3), and releases are deliberate and versioned (ADR 0007's shared SemVer + CHANGELOG process, with F014's CI only building/publishing images on `main` pushes and version tags). A long-lived `develop` branch fits "batch several features into a deliberate release" better than "every merge to main is a release." `main` and `develop` both already exist in the repository.

## Decision
Three workflows:

- **Feature**: branch off `develop` (`feature/xxx`), PR into `develop`. CI runs tests only on this PR — no image build (that stays gated to `main`/tags, per F014). Squash-merge, delete the branch.
- **Release**: PR `develop` → `main`, merged with a merge commit (not squashed), preserving traceability of exactly which commits shipped. This merge to `main` triggers F014's `build-and-push` job (`:latest`/`:<sha>` images). Follow with ADR 0007's release steps (update `CHANGELOG.md`, bump `version` in `build.gradle`/`package.json`, tag `vX.Y.Z`, push the tag) to get version-pinned images.
- **Hotfix**: branch `hotfix/xxx` off `main` directly, PR into `main`, merge, tag/release as above, then merge `main` back into `develop` (or cherry-pick the fix) so it isn't lost on the next `develop` → `main` promotion.
- **Branch protection**: both `main` and `develop` require PRs and passing CI — no direct pushes to either. Feature/hotfix branches are short-lived and deleted after merge.

## Consequences
- Batches features into a deliberate release point instead of shipping on every merge, and gives cross-dependent features (this project has many — see the `Dependencies` line on nearly every `docs/features/FXXX` plan) a stable integration branch to land on before promotion.
- Keeps the hotfix path clean and independent of whatever's mid-flight on `develop`.
- Keeps CI image-build cost low — images are only built on the deliberate `main`/tag pushes this ADR defines, not on every feature branch.
- Two long-lived branches can drift if a hotfix merge-back to `develop` is forgotten — needs discipline (or a follow-up automation) to not skip that step.
- Slower feedback loop than plain GitHub Flow: a merged feature isn't "released" (doesn't reach `main`/get an image) until `develop` is explicitly promoted, rather than immediately on merge.
- More process/ceremony than a single-branch flow, for what is currently a solo project — the core reason GitHub Flow avoids long-lived branches (protecting a continuously-deployed `main` from drift) doesn't fully apply here, since nothing is continuously deployed. The batching benefit is real, but it's solving a smaller problem than in a typical always-deployed service; revisit if the extra branch proves to be ceremony without payoff.
