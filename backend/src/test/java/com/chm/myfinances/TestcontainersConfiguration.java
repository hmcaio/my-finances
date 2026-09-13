package com.chm.myfinances;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Every backend test (local {@code ./gradlew test} or CI) gets its own real, ephemeral Postgres
 * container via Testcontainers instead of depending on a manually-provisioned database — see ADR
 * 0010. {@code @ServiceConnection} auto-configures Spring's datasource to point at the container,
 * so no {@code application-test.yml} datasource block is needed.
 *
 * <p>Uses {@code org.testcontainers.postgresql.PostgreSQLContainer} (Testcontainers 2.x), not the
 * older {@code org.testcontainers.containers.PostgreSQLContainer} — the latter is retained only for
 * backward compatibility and is marked {@code @Deprecated} in 2.x.
 */
@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

  // Same postgres:17-alpine tag used by docker-compose.yml/docker-compose.prod.yml/CI, so the
  // version under test always matches dev and prod.
  @Bean
  @ServiceConnection
  PostgreSQLContainer postgresContainer() {
    return new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));
  }
}
