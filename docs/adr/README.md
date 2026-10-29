# Architectural Decision Records

One file per decision, numbered sequentially, never renumbered or edited to change the decision — a decision that's later reversed gets a new ADR, and the old one's status is updated to point at it.

| ADR | Title | Status |
|---|---|---|
| [0001](0001-account-model-and-transfers.md) | Model accounts with explicit types and transfers instead of a flat category-only ledger | Accepted (amended by [0012](0012-investments-as-accounts-and-transfers.md)) |
| [0002](0002-versioned-budget-and-recurring-template.md) | Version Budget and RecurringTemplate instead of mutating in place | Accepted |
| [0003](0003-lazy-catchup-recurring-generation.md) | Generate recurring occurrences lazily/catch-up instead of real-time scheduling | Accepted |
| [0004](0004-hexagonal-ddd-tdd.md) | Adopt Hexagonal Architecture, Domain-Driven Design, and Test-Driven Development for the backend | Accepted |
| [0005](0005-single-point-uuid-generation.md) | Generate all entity IDs as UUIDs from a single domain-layer IdGenerator port | Accepted |
| [0006](0006-separate-prod-packaging-from-dev.md) | Keep production Docker packaging separate from local dev tooling | Accepted (partly superseded by [0018](0018-opt-in-full-stack-dev-compose-profile.md); publish trigger amended by [0032](0032-github-flow-main-only.md)) |
| [0007](0007-single-shared-semver-and-changelog.md) | Single shared SemVer version across backend and frontend, with a root CHANGELOG | Accepted |
| [0008](0008-github-flow-with-develop-branch.md) | Branching strategy: GitHub Flow with an added long-lived `develop` branch | Superseded by [0032](0032-github-flow-main-only.md) |
| [0009](0009-conventional-commits.md) | Use Conventional Commits, mapped to CHANGELOG categories and SemVer bump type | Accepted (branch workflow in its Consequences amended by [0032](0032-github-flow-main-only.md)) |
| [0010](0010-testcontainers-for-backend-tests.md) | Use Testcontainers for backend tests instead of a fixed CI Postgres service container | Accepted |
| [0011](0011-logging-slf4j-request-id.md) | Logging: SLF4J/Logback + a request id; no aggregator | Accepted |
| [0012](0012-investments-as-accounts-and-transfers.md) | Model investment accounts as accounts and buys/sells as transfers | Accepted (amended by [0020](0020-investment-holdings-many-to-many.md), [0024](0024-trade-confirmations-as-multi-line-transfers.md), [0033](0033-opening-position-as-cashless-holding-start.md)) |
| [0013](0013-two-tier-backend-test-strategy.md) | Backend test strategy: two tiers, real Postgres for persistence, fakes for logic | Accepted |
| [0014](0014-archunit-for-architecture-rules.md) | Enforce architecture rules with ArchUnit, with `Transaction` depending on `CategoryType` as a documented exception | Accepted |
| [0015](0015-automated-encrypted-backups-sidecar.md) | Automated, encrypted backups run by a sidecar container in the prod stack | Accepted (image base amended: Alpine + Postgres client, not the `postgres` image, #204) |
| [0016](0016-tanstack-query-client-cache.md) | Client-side query cache with TanStack Query and coarse invalidation; no server cache until measured | Accepted |
| [0017](0017-delete-accounts-with-no-history.md) | Allow hard-deleting an account that has no history | Accepted (example amended by [0020](0020-investment-holdings-many-to-many.md)) |
| [0018](0018-opt-in-full-stack-dev-compose-profile.md) | Opt-in `full` compose profile runs the whole stack in dev | Accepted |
| [0019](0019-responsive-layout-strategy.md) | Responsive layout: MUI breakpoints, cards over tables, shared primitives, geometry-only e2e | Accepted |
| [0020](0020-investment-holdings-many-to-many.md) | Model investment products and accounts as many-to-many holdings, not a 1:1 link | Accepted (amends [0012](0012-investments-as-accounts-and-transfers.md), [0017](0017-delete-accounts-with-no-history.md)) |
| [0021](0021-fuel-details-on-transaction.md) | Record fuel purchases as Transaction + optional FuelDetails, gated by a dedicated category flag | Accepted |
| [0022](0022-audit-log-explicit-port-same-transaction.md) | Record an audit log through an explicit application-layer port, in the same transaction as the change | Accepted |
| [0023](0023-fii-allocation-plan-and-dividends.md) | Generalize ticker/segment on InvestmentProduct; version the FII allocation plan like a budget; link dividends to Transaction like fuel details | Accepted |
| [0024](0024-trade-confirmations-as-multi-line-transfers.md) | Model a buy/sell as a multi-line Trade Confirmation, with backend-derived settlement amount | Accepted (amends [0012](0012-investments-as-accounts-and-transfers.md); amended by [0033](0033-opening-position-as-cashless-holding-start.md)) |
| [0025](0025-investment-split-as-read-side-adjustment.md) | Model a stock/FII split as a product-level event, applied as a read-side quantity adjustment | Accepted |
| [0026](0026-structured-error-responses-rfc9457.md) | Replace status-code-only error responses with RFC 9457 Problem Details bodies, `type` auto-derived from exception class name | Accepted |
| [0027](0027-frontend-i18n-react-i18next.md) | Frontend-only i18n with react-i18next: client-only locale, English + Brazilian Portuguese, locale-aware formatting | Accepted |
| [0028](0028-fixed-taxonomy-values-and-onboarding-seeded-categories.md) | Lock PaymentMethod/InvestmentCategory/InvestmentSubcategory/InvestmentSegment to fixed system values, close Institution's rename gap, and seed Category defaults through onboarding instead of migration | Accepted |
| [0029](0029-session-auth-and-generic-network-binding.md) | Session-cookie authentication with a single shared Argon2-hashed credential, and a generic `BIND_ADDR`-based private-network binding not pinned to any specific VPN/overlay product | Accepted |
| [0030](0030-postgres-unaccent-for-accent-insensitive-search.md) | Postgres `unaccent` extension for accent-insensitive text search (first extension the app depends on) | Accepted |
| [0031](0031-image-vulnerability-scan-gates-publishing.md) | Scan container images for vulnerabilities with a pinned Trivy image and gate publishing on the result | Accepted (publish trigger amended by [0032](0032-github-flow-main-only.md)) |
| [0032](0032-github-flow-main-only.md) | Plain GitHub Flow: `main` is the only long-lived branch, and images publish on release tags only | Accepted (supersedes [0008](0008-github-flow-with-develop-branch.md); amends the publish trigger of [0006](0006-separate-prod-packaging-from-dev.md), [0031](0031-image-vulnerability-scan-gates-publishing.md)) |
| [0033](0033-opening-position-as-cashless-holding-start.md) | Record a holding's starting quantity and cost basis as a cashless opening position | Accepted (amends [0012](0012-investments-as-accounts-and-transfers.md), [0024](0024-trade-confirmations-as-multi-line-transfers.md)) |

Template:
```
# NNNN. Title

Status: Accepted | Superseded by NNNN | Deprecated
Date: YYYY-MM-DD

## Context
## Decision
## Consequences
```
