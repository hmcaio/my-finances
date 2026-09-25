package com.chm.myfinances.infrastructure.persistence.budget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chm.myfinances.TestcontainersConfiguration;
import com.chm.myfinances.testsupport.migration.AbstractMigrationTest;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Proves {@code V16__budget_version_tombstone.sql} (issue #61): a budget version that predates it
 * keeps its cap, {@code monthly_cap} is now nullable (the tombstone), and the replaced {@code
 * CHECK} still rejects a zero or negative cap.
 *
 * <p>Runs Flyway by hand in its own schema of the shared Testcontainers Postgres (see {@link
 * AbstractMigrationTest}).
 */
@Tag("integration")
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class BudgetTombstoneMigrationTest extends AbstractMigrationTest {

  private static final String SCHEMA = "migration_test_budget_tombstone";

  BudgetTombstoneMigrationTest() {
    super(SCHEMA);
  }

  @Test
  void existingVersionsKeepTheirCapAndTheColumnBecomesNullable() throws Exception {
    flyway("15").migrate();
    UUID versionId = UUID.randomUUID();
    try (Connection connection = connection()) {
      assertThat(isNullable(connection, "budget_versions", "monthly_cap")).isFalse();
      UUID budgetId = insertBudget(connection);
      insertVersion(connection, versionId, budgetId, "2026-01-01", "500.00");
    }

    flyway("16").migrate();

    try (Connection connection = connection();
        PreparedStatement statement =
            connection.prepareStatement("SELECT monthly_cap FROM budget_versions WHERE id = ?")) {
      assertThat(isNullable(connection, "budget_versions", "monthly_cap")).isTrue();
      statement.setObject(1, versionId);
      try (ResultSet rows = statement.executeQuery()) {
        assertThat(rows.next()).isTrue();
        assertThat(rows.getBigDecimal(1)).isEqualByComparingTo("500.00");
      }
    }
  }

  @Test
  void theCheckAcceptsANullOrPositiveCapAndRejectsZeroOrNegative() throws Exception {
    flyway("16").migrate();
    try (Connection connection = connection()) {
      UUID budgetId = insertBudget(connection);

      insertVersion(connection, UUID.randomUUID(), budgetId, "2026-01-01", null);
      insertVersion(connection, UUID.randomUUID(), budgetId, "2026-02-01", "0.01");

      assertThatThrownBy(
              () -> insertVersion(connection, UUID.randomUUID(), budgetId, "2026-03-01", "0.00"))
          .isInstanceOf(SQLException.class);
      assertThatThrownBy(
              () -> insertVersion(connection, UUID.randomUUID(), budgetId, "2026-04-01", "-5.00"))
          .isInstanceOf(SQLException.class);
    }
  }

  private static UUID insertBudget(Connection connection) throws SQLException {
    UUID id = UUID.randomUUID();
    UUID categoryId = UUID.randomUUID();
    try (PreparedStatement statement =
        connection.prepareStatement(
            "INSERT INTO categories (id, name, type, created_at, last_modified_at)"
                + " VALUES (?, ?, 'EXPENSE', now(), now())")) {
      statement.setObject(1, categoryId);
      statement.setString(2, "Tombstone Test " + categoryId);
      statement.executeUpdate();
    }
    try (PreparedStatement statement =
        connection.prepareStatement(
            "INSERT INTO budgets (id, category_id, created_at, last_modified_at)"
                + " VALUES (?, ?, now(), now())")) {
      statement.setObject(1, id);
      statement.setObject(2, categoryId);
      statement.executeUpdate();
    }
    return id;
  }

  private static void insertVersion(
      Connection connection, UUID id, UUID budgetId, String effectiveFrom, String cap)
      throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement(
            "INSERT INTO budget_versions (id, budget_id, monthly_cap, effective_from,"
                + " created_at, last_modified_at)"
                + " VALUES (?, ?, ?, CAST(? AS date), now(), now())")) {
      statement.setObject(1, id);
      statement.setObject(2, budgetId);
      statement.setBigDecimal(3, cap == null ? null : new BigDecimal(cap));
      statement.setString(4, effectiveFrom);
      statement.executeUpdate();
    }
  }
}
