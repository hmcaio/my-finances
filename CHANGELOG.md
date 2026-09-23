# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html). Backend and frontend share a single project version — see [ADR 0007](docs/adr/0007-single-shared-semver-and-changelog.md) — released together as one git tag `vX.Y.Z`, which triggers CI (F014) to publish both Docker images at that version.

## [Unreleased]

### Added

- **F001 — Project Scaffolding** — Spring Boot backend and React/TypeScript/MUI frontend skeletons with routing and a dark-mode toggle, OpenAPI docs at `/swagger-ui.html`, and a local Postgres via Docker Compose with Flyway migrations. ([#1](https://github.com/hmcaio/my-finances/pull/1))
  - Upgrade: Flyway applies the migrations automatically on startup. `V10` adds UNIQUE constraints on category, payment-method and account names and positive-amount CHECKs, so startup fails on an existing dev database that violates them.
- **F002 — Categories & Payment Methods** — manage both, with starter lists. ([#3](https://github.com/hmcaio/my-finances/pull/3))
- **F003 — Account Management** — accounts of several types with an opening balance, a running balance, and closing. ([#5](https://github.com/hmcaio/my-finances/pull/5))
- **F004 — Transactions** — record, edit, delete and filter transactions (date range, category, account, payment method); account balances follow. ([#6](https://github.com/hmcaio/my-finances/pull/6))
- **F005 — Transfers** — move money between accounts, e.g. paying a credit card from checking, without double-counting spend. ([#7](https://github.com/hmcaio/my-finances/pull/7))
- **F006 — Budgets** — versioned monthly caps per expense category and budget-vs-actual reporting using each month's own cap. ([#8](https://github.com/hmcaio/my-finances/pull/8))
- **F007 — Recurring Templates** — versioned recurring bills, lazy catch-up generation of pending occurrences, and confirm/dismiss. ([#9](https://github.com/hmcaio/my-finances/pull/9))
- **F008 — Investment Accounts, Products & Taxonomy** — a new `Investment` account type for brokers and pension plans: no opening balance, no transactions or recurring templates (money will move through transfers), and it can only be closed once all its products are closed. Inside it you track investment products (name, category, optional sub-category), edited or closed, and deleted only while they have no history. Categories and sub-categories form a user-editable two-level list, seeded with Brazilian defaults (Fixed Income, Variable Income, Funds, Pension, International, Crypto, Other) and managed under "Investment Categories" in the sidebar. Snapshots, buy/sell transfers and the allocation view come with F009. ([#28](https://github.com/hmcaio/my-finances/pull/28))
  - Upgrade: migration `V13` allows the `INVESTMENT` account type, makes `accounts.opening_balance` and `opening_balance_date` nullable (a new CHECK requires them for every type except `INVESTMENT` and forbids them for it; existing accounts are untouched), creates the investment category, sub-category and product tables, and seeds the default categories and sub-categories. It runs automatically on startup. In the account API, `openingBalance`/`openingBalanceDate` become optional in the create request (`400` when present for `INVESTMENT` or missing for any other type) and nullable in the response.
- **F014 — CI/CD & Production Packaging** — production Docker images (backend; frontend served by nginx), `docker-compose.prod.yml`, and CI that publishes to GHCR on `main` and `vX.Y.Z` tags. ([#2](https://github.com/hmcaio/my-finances/pull/2))
- **F016 — Logging** — request-id access logging, logged unexpected errors, a capped rolling backend log file, a frontend logger with error capture and an error boundary. ([#21](https://github.com/hmcaio/my-finances/pull/21))
  - Upgrade: `docker-compose.prod.yml` adds the named volume `my-finances-logs-prod` (backend logs; `docker compose down -v` deletes it along with the database) and an optional `LOG_LEVEL` in `.env` (default `INFO`).
- **F017 — Institutions** — a shared, editable list of the banks and brokers your accounts sit at, with a built-in "No institution" row that can be renamed but not deleted. Every account now has an institution, chosen (or created on the spot) on the account form and shown on the account screens. ([#26](https://github.com/hmcaio/my-finances/pull/26))
  - Upgrade: migration `V12` creates the institution list with a built-in "No institution" row, converts each account's institution text into it (surrounding whitespace trimmed, names that differ only by case merged into one), assigns accounts that had none to "No institution" and drops the old column. It runs automatically on startup and is not reversible. The account API's `institution` field is replaced by a required `institutionId`.

### Changed

- Categories now have a built-in row per type, "Other Expense" and "Other Income", that can be renamed but not deleted, like "No institution". (closes [#29](https://github.com/hmcaio/my-finances/issues/29), [#30](https://github.com/hmcaio/my-finances/pull/30))
  - Upgrade: migration `V14` marks the existing "Other Income" and the seeded `Other` as built-in, renaming `Other` to "Other Expense" (kept as `Other` if an income category already uses that name). A seed row you renamed or deleted gets a fresh built-in row instead, so a category you renamed stays as an ordinary one. The category API's response gains a `builtIn` field.
- Loading states use skeleton placeholders instead of spinners, and a failed first load shows a "Could not load data" row with Retry. (closes [#16](https://github.com/hmcaio/my-finances/issues/16), [#17](https://github.com/hmcaio/my-finances/pull/17))
- Dev: pgAdmin now auto-registers the local Postgres server. ([#10](https://github.com/hmcaio/my-finances/pull/10))
  - Upgrade: pgAdmin imports its server list only into an empty volume. To apply it to an existing dev install, reset pgAdmin's volume (`docker compose down -v` also wipes the dev Postgres data).

### Fixed

- A malformed JSON body, an invalid enum value, or an unparseable id/date in the path or query string now returns 400 instead of 500, and is no longer logged as an ERROR. (closes [#19](https://github.com/hmcaio/my-finances/issues/19), [#24](https://github.com/hmcaio/my-finances/pull/24))
- Pending recurring occurrences are no longer duplicated when two requests trigger catch-up at the same time (e.g. the startup run overlapping a page load, or two tabs). (closes [#20](https://github.com/hmcaio/my-finances/issues/20), [#23](https://github.com/hmcaio/my-finances/pull/23))
  - Upgrade: migration `V11` removes existing duplicate pending occurrences (keeping the earliest of each template and due date) and adds a UNIQUE constraint on `(template_id, due_date)`; it runs automatically on startup.
- The Name fields on the Categories and Payment methods pages now stop at 100 characters, like the other settings pages, instead of letting you type past the limit and fail on save. (closes [#43](https://github.com/hmcaio/my-finances/issues/43))
- Creating a budget or recurring template, and closing an account, are now atomic, so a failure between steps no longer leaves a budget or template with no versions. (closes [#11](https://github.com/hmcaio/my-finances/issues/11), [#12](https://github.com/hmcaio/my-finances/pull/12))
- Default dates and months in the account, transaction, transfer, budget and recurring-template forms now use your local time instead of UTC, so they no longer jump to tomorrow (or next month) in the evening. (see [#32](https://github.com/hmcaio/my-finances/issues/32), [#33](https://github.com/hmcaio/my-finances/pull/33))
