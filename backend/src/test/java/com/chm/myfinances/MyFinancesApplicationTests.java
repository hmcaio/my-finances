package com.chm.myfinances;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Scaffolding-level smoke test (F001/ADR 0010): proves Spring Boot, Flyway's {@code
 * V1__baseline.sql}, and the Testcontainers-provisioned Postgres container all wire up together end
 * to end. Real feature tests (net worth calc, versioning, recurring catch-up, ...) start with
 * F002+.
 */
@Tag("integration")
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class MyFinancesApplicationTests {

  @Test
  void contextLoads() {}
}
