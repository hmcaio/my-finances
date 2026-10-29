package com.chm.myfinances.infrastructure.persistence.investmentsegment;

import static org.assertj.core.api.Assertions.assertThat;

import com.chm.myfinances.TestcontainersConfiguration;
import com.chm.myfinances.testsupport.migration.AbstractMigrationTest;
import java.sql.Connection;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Proves {@code V24__seed_investment_segments.sql} (F034 spec/ADR 0028): the fresh table (empty
 * since V20) ends up with exactly the nine seeded B3 FII sector names and nothing else; and {@code
 * V26__expand_investment_taxonomy_seeds.sql} (issue #227) adds five more without touching those
 * nine and without duplicating a name that already exists. The test of the seed leaving a
 * pre-existing custom segment untouched was retired (issue #89, B6). Runs Flyway by hand in its own
 * schema of the shared Testcontainers Postgres (see {@link AbstractMigrationTest}).
 */
@Tag("integration")
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class InvestmentSegmentSeedMigrationTest extends AbstractMigrationTest {

  private static final String SCHEMA = "migration_test_investment_segment_seed";

  private static final Set<String> V24_NAMES =
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

  private static final Set<String> V26_NAMES =
      Set.of("Fundo de Fundos", "Hotel", "Agências Bancárias", "Educacional", "Desenvolvimento");

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
          .isEqualTo(V24_NAMES);
    }
  }

  @Test
  void theExpansionAddsFiveSegmentsAndKeepsTheNineExistingOnes() throws Exception {
    flyway("25").migrate();

    flyway("26").migrate();

    Set<String> expected = new HashSet<>(V24_NAMES);
    expected.addAll(V26_NAMES);
    try (Connection connection = connection()) {
      assertThat(count(connection, "SELECT count(*) FROM investment_segments")).isEqualTo(14);
      assertThat(namesFrom(connection, "SELECT name FROM investment_segments")).isEqualTo(expected);
    }
  }

  @Test
  void theExpansionSkipsASegmentThatAlreadyExists() throws Exception {
    flyway("25").migrate();
    try (Connection connection = connection();
        Statement statement = connection.createStatement()) {
      statement.executeUpdate(
          "INSERT INTO investment_segments (id, name, created_at, last_modified_at)"
              + " VALUES (gen_random_uuid(), 'Hotel', now(), now())");
    }

    flyway("26").migrate();

    try (Connection connection = connection()) {
      assertThat(count(connection, "SELECT count(*) FROM investment_segments")).isEqualTo(14);
      assertThat(count(connection, "SELECT count(*) FROM investment_segments WHERE name = 'Hotel'"))
          .isEqualTo(1);
    }
  }
}
