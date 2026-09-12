# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project status

This repository is pre-implementation: it currently contains only `LICENSE` and `docs/PRD.md`. No backend, frontend, build files, or tests exist yet. There are no build/lint/test commands to document until the project is scaffolded — do not invent any.

## Source of truth

**Read [docs/PRD.md](docs/PRD.md) before making any architectural or scope decision.** It is the authoritative, detailed spec for this system — data model (entities, versioning rules, net worth formula), functional requirements, tech stack, and explicit non-goals. Do not re-derive product decisions from first principles; the PRD already resolved many non-obvious tradeoffs (e.g. why credit card spend is a liability-increasing expense and payments are transfers, why budgets/recurring templates are versioned instead of mutated in place, why recurring-occurrence generation is lazy/catch-up rather than a real-time scheduler).

## Planned architecture (from the PRD)

- **Backend**: Java + Spring Boot, built with Gradle; Flyway for schema migrations.
- **Frontend**: React + TypeScript, built with Vite.
- **Database**: PostgreSQL, run via Docker Compose for local dev.
- **Methodology**: Domain-Driven Design (entity clusters as aggregates — Account+Transfer, Budget+BudgetVersion, RecurringTemplate+RecurringTemplateVersion, the Investment* cluster), Hexagonal Architecture (domain/business logic isolated from Spring/JPA/Postgres behind ports), Test-Driven Design (tests-first for the rules-heavy logic: net worth calc, versioning, recurring catch-up generation).
- **Runtime model**: local, single-user, no auth, bound to `localhost` only, run on-demand (brought up/down by the user) rather than kept always-on — this is why recurring-template generation must be catch-up-based, not cron-based.

When code is added, update this file with actual build/lint/test commands and any structural conventions that emerge (package layout, module boundaries) rather than leaving this section abstract.
