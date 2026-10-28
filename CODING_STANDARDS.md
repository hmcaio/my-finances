# Coding standards (read at review)

The review checklist for a diff in this repo. Read it after the code is written and before calling it done; it holds the judgement calls a linter cannot make. The build already fails on the mechanical rules (ArchUnit in `ArchitectureTest`/`TestConventionsTest`, ESLint `no-restricted-imports`/`no-console`, `scripts/check-versions.sh`, Spotless/Prettier, the TypeScript build), so skip those and spend attention here. Each item names where its mechanics live.

## Fit

- **Match the surrounding code**: the neighbouring aggregate, feature folder or test is the template for naming, package placement and shape (backend shapes: `docs/conventions/backend-structure.md`; frontend layout: `docs/conventions/frontend-structure.md`).
- **Reuse before adding**:
  - Frontend: the shared components (`docs/conventions/frontend-components.md`, `frontend-responsive.md`) instead of re-inlined markup; `MonthPicker`, `DataTableBody`, `ConfirmDialog`, `ResponsiveTable`/`ResponsiveDialog`; `donutGeometry.ts` + `FlatAllocationDonutChart` for a new donut (`frontend-charts.md`); `formatMoney`/`formatNumber`/`formatDate` instead of `.toFixed()`.
  - Backend: `TextFieldConstraints` constants for every text limit, the existing ports/queries for a new report, an existing exception (and its `type` message) before a new one.
- **A new exception to a rule is stated where the rule lives** (the `data-constraints.md` list, the conventions file, an ADR), with its reason. An unexplained deviation from a neighbour is a review finding.
- **Point at a file, never at a heading**: a code comment, spec, plan or skill that cites a rule names the path (`docs/conventions/<topic>.md`), an ADR id or the class of the check that enforces it, not "CLAUDE.md's Testing section". Headings move silently when a file is reorganised; a path breaks loudly and can be grepped. A comment that cites a moved or removed rule is a review finding.

## Changes that travel together

A diff that touches the left side is incomplete without the right side.

- New `@ResponseStatus` exception, or a new `type`: a `PROBLEM_TYPE_MESSAGES` entry plus its `errors` translation in both locales; `@ApiResponse` docs listing the `type` on the controller method (`docs/conventions/errors.md`).
- New or changed controller endpoint: regenerated `schema.ts`, an MSW handler (with a `409` variant where it can conflict), an explicit `200` `@ApiResponse` when only error responses are documented.
- New write use case: `@Transactional` (every externally reachable overload) and an `AuditRecorder` call in the same transaction (`docs/conventions/transactions-and-audit.md`).
- New table with an FK to `accounts`: the `AccountUsageChecker` and its test.
- New text or amount field: bounded and checked at every layer (domain, DTO, column, frontend `maxLength`) with the rule in root `CLAUDE.md` and the constants in `docs/conventions/data-constraints.md`.
- New translation namespace: `i18n.ts`, `i18next.d.ts` and both locale folders; every user-visible string through `t()` (`docs/conventions/frontend-i18n.md`).
- New seeded row in a shared test fixture or migration: re-check every exact-count assertion and name collision (`docs/conventions/backend-testing.md`, `frontend-testing.md`).
- A native or JPQL query that restates a domain rule in SQL (a sign, an as-of filter): a parity test running it against the Java rule on randomized data (`AccountLedgerActivityParityTest`), and a note on both copies saying to change them together.
- Pinned tool version: every sibling file (`docs/conventions/tooling.md`).
- User-visible `feat`/`fix`: a `CHANGELOG.md` entry in the same commit (root `CLAUDE.md`, Workflow).

## Persistence reads

- **An N+1 read is a finding.** A repository adapter that maps a list or page fetches each kind of child in one query for the whole result (`findBy...In(ids)`, grouped in memory), never once per element: issue #189 was 5,001 trade-line queries for 5,000 transfers. The same goes for an application query that loops a repository call over rows or accounts: pass the list to a bulk method.
- **A new collection read carries a `StatementCount` test**: the count with a little data equals the count with much more (`docs/conventions/backend-testing.md`). A fixed cap alone passes a read that runs a few queries per element.

## Behaviour preserved across a refactor

**Splitting one method that enforced a cross-cutting invariant unconditionally into several entry points drops that invariant from whichever entry point doesn't obviously need it, unless it is re-applied to each one.** A design doc's "unchanged" claim about the other path is no substitute for rereading the old method's full body line by line. Example: F009/F022's single `TransferService.create`/`edit` ran one `requireValidInvestmentShape` on every call, rejecting a plain (no-product) transfer that touched an `INVESTMENT` account. F027 split that into `create`/`edit` (plain) vs `createTradeConfirmation`/`editTradeConfirmation`, and the equivalent guard (`requireInvestmentShape`) was written only for the new confirmation path: the plain path silently lost it, and no test caught the regression for the rest of that feature's lifetime (issue #113). The fix (`requirePlainShape`) re-derives that one rule from the pre-split method rather than from the split's own stated intent.

## Logs and data exposure

- A log line carries ids and counts only: no amounts, descriptions/notes, account/category/payment-method names, opening balances, usernames, passwords, hashes or client addresses, in either stack (ADR 0011). Reject an exception message or a request/response body added to a log call, a log inside a loop over a table, and any enabling of Hibernate bind-parameter logging (`docs/conventions/logging.md`).
- `detail` stays null in every error body; nothing exception-derived reaches a response.

## Frontend behaviour

- Content that must fit 360px has no fixed non-shrinking width (`docs/conventions/frontend-responsive.md`).
- Loading uses a delayed `Skeleton`, not a spinner or "Loading..." text (`docs/conventions/frontend-data-loading.md`).
- A new data-fetching top-level component sits inside `AuthenticatedApp` (`docs/conventions/auth.md`).

## Steering files

A rule added to a `CLAUDE.md` is one that cannot be derived from the code and would cost time to rediscover, and it is not already enforced by a check. Stories (what changed, why) belong in the commit/PR body or the feature's `spec.md`. See "Keeping these files useful" in root `CLAUDE.md`.
