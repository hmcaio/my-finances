# 0036. Run git hooks through the pre-commit framework, adding secret and SAST scanning

Status: Accepted
Date: 2026-11-17

## Context
The repo had one hook, `.githooks/pre-commit` (staged-aware Spotless / Prettier / ESLint), enabled by pointing `core.hooksPath` at it. Nothing scanned for committed secrets or insecure code patterns: CI covers dependency and image vulnerabilities (Trivy, [ADR 0031](0031-image-vulnerability-scan-gates-publishing.md)) but not source. Adding two more tools to a hand-written hook means reimplementing staging, stashing, tool installation and pinning per tool. [ADR 0009](0009-conventional-commits.md) expresses a preference for minimal tooling, so the cost of a new dependency is part of this decision.

## Decision
Use the [pre-commit](https://pre-commit.com) framework, configured in `.pre-commit-config.yaml`, installed per clone by `scripts/setup-hooks.sh` (which unsets the old `core.hooksPath=.githooks`, because pre-commit refuses to install while it is set).

- **pre-commit:** Betterleaks (`v1.10.1`, staged diff only) and `format-check`, the old hook's logic moved unchanged to `scripts/hooks/format-check.sh`.
- **pre-push:** Semgrep (`semgrep==1.185.0`) with explicit `p/default`, `p/java`, `p/javascript`, `p/typescript`, `p/react` packs and `--metrics=off`. `--config auto` is not used: Semgrep rejects it together with `--metrics=off`.
- **Betterleaks allowlist:** `.betterleaks.toml`, passed through `args` because Betterleaks does not auto-load it. It allows rule `generic-password` on named dev/test placeholder paths only. A full tree and history baseline scan found exactly those 9 tree and 11 history findings, and no other rule.
- **Semgrep baseline:** a full-repo scan found 38 findings that predate the hooks. [#294](https://github.com/hmcaio/my-finances/pull/294) fixed 36 (SHA-pinned Actions, a Dependabot cooldown, npm `min-release-age`, a literal regexp) and left 2 as a documented exception: `backup/Dockerfile` starts as root on purpose (`entrypoint.sh` drops to `BACKUP_UID:BACKUP_GID` with `gosu`, [ADR 0015](0015-automated-encrypted-backups-sidecar.md)), suppressed per line with `# nosemgrep` and the reason beside it. The tree was clean when the hooks landed, so pre-push blocks only new findings.
- Versions are pinned in `.pre-commit-config.yaml` and bumped by hand after reviewing the new tags (a malicious update to a scanner is a supply-chain risk).
- The hooks are deliberately **not** added to CI or `scripts/verify.sh`. This is an exception to "verify.sh is the one definition of the checks".

## Consequences
- Secrets and insecure patterns are caught before they leave the machine; the existing format check behaves as before.
- Every clone needs the `pre-commit` tool, and the first run needs network to build the hook environments. The clone's `core.hooksPath` is rewritten by the setup script.
- pre-commit stashes unstaged changes during a run, which touches bind-mounted source (dev hot reload, IDE watchers) when the tree is dirty.
- `git commit --no-verify` and `git push --no-verify` bypass everything and no CI check backs them up. Adding the hooks to CI later is a one-job change that would remove that gap.
- Betterleaks only scans staged diffs; existing code is covered by the one-off baseline scan, and a re-baseline is needed if the allowlist or packs change.
- A new finding in a pushed file blocks the push. Fix it, or suppress it on that line with `# nosemgrep: <full rule id>` and the reason; never exclude a rule repo-wide or a directory (that would also hide the workflow script-injection rules in `.github/workflows/`).
- Semgrep packs and rules are fetched from the registry at run time; metrics are off, but a pack update can change findings between runs.
