# 0004. Adopt Hexagonal Architecture, Domain-Driven Design, and Test-Driven Development for the backend

Status: Accepted
Date: 2026-09-12

## Context
Much of this system's complexity is business-rule complexity, not CRUD complexity: two-sided transfer balance updates, forward-only versioning on budgets and recurring templates, lazy/catch-up occurrence generation, delete-safety rules that differ by whether history exists, and a net worth formula composed across several entity clusters. This logic needs to be correct and independently testable, not entangled with Spring/JPA/Postgres specifics.

## Decision
- **Domain-Driven Design**: entity clusters map to aggregates/bounded contexts (Account + Transfer, Budget + BudgetVersion, RecurringTemplate + RecurringTemplateVersion, the Investment* cluster), each owning its own consistency rules.
- **Hexagonal Architecture**: domain/application logic sits behind ports, isolated from Spring Web/JPA/Postgres adapters — the backend package layout is layer-then-context (`domain/`, `application/`, `infrastructure/`, each with per-aggregate subpackages; see F001).
- **Test-Driven Development**: the rules-heavy logic (net worth calc, versioning resolution, recurring catch-up generation, budget-vs-actual by historical version) is driven by tests written first.

## Consequences
- Domain model classes are framework-free and hand-written (no Lombok there — see [0005](0005-single-point-uuid-generation.md)'s sibling convention on Lombok scope), keeping invariants enforceable at construction/mutation time.
- Cross-feature dependencies that would otherwise create tight coupling (e.g. F003 needing to notify F007 when an account closes) are expressed as ports/events instead of direct calls into another feature's package.
- More upfront structure than a typical CRUD-first Spring app, justified specifically because so much of this system (unlike a simple form-over-database app) is rules, not plumbing.
