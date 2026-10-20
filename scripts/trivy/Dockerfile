# The vulnerability scanner the `scan` stage of scripts/verify.sh runs (CI's build-and-push job and
# your machine use this one pin; docs/conventions/tooling.md). Never built: it exists so Dependabot's
# docker ecosystem sees the pin and proposes bumps, and verify.sh reads the FROM reference from here.
#
# Pinned by tag AND digest on purpose. In March 2026 Trivy's release tags, its GitHub Action and
# some registry tags were hijacked (GHSA-69fq-xp46-6x23); a digest cannot be moved. We also run the
# image directly instead of through aquasecurity/trivy-action, so no third-party action code runs
# with the job's `packages: write` token. Bump: take the new tag and its digest together
# (`docker buildx imagetools inspect aquasec/trivy:<tag>`), or let Dependabot do it.
FROM aquasec/trivy:0.76.0@sha256:2416d1f74045b57274df1a340eee6b510cacd73c66a6ccb8da092b117eb7d222
