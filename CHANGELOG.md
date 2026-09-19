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
- **F014 — CI/CD & Production Packaging** — production Docker images (backend; frontend served by nginx), `docker-compose.prod.yml`, and CI that publishes to GHCR on `main` and `vX.Y.Z` tags. ([#2](https://github.com/hmcaio/my-finances/pull/2))
- **F016 — Logging** — request-id access logging, logged unexpected errors, a capped rolling backend log file, a frontend logger with error capture and an error boundary. ([#21](https://github.com/hmcaio/my-finances/pull/21))
  - Upgrade: `docker-compose.prod.yml` adds the named volume `my-finances-logs-prod` (backend logs; `docker compose down -v` deletes it along with the database) and an optional `LOG_LEVEL` in `.env` (default `INFO`).

### Changed

- Loading states use skeleton placeholders instead of spinners, and a failed first load shows a "Could not load data" row with Retry. (closes [#16](https://github.com/hmcaio/my-finances/issues/16), [#17](https://github.com/hmcaio/my-finances/pull/17))
- Dev: pgAdmin now auto-registers the local Postgres server. ([#10](https://github.com/hmcaio/my-finances/pull/10))
  - Upgrade: pgAdmin imports its server list only into an empty volume. To apply it to an existing dev install, reset pgAdmin's volume (`docker compose down -v` also wipes the dev Postgres data).

### Fixed

- Pending recurring occurrences are no longer duplicated when two requests trigger catch-up at the same time (e.g. the startup run overlapping a page load, or two tabs). (closes [#20](https://github.com/hmcaio/my-finances/issues/20))
  - Upgrade: migration `V11` removes existing duplicate pending occurrences (keeping the earliest of each template and due date) and adds a UNIQUE constraint on `(template_id, due_date)`; it runs automatically on startup.
- Creating a budget or recurring template, and closing an account, are now atomic, so a failure between steps no longer leaves a budget or template with no versions. (closes [#11](https://github.com/hmcaio/my-finances/issues/11), [#12](https://github.com/hmcaio/my-finances/pull/12))
