# Backend

Spring Boot REST API for My Finances: Java, Gradle, PostgreSQL via Flyway. Hexagonal Architecture + Domain-Driven Design ([ADR 0004](../docs/adr/0004-hexagonal-ddd-tdd.md)) — `domain/` is framework-free business logic and repository ports, `application/` holds use-case services, `infrastructure/` is everything that talks to Spring, JPA or the outside world.

See [CLAUDE.md](CLAUDE.md) for the full command list, package layout, error/transaction conventions and testing setup. The product spec (data model, functional requirements, non-goals) is [docs/PRD.md](../docs/PRD.md); every non-obvious architectural call is an [ADR](../docs/adr/).

## Running it

Needs a local Postgres (`docker compose up -d` from the repo root) and a JDK matching `backend/Dockerfile`'s `eclipse-temurin` version.

```
./gradlew bootRun    # API at http://localhost:8080 (needs AUTH_USERNAME / AUTH_PASSWORD_HASH in the environment)
```

Swagger UI: `http://localhost:8080/swagger-ui.html`. OpenAPI spec (consumed by the frontend's type generation): `http://localhost:8080/v3/api-docs`. Both need a session (F036): sign in through the app first, then open them in the same browser.

Running the whole stack in containers instead (`docker compose --profile full up -d`, the default dev path) is documented in the repo root [README.md](../README.md).

## Testing

```
./gradlew test            # domain, application (fakes) and ArchUnit — no Docker
./gradlew integrationTest # every @SpringBootTest — needs Docker (Testcontainers starts its own Postgres)
./gradlew spotlessCheck   # google-java-format; spotlessApply to fix
```

Two tiers by design (ADR 0013, ADR 0010): fast fakes for business logic, real Postgres only for what actually needs it. `backend/CLAUDE.md`'s Testing section has the fixture/seed-data gotchas that aren't obvious from the test code alone.
