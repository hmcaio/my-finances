package com.chm.myfinances.infrastructure.persistence.investmentsegment;

import static org.assertj.core.api.Assertions.assertThat;

import com.chm.myfinances.TestcontainersConfiguration;
import com.chm.myfinances.testsupport.migration.AbstractMigrationTest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Proves {@code V24__seed_investment_segments.sql} against pre-existing data (backend {@code
 * CLAUDE.md}'s migration-testing pattern, F034 spec/ADR 0028): the fresh table (empty since V20)
 * ends up with exactly the nine seeded B3 FII sector names and nothing else, and an
 * already-existing, unrelated segment row (the only kind of pre-existing data this table could have
 * before the lockdown) is left untouched. Same Flyway-by-hand-into-a-throwaway-schema setup as
 * {@code FiiPortfolioMigrationTest}.
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

  @Test
  void aPreExistingCustomSegmentIsLeftUntouched() throws Exception {
    flyway("23").migrate();
    UUID existingId;
    try (Connection connection = connection()) {
      existingId = insertSegment(connection, "Pre-existing Custom Segment Test");
    }

    flyway("24").migrate();

    try (Connection connection = connection()) {
      assertThat(count(connection, "SELECT count(*) FROM investment_segments")).isEqualTo(10);
      assertThat(
              count(
                  connection,
                  "SELECT count(*) FROM investment_segments WHERE id = '" + existingId + "'"))
          .isEqualTo(1);
    }
  }

  private static UUID insertSegment(Connection connection, String name) throws SQLException {
    UUID id = UUID.randomUUID();
    try (PreparedStatement statement =
        connection.prepareStatement(
            "INSERT INTO investment_segments (id, name, created_at, last_modified_at)"
                + " VALUES (?, ?, now(), now())")) {
      statement.setObject(1, id);
      statement.setString(2, name);
      statement.executeUpdate();
    }
    return id;
  }
}
