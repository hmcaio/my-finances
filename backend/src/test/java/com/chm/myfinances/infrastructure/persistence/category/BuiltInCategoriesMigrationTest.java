package com.chm.myfinances.infrastructure.persistence.category;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chm.myfinances.TestcontainersConfiguration;
import com.chm.myfinances.testsupport.migration.AbstractMigrationTest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Proves {@code V14__builtin_categories.sql} adopts the seeded fallback categories that already
 * exist in a database, whatever state the user left them in - the case a fresh-database migration
 * run can't show. {@code V2} seeded {@code Other} (EXPENSE) and {@code Other Income} (INCOME) as
 * ordinary, deletable, renamable rows.
 *
 * <p>Runs Flyway by hand in its own schema of the shared Testcontainers Postgres: migrate to V13,
 * shape the seeded rows, migrate to V14 (same setup as {@code InstitutionBackfillMigrationTest}).
 */
@Tag("integration")
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class BuiltInCategoriesMigrationTest extends AbstractMigrationTest {

  private static final String SCHEMA = "migration_test_builtin_categories";

  BuiltInCategoriesMigrationTest() {
    super(SCHEMA);
  }

  @Test
  void untouchedSeedRowsAreFlaggedAndTheExpenseOneIsRenamedInPlace() throws Exception {
    flyway("13").migrate();
    UUID otherId;
    UUID otherIncomeId;
    try (Connection connection = connection()) {
      otherId = idOf(connection, "Other");
      otherIncomeId = idOf(connection, "Other Income");
    }

    flyway("14").migrate();

    try (Connection connection = connection()) {
      assertThat(builtIn(connection))
          .containsOnly(Map.entry("Other Expense", "EXPENSE"), Map.entry("Other Income", "INCOME"));
      // Adopted, not replaced: existing transactions/budgets keep pointing at the same row.
      assertThat(idOf(connection, "Other Expense")).isEqualTo(otherId);
      assertThat(idOf(connection, "Other Income")).isEqualTo(otherIncomeId);
      assertThat(count(connection, "SELECT count(*) FROM categories WHERE name = 'Other'"))
          .isZero();
      assertThat(count(connection, "SELECT count(*) FROM categories WHERE NOT built_in"))
          .isEqualTo(count(connection, "SELECT count(*) FROM categories") - 2);
    }
  }

  @Test
  void theSchemaRefusesASecondBuiltInCategoryOfTheSameType() throws Exception {
    flyway("14").migrate();

    try (Connection connection = connection();
        Statement statement = connection.createStatement()) {
      assertThatThrownBy(
              () ->
                  statement.executeUpdate(
                      "UPDATE categories SET built_in = true WHERE name = 'Groceries'"))
          .isInstanceOf(SQLException.class)
          .hasMessageContaining("uq_categories_single_built_in_per_type");
    }
  }

  @Test
  void renamedOrDeletedSeedRowsGetFreshBuiltInRowsAndTheUserRowsStayOrdinary() throws Exception {
    flyway("13").migrate();
    try (Connection connection = connection()) {
      // The user renamed the expense fallback and deleted the income one.
      execute(connection, "UPDATE categories SET name = 'Misc' WHERE name = 'Other'");
      execute(connection, "DELETE FROM categories WHERE name = 'Other Income'");
    }

    flyway("14").migrate();

    try (Connection connection = connection()) {
      assertThat(builtIn(connection))
          .containsOnly(Map.entry("Other Expense", "EXPENSE"), Map.entry("Other Income", "INCOME"));
      assertThat(
              count(
                  connection,
                  "SELECT count(*) FROM categories WHERE name = 'Misc' AND NOT built_in"))
          .isEqualTo(1);
    }
  }

  @Test
  void aUserCreatedOtherExpenseIsAdoptedInsteadOfTheSeededOther() throws Exception {
    flyway("13").migrate();
    try (Connection connection = connection()) {
      execute(connection, "DELETE FROM categories WHERE name = 'Other'");
      insertCategory(connection, "Other Expense", "EXPENSE");
    }

    flyway("14").migrate();

    try (Connection connection = connection()) {
      assertThat(builtIn(connection)).containsEntry("Other Expense", "EXPENSE");
      assertThat(count(connection, "SELECT count(*) FROM categories WHERE name = 'Other Expense'"))
          .isEqualTo(1);
    }
  }

  @Test
  void aNameHeldByTheOtherTypeKeepsTheSeededNameInsteadOfFailingTheMigration() throws Exception {
    flyway("13").migrate();
    try (Connection connection = connection()) {
      // names are globally unique (V10), so an INCOME row can occupy "Other Expense"
      insertCategory(connection, "Other Expense", "INCOME");
    }

    flyway("14").migrate();

    try (Connection connection = connection()) {
      assertThat(builtIn(connection))
          .containsOnly(Map.entry("Other", "EXPENSE"), Map.entry("Other Income", "INCOME"));
    }
  }

  @Test
  void aNameHeldByTheOtherTypeAndNoSeedRowGetsADisambiguatedBuiltInRow() throws Exception {
    flyway("13").migrate();
    try (Connection connection = connection()) {
      execute(connection, "DELETE FROM categories WHERE name = 'Other'");
      insertCategory(connection, "Other Expense", "INCOME");
    }

    flyway("14").migrate();

    try (Connection connection = connection()) {
      assertThat(builtIn(connection))
          .containsOnly(
              Map.entry("Other Expense (built-in)", "EXPENSE"),
              Map.entry("Other Income", "INCOME"));
    }
  }

  private static void execute(Connection connection, String sql) throws SQLException {
    try (Statement statement = connection.createStatement()) {
      statement.executeUpdate(sql);
    }
  }

  private static void insertCategory(Connection connection, String name, String type)
      throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement(
            "INSERT INTO categories (id, name, type, created_at, last_modified_at)"
                + " VALUES (?, ?, ?, now(), now())")) {
      statement.setObject(1, UUID.randomUUID());
      statement.setString(2, name);
      statement.setString(3, type);
      statement.executeUpdate();
    }
  }

  private static UUID idOf(Connection connection, String name) throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement("SELECT id FROM categories WHERE name = ?")) {
      statement.setString(1, name);
      try (ResultSet rows = statement.executeQuery()) {
        assertThat(rows.next()).as("category %s exists", name).isTrue();
        return rows.getObject(1, UUID.class);
      }
    }
  }

  /** Built-in rows as name -> type. */
  private static Map<String, String> builtIn(Connection connection) throws SQLException {
    Map<String, String> result = new HashMap<>();
    try (Statement statement = connection.createStatement();
        ResultSet rows =
            statement.executeQuery("SELECT name, type FROM categories WHERE built_in")) {
      while (rows.next()) {
        result.put(rows.getString(1), rows.getString(2));
      }
    }
    return result;
  }
}
