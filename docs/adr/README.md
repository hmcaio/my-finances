# Architectural Decision Records

One file per decision, numbered sequentially, never renumbered or edited to change the decision — a decision that's later reversed gets a new ADR, and the old one's status is updated to point at it.

| ADR | Title | Status |
|---|---|---|
| [0001](0001-account-model-and-transfers.md) | Model accounts with explicit types and transfers instead of a flat category-only ledger | Accepted |
| [0002](0002-versioned-budget-and-recurring-template.md) | Version Budget and RecurringTemplate instead of mutating in place | Accepted |
| [0003](0003-lazy-catchup-recurring-generation.md) | Generate recurring occurrences lazily/catch-up instead of real-time scheduling | Accepted |
| [0004](0004-hexagonal-ddd-tdd.md) | Adopt Hexagonal Architecture, Domain-Driven Design, and Test-Driven Development for the backend | Accepted |
| [0005](0005-single-point-uuid-generation.md) | Generate all entity IDs as UUIDs from a single domain-layer IdGenerator port | Accepted |
| [0006](0006-separate-prod-packaging-from-dev.md) | Keep production Docker packaging separate from local dev tooling | Accepted |
| [0007](0007-single-shared-semver-and-changelog.md) | Single shared SemVer version across backend and frontend, with a root CHANGELOG | Accepted |
| [0008](0008-github-flow-with-develop-branch.md) | Branching strategy: GitHub Flow with an added long-lived `develop` branch | Accepted |
| [0009](0009-conventional-commits.md) | Use Conventional Commits, mapped to CHANGELOG categories and SemVer bump type | Accepted |
| [0010](0010-testcontainers-for-backend-tests.md) | Use Testcontainers for backend tests instead of a fixed CI Postgres service container | Accepted |
| [0011](0011-logging-slf4j-request-id.md) | Logging: SLF4J/Logback + a request id; no aggregator | Accepted |

Template:
```
# NNNN. Title

Status: Accepted | Superseded by NNNN | Deprecated
Date: YYYY-MM-DD

## Context
## Decision
## Consequences
```
