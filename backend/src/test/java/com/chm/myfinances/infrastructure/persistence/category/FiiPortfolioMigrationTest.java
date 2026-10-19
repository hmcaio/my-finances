package com.chm.myfinances.infrastructure.persistence.category;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chm.myfinances.TestcontainersConfiguration;
import com.chm.myfinances.testsupport.migration.AbstractMigrationTest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Proves the constraints {@code V20__fii_segments_allocation_plan_dividends.sql} adds (F026 spec):
 * the partial unique index on {@code dividend_category} and the allocation-plan tables. The tests
 * of V20's one-off "Dividends" seed against pre-existing rows were retired (issue #89, B6). Runs
 * Flyway by hand in its own schema of the shared Testcontainers Postgres (see {@link
 * AbstractMigrationTest}).
 */
@Tag("integration")
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class FiiPortfolioMigrationTest extends AbstractMigrationTest {

  private static final String SCHEMA = "migration_test_fii_portfolio";

  FiiPortfolioMigrationTest() {
    super(SCHEMA);
  }

  @Test
  void theSchemaRefusesASecondDividendCategory() throws Exception {
    flyway("20").migrate();

    try (Connection connection = connection()) {
      UUID anotherId = insertCategory(connection, "Another Dividend Category", "INCOME");

      assertThatThrownBy(
              () ->
                  execute(
                      connection,
                      "UPDATE categories SET dividend_category = true WHERE id = '"
                          + anotherId
                          + "'"))
          .isInstanceOf(SQLException.class)
          .hasMessageContaining("uq_categories_single_dividend_category");
    }
  }

  @Test
  void allocationPlanTablesAcceptRowsAndEnforceTheirConstraints() throws Exception {
    flyway("19").migrate();
    UUID investmentCategoryId;
    UUID productId;
    try (Connection connection = connection()) {
      investmentCategoryId =
          insertInvestmentCategory(connection, "Variable Income Plan Migration Test");
      productId =
          insertInvestmentProduct(connection, investmentCategoryId, "HGLG11 Plan Migration Test");
    }

    flyway("20").migrate();

    try (Connection connection = connection()) {
      UUID planId = UUID.randomUUID();
      try (PreparedStatement statement =
          connection.prepareStatement(
              "INSERT INTO allocation_plans (id, created_at, last_modified_at) VALUES (?, now(), now())")) {
        statement.setObject(1, planId);
        statement.executeUpdate();
      }
      UUID versionId = UUID.randomUUID();
      try (PreparedStatement statement =
          connection.prepareStatement(
              "INSERT INTO allocation_plan_versions (id, plan_id, effective_from, created_at,"
                  + " last_modified_at) VALUES (?, ?, CURRENT_DATE, now(), now())")) {
        statement.setObject(1, versionId);
        statement.setObject(2, planId);
        statement.executeUpdate();
      }
      try (PreparedStatement statement =
          connection.prepareStatement(
              "INSERT INTO allocation_plan_entries (id, version_id, investment_product_id,"
                  + " target_percentage, created_at, last_modified_at)"
                  + " VALUES (?, ?, ?, 100.00, now(), now())")) {
        statement.setObject(1, UUID.randomUUID());
        statement.setObject(2, versionId);
        statement.setObject(3, productId);
        statement.executeUpdate();
      }

      assertThat(
              count(
                  connection,
                  "SELECT count(*) FROM allocation_plan_entries WHERE version_id = '"
                      + versionId
                      + "'"))
          .isEqualTo(1);

      assertThatThrownBy(
              () -> {
                try (PreparedStatement statement =
                    connection.prepareStatement(
                        "INSERT INTO allocation_plan_entries (id, version_id, investment_product_id,"
                            + " target_percentage, created_at, last_modified_at)"
                            + " VALUES (?, ?, ?, 0, now(), now())")) {
                  statement.setObject(1, UUID.randomUUID());
                  statement.setObject(2, versionId);
                  statement.setObject(3, UUID.randomUUID());
                  statement.executeUpdate();
                }
              })
          .isInstanceOf(SQLException.class)
          .hasMessageContaining("chk_allocation_plan_entries_target_percentage_positive");
    }
  }

  private static void execute(Connection connection, String sql) throws SQLException {
    try (var statement = connection.createStatement()) {
      statement.executeUpdate(sql);
    }
  }

  private static UUID insertCategory(Connection connection, String name, String type)
      throws SQLException {
    UUID id = UUID.randomUUID();
    try (PreparedStatement statement =
        connection.prepareStatement(
            "INSERT INTO categories (id, name, type, created_at, last_modified_at)"
                + " VALUES (?, ?, ?, now(), now())")) {
      statement.setObject(1, id);
      statement.setString(2, name);
      statement.setString(3, type);
      statement.executeUpdate();
    }
    return id;
  }

  private static UUID insertInvestmentCategory(Connection connection, String name)
      throws SQLException {
    UUID id = UUID.randomUUID();
    try (PreparedStatement statement =
        connection.prepareStatement(
            "INSERT INTO investment_categories (id, name, created_at, last_modified_at)"
                + " VALUES (?, ?, now(), now())")) {
      statement.setObject(1, id);
      statement.setString(2, name);
      statement.executeUpdate();
    }
    return id;
  }

  private static UUID insertInvestmentProduct(Connection connection, UUID categoryId, String name)
      throws SQLException {
    UUID id = UUID.randomUUID();
    try (PreparedStatement statement =
        connection.prepareStatement(
            "INSERT INTO investment_products (id, investment_category_id, name, created_at,"
                + " last_modified_at) VALUES (?, ?, ?, now(), now())")) {
      statement.setObject(1, id);
      statement.setObject(2, categoryId);
      statement.setString(3, name);
      statement.executeUpdate();
    }
    return id;
  }
}
