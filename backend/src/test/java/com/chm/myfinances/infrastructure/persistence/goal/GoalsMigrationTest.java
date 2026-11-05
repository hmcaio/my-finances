package com.chm.myfinances.infrastructure.persistence.goal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chm.myfinances.TestcontainersConfiguration;
import com.chm.myfinances.testsupport.migration.AbstractMigrationTest;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Proves the constraints {@code V28__goals.sql} (F039, ADR 0034) adds: each check rejects its bad
 * row (zero target, negative contribution, an out-of-range or unordered return, an invalid status,
 * an archived goal shown on the dashboard, an out-of-range percentage), a duplicate (goal, product)
 * is rejected, and deleting a goal cascades to its allocations. Runs Flyway by hand in its own
 * schema of the shared Testcontainers Postgres (see {@link AbstractMigrationTest}).
 */
@Tag("integration")
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class GoalsMigrationTest extends AbstractMigrationTest {

  private static final String SCHEMA = "migration_test_goals";

  GoalsMigrationTest() {
    super(SCHEMA);
  }

  /** A goal row with every value valid; each test overrides the one it wants to break. */
  private record Row(
      String target,
      String contribution,
      String pessimistic,
      String base,
      String optimistic,
      String status,
      boolean show) {

    static Row valid() {
      return new Row("80000.00", "2000.00", "2", "4", "6", "ACTIVE", true);
    }

    Row target(String value) {
      return new Row(value, contribution, pessimistic, base, optimistic, status, show);
    }

    Row contribution(String value) {
      return new Row(target, value, pessimistic, base, optimistic, status, show);
    }

    Row returns(String p, String b, String o) {
      return new Row(target, contribution, p, b, o, status, show);
    }

    Row status(String value, boolean showOnDashboard) {
      return new Row(target, contribution, pessimistic, base, optimistic, value, showOnDashboard);
    }
  }

  @Test
  void aValidGoalAndAllocationAreAccepted() throws Exception {
    flyway("28").migrate();
    try (Connection connection = connection()) {
      UUID goalId = insertGoal(connection, Row.valid());
      insertAllocation(connection, goalId, insertProduct(connection), "100");
    }
  }

  @Test
  void aZeroOrNegativeTargetIsRejected() throws Exception {
    flyway("28").migrate();
    try (Connection connection = connection()) {
      assertRejected(
          () -> insertGoal(connection, Row.valid().target("0")), "chk_goals_target_positive");
      assertRejected(
          () -> insertGoal(connection, Row.valid().target("-1")), "chk_goals_target_positive");
    }
  }

  @Test
  void aNegativeContributionIsRejectedButZeroIsAccepted() throws Exception {
    flyway("28").migrate();
    try (Connection connection = connection()) {
      assertRejected(
          () -> insertGoal(connection, Row.valid().contribution("-0.01")),
          "chk_goals_contribution_not_negative");
      insertGoal(connection, Row.valid().contribution("0"));
    }
  }

  @Test
  void aReturnOutsideMinusFiftyToOneHundredIsRejected() throws Exception {
    flyway("28").migrate();
    try (Connection connection = connection()) {
      assertRejected(
          () -> insertGoal(connection, Row.valid().returns("-50.01", "4", "6")),
          "chk_goals_return_pessimistic_range");
      assertRejected(
          () -> insertGoal(connection, Row.valid().returns("2", "4", "100.01")),
          "chk_goals_return_optimistic_range");
      assertRejected(
          () -> insertGoal(connection, Row.valid().returns("-50", "100.01", "100")),
          "chk_goals_return_base_range");
      insertGoal(connection, Row.valid().returns("-50", "0", "100"));
    }
  }

  @Test
  void unorderedReturnsAreRejected() throws Exception {
    flyway("28").migrate();
    try (Connection connection = connection()) {
      assertRejected(
          () -> insertGoal(connection, Row.valid().returns("5", "4", "6")),
          "chk_goals_returns_ordered");
      assertRejected(
          () -> insertGoal(connection, Row.valid().returns("2", "7", "6")),
          "chk_goals_returns_ordered");
      insertGoal(connection, Row.valid().returns("4", "4", "4"));
    }
  }

  @Test
  void anUnknownStatusIsRejected() throws Exception {
    flyway("28").migrate();
    try (Connection connection = connection()) {
      assertRejected(
          () -> insertGoal(connection, Row.valid().status("DONE", false)), "chk_goals_status");
    }
  }

  @Test
  void anArchivedGoalCannotBeShownOnTheDashboard() throws Exception {
    flyway("28").migrate();
    try (Connection connection = connection()) {
      assertRejected(
          () -> insertGoal(connection, Row.valid().status("ARCHIVED", true)),
          "chk_goals_archived_not_on_dashboard");
      insertGoal(connection, Row.valid().status("ARCHIVED", false));
    }
  }

  @Test
  void aPercentageOutsideZeroExclusiveToOneHundredIsRejected() throws Exception {
    flyway("28").migrate();
    try (Connection connection = connection()) {
      UUID goalId = insertGoal(connection, Row.valid());
      UUID productId = insertProduct(connection);

      assertRejected(
          () -> insertAllocation(connection, goalId, productId, "0"),
          "chk_goal_allocations_percentage_range");
      assertRejected(
          () -> insertAllocation(connection, goalId, productId, "-1"),
          "chk_goal_allocations_percentage_range");
      assertRejected(
          () -> insertAllocation(connection, goalId, productId, "100.01"),
          "chk_goal_allocations_percentage_range");
    }
  }

  @Test
  void aSecondAllocationForTheSameGoalAndProductIsRejected() throws Exception {
    flyway("28").migrate();
    try (Connection connection = connection()) {
      UUID goalId = insertGoal(connection, Row.valid());
      UUID productId = insertProduct(connection);
      insertAllocation(connection, goalId, productId, "30");

      assertRejected(
          () -> insertAllocation(connection, goalId, productId, "20"),
          "uq_goal_allocations_goal_product");
      insertAllocation(connection, insertGoal(connection, Row.valid()), productId, "20");
    }
  }

  @Test
  void deletingAGoalCascadesToItsAllocations() throws Exception {
    flyway("28").migrate();
    try (Connection connection = connection()) {
      UUID goalId = insertGoal(connection, Row.valid());
      insertAllocation(connection, goalId, insertProduct(connection), "50");

      try (PreparedStatement statement =
          connection.prepareStatement("DELETE FROM goals WHERE id = ?")) {
        statement.setObject(1, goalId);
        statement.executeUpdate();
      }

      assertThat(count(connection, "SELECT count(*) FROM goal_allocations")).isZero();
    }
  }

  @Test
  void aProductWithAnAllocationCannotBeDeletedByTheDatabase() throws Exception {
    flyway("28").migrate();
    try (Connection connection = connection()) {
      UUID productId = insertProduct(connection);
      insertAllocation(connection, insertGoal(connection, Row.valid()), productId, "50");

      assertThatThrownBy(
              () -> {
                try (PreparedStatement statement =
                    connection.prepareStatement("DELETE FROM investment_products WHERE id = ?")) {
                  statement.setObject(1, productId);
                  statement.executeUpdate();
                }
              })
          .isInstanceOf(SQLException.class)
          .hasMessageContaining("goal_allocations");
    }
  }

  private static void assertRejected(SqlAction action, String constraintName) {
    assertThatThrownBy(action::run)
        .isInstanceOf(SQLException.class)
        .hasMessageContaining(constraintName);
  }

  @FunctionalInterface
  private interface SqlAction {
    void run() throws SQLException;
  }

  private static UUID insertGoal(Connection connection, Row row) throws SQLException {
    UUID id = UUID.randomUUID();
    try (PreparedStatement statement =
        connection.prepareStatement(
            "INSERT INTO goals (id, name, notes, target_amount, target_date,"
                + " planned_monthly_contribution, return_pessimistic, return_base,"
                + " return_optimistic, status, show_on_dashboard, created_at, last_modified_at)"
                + " VALUES (?, 'Goal Migration Test', NULL, ?, NULL, ?, ?, ?, ?, ?, ?, now(),"
                + " now())")) {
      statement.setObject(1, id);
      statement.setBigDecimal(2, new BigDecimal(row.target()));
      statement.setBigDecimal(3, new BigDecimal(row.contribution()));
      statement.setBigDecimal(4, new BigDecimal(row.pessimistic()));
      statement.setBigDecimal(5, new BigDecimal(row.base()));
      statement.setBigDecimal(6, new BigDecimal(row.optimistic()));
      statement.setString(7, row.status());
      statement.setBoolean(8, row.show());
      statement.executeUpdate();
    }
    return id;
  }

  private static UUID insertProduct(Connection connection) throws SQLException {
    UUID productId = UUID.randomUUID();
    try (PreparedStatement statement =
        connection.prepareStatement(
            "INSERT INTO investment_products (id, investment_category_id, name, created_at,"
                + " last_modified_at) VALUES (?, (SELECT id FROM investment_categories WHERE name ="
                + " 'Crypto'), ?, now(), now())")) {
      statement.setObject(1, productId);
      statement.setString(2, "Product Goal Migration Test " + productId);
      statement.executeUpdate();
    }
    return productId;
  }

  private static void insertAllocation(
      Connection connection, UUID goalId, UUID productId, String percentage) throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement(
            "INSERT INTO goal_allocations (id, goal_id, product_id, percentage, created_at,"
                + " last_modified_at) VALUES (?, ?, ?, ?, now(), now())")) {
      statement.setObject(1, UUID.randomUUID());
      statement.setObject(2, goalId);
      statement.setObject(3, productId);
      statement.setBigDecimal(4, new BigDecimal(percentage));
      statement.executeUpdate();
    }
  }
}
