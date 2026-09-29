package com.chm.myfinances;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Smoke test (F001/ADR 0010): proves Spring Boot, every Flyway migration, and the
 * Testcontainers-provisioned Postgres container all wire up together end to end.
 */
@Tag("integration")
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class MyFinancesApplicationTests {

  @Test
  void contextLoads() {}
}
