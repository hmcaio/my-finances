package com.chm.myfinances.infrastructure.persistence.investmentsegment;

import static org.assertj.core.api.Assertions.assertThat;

import com.chm.myfinances.TestcontainersConfiguration;
import com.chm.myfinances.testsupport.migration.AbstractMigrationTest;
import java.sql.Connection;
import java.util.Set;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Proves {@code V24__seed_investment_segments.sql} (F034 spec/ADR 0028): the fresh table (empty
 * since V20) ends up with exactly the nine seeded B3 FII sector names and nothing else. The test of
 * the seed leaving a pre-existing custom segment untouched was retired (issue #89, B6). Runs Flyway
 * by hand in its own schema of the shared Testcontainers Postgres (see {@link
 * AbstractMigrationTest}).
 */
@Tag("integration")
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class InvestmentSegmentSeedMigrationTest extends AbstractMigrationTest {

  private static final String SCHEMA = "migration_test_investment_segment_seed";

  private static final Set<String> SEEDED_NAMES =
      Set.of(
          "Lajes Corporativas",
          "Shoppings",
          "Logística",
          "Papel (Recebíveis)",
          "Híbrido",
          "Fiagro",
          "Hospitalar",
          "Residencial",
          "Outros");

  InvestmentSegmentSeedMigrationTest() {
    super(SCHEMA);
  }

  @Test
  void aFreshTableEndsUpWithExactlyTheNineSeededNames() throws Exception {
    flyway("23").migrate();

    flyway("24").migrate();

    try (Connection connection = connection()) {
      assertThat(count(connection, "SELECT count(*) FROM investment_segments")).isEqualTo(9);
      assertThat(namesFrom(connection, "SELECT name FROM investment_segments"))
          .isEqualTo(SEEDED_NAMES);
    }
  }
}
