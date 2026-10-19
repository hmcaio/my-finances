package com.chm.myfinances.infrastructure.persistence.category;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chm.myfinances.TestcontainersConfiguration;
import com.chm.myfinances.testsupport.migration.AbstractMigrationTest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Proves the {@code uq_categories_single_built_in_per_type} partial unique index {@code
 * V14__builtin_categories.sql} adds. The tests of V14's one-off adoption of existing seed rows were
 * retired (issue #89, B6); {@code V2CategorySeedMigrationTest} still covers the protected set a
 * fresh database ends up with.
 *
 * <p>Runs Flyway by hand in its own schema of the shared Testcontainers Postgres (see {@link
 * AbstractMigrationTest}).
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
  void theSchemaRefusesASecondBuiltInCategoryOfTheSameType() throws Exception {
    flyway("14").migrate();

    try (Connection connection = connection()) {
      // F035/ADR 0028: V2 no longer seeds a regular EXPENSE category by default, so insert one of
      // our own to attempt flagging as a second built-in row of the same type.
      insertCategory(connection, "Another Expense Category", "EXPENSE");

      try (Statement statement = connection.createStatement()) {
        assertThatThrownBy(
                () ->
                    statement.executeUpdate(
                        "UPDATE categories SET built_in = true"
                            + " WHERE name = 'Another Expense Category'"))
            .isInstanceOf(SQLException.class)
            .hasMessageContaining("uq_categories_single_built_in_per_type");
      }
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
}
