# Architectural Decision Records

One file per decision, numbered sequentially, never renumbered or edited to change the decision — a decision that's later reversed gets a new ADR, and the old one's status is updated to point at it.

| ADR | Title | Status |
|---|---|---|
| [0001](0001-account-model-and-transfers.md) | Model accounts with explicit types and transfers instead of a flat category-only ledger | Accepted (amended by [0012](0012-investments-as-accounts-and-transfers.md)) |
| [0002](0002-versioned-budget-and-recurring-template.md) | Version Budget and RecurringTemplate instead of mutating in place | Accepted |
| [0003](0003-lazy-catchup-recurring-generation.md) | Generate recurring occurrences lazily/catch-up instead of real-time scheduling | Accepted |
| [0004](0004-hexagonal-ddd-tdd.md) | Adopt Hexagonal Architecture, Domain-Driven Design, and Test-Driven Development for the backend | Accepted |
| [0005](0005-single-point-uuid-generation.md) | Generate all entity IDs as UUIDs from a single domain-layer IdGenerator port | Accepted |
| [0006](0006-separate-prod-packaging-from-dev.md) | Keep production Docker packaging separate from local dev tooling | Accepted (partly superseded by [0018](0018-opt-in-full-stack-dev-compose-profile.md)) |
| [0007](0007-single-shared-semver-and-changelog.md) | Single shared SemVer version across backend and frontend, with a root CHANGELOG | Accepted |
| [0008](0008-github-flow-with-develop-branch.md) | Branching strategy: GitHub Flow with an added long-lived `develop` branch | Accepted |
| [0009](0009-conventional-commits.md) | Use Conventional Commits, mapped to CHANGELOG categories and SemVer bump type | Accepted |
| [0010](0010-testcontainers-for-backend-tests.md) | Use Testcontainers for backend tests instead of a fixed CI Postgres service container | Accepted |
| [0011](0011-logging-slf4j-request-id.md) | Logging: SLF4J/Logback + a request id; no aggregator | Accepted |
| [0012](0012-investments-as-accounts-and-transfers.md) | Model investment accounts as accounts and buys/sells as transfers | Accepted |
| [0013](0013-two-tier-backend-test-strategy.md) | Backend test strategy: two tiers, real Postgres for persistence, fakes for logic | Accepted |
| [0014](0014-archunit-for-architecture-rules.md) | Enforce architecture rules with ArchUnit, with `Transaction` depending on `CategoryType` as a documented exception | Accepted |
| [0015](0015-automated-encrypted-backups-sidecar.md) | Automated, encrypted backups run by a sidecar container in the prod stack | Accepted |
| [0016](0016-tanstack-query-client-cache.md) | Client-side query cache with TanStack Query and coarse invalidation; no server cache until measured | Accepted |
| [0017](0017-delete-accounts-with-no-history.md) | Allow hard-deleting an account that has no history | Accepted |
| [0018](0018-opt-in-full-stack-dev-compose-profile.md) | Opt-in `full` compose profile runs the whole stack in dev | Accepted |

Template:
```
# NNNN. Title

Status: Accepted | Superseded by NNNN | Deprecated
Date: YYYY-MM-DD

## Context
## Decision
## Consequences
```
