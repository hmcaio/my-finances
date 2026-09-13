# Features

Each feature below has its own folder with a `spec.md` (technical specification) and `plan.md` (action plan). All features derive from [../PRD.md](../PRD.md) — read that first for the full data model and rationale; these docs only add implementation-level detail and don't repeat product reasoning already settled there.

| Feature | Summary |
|---|---|
| [F001 — Project Scaffolding](F001-project-scaffolding/spec.md) | Gradle Spring Boot backend skeleton (hexagonal layout), Vite/React/TS frontend skeleton, Docker Compose (Postgres), Flyway baseline, shared conventions (IdGenerator, AuditableEntity, Lombok policy). |
| [F002 — Categories & Payment Methods](F002-categories-payment-methods/spec.md) | CRUD for both (PRD §5.1, §5.2), starter lists, delete/reassignment handling. |
| [F003 — Account Management](F003-account-management/spec.md) | Account CRUD, types, opening balance, running balance, closing (PRD §5.4, §6.2 minus transfers). |
| [F004 — Transactions](F004-transactions/spec.md) | Transaction CRUD, filtering, running-balance impact (PRD §5.3, §6.1). |
| [F005 — Transfers](F005-transfers/spec.md) | Transfers between accounts, two-sided balance update (PRD §5.5, §6.2 transfer part). |
| [F006 — Budgets](F006-budgets/spec.md) | Versioned monthly caps, budget-vs-actual (PRD §5.6, §6.4). |
| [F007 — Recurring Templates](F007-recurring-templates/spec.md) | Versioned templates, lazy/catch-up generation, confirm flow (PRD §5.7, §6.5). |
| [F008 — Investment Accounts & Products](F008-investment-accounts-products/spec.md) | Investment account/category/product CRUD, close-instead-of-delete (PRD §5.8 entities, §6.6 minus logs/snapshots). |
| [F009 — Investment Buy/Sell & Snapshots](F009-investment-buysell-snapshots/spec.md) | Buy/sell log, manual value snapshots, allocation-by-category view (PRD §5.8 logs/snapshots, §6.6 remaining). |
| [F010 — Net Worth](F010-net-worth/spec.md) | Net worth formula and trend series (PRD §5.9). |
| [F011 — Onboarding](F011-onboarding/spec.md) | First-run flow to create the first account (PRD §6.7). |
| [F012 — Dashboard](F012-dashboard/spec.md) | Aggregated dashboard widgets (PRD §6.8). |
| [F013 — Data Export](F013-data-export/spec.md) | All-entity ZIP/CSV export with filters (PRD §6.9). |
| [F014 — CI/CD & Production Packaging](F014-cicd-production-packaging/spec.md) | Docker production images (backend/frontend), `docker-compose.prod.yml`, GitHub Actions CI/CD publishing to GHCR — separate from and non-disruptive to F001's dev workflow. |

## Cross-cutting conventions (defined in F001, applied everywhere)

- **IDs**: UUID, generated from a single point — a domain-layer `IdGenerator` port with one `RandomUuidGenerator` adapter, injected wherever a new aggregate is created. No entity generates its own id ad hoc, and no `@GeneratedValue` on JPA entities.
- **Auditing**: every persisted entity extends a shared `AuditableEntity` (`@MappedSuperclass`, `@EntityListeners(AuditingEntityListener.class)`) with `createdAt`/`lastModifiedAt` as `Instant`, populated via `@EnableJpaAuditing`. Applies to persistence-layer JPA entities, not domain model classes.
- **Lombok**: allowed on infrastructure-layer JPA entities and DTOs. Not used on domain model classes (aggregates/entities/value objects) — those keep hand-written constructors/factory methods so invariants can't be bypassed.
- **Repo layout**: `/backend` (Gradle, layer-then-context packages: `domain/`, `application/`, `infrastructure/`, each with subpackages per aggregate) and `/frontend` (Vite + React + TypeScript) at the repository root, alongside `docs/`.

Each feature's `spec.md`/`plan.md` has explicit **Backend** and **Frontend** sections, and `plan.md` lists which other features (`FXXX`) it depends on.
