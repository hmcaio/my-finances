# 0005. Generate all entity IDs as UUIDs from a single domain-layer IdGenerator port

Status: Accepted
Date: 2026-09-13

## Context
Entity IDs need to be UUIDs, generated from a single point rather than each entity/table picking its own mechanism (e.g. scattered `@GeneratedValue` annotations, or ad hoc `UUID.randomUUID()` calls wherever an entity happens to be constructed). Under Hexagonal Architecture ([0004](0004-hexagonal-ddd-tdd.md)), the domain layer shouldn't need a database round-trip to know an aggregate's own identity — a DB-generated default id means the id doesn't exist until after the first save, which complicates testing domain logic and cross-aggregate references before persistence.

## Decision
A single `IdGenerator` port lives in `domain/shared` (`UUID newId()`), with exactly one implementation — `RandomUuidGenerator`, backed by `UUID.randomUUID()` — registered as a Spring bean. The application-layer use case calls `idGenerator.newId()` and passes the id into the aggregate's factory method when creating it. JPA entities never use `@GeneratedValue`; the id is already assigned by the time the entity is mapped for persistence.

## Consequences
- Every new-entity creation path in every feature follows the same pattern, established once in F001 and reused by F002–F013.
- Domain logic and unit tests can construct aggregates with real ids without touching a database.
- Lombok is not used on domain classes for the same reason a single generation point matters — a generated all-args constructor would let code bypass the intended "always call the factory method with an id from IdGenerator" path. Lombok stays scoped to infrastructure-layer JPA entities and DTOs.
