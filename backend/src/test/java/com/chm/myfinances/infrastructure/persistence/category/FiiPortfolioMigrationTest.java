package com.chm.myfinances.infrastructure.persistence.category;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chm.myfinances.TestcontainersConfiguration;
import com.chm.myfinances.testsupport.migration.AbstractMigrationTest;
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
 * Proves {@code V20__fii_segments_allocation_plan_dividends.sql} against pre-existing data (backend
 * {@code CLAUDE.md}'s migration-testing pattern, F026 spec): the fresh-insert "Dividends" seed
 * (never adopting an existing row, unlike V18's fuel-category seed), existing categories/
 * investment products/transactions left untouched, the new nullable columns, and the partial unique
 * index on {@code dividend_category}. Same {@code Flyway-by-hand-into-a-throwaway-schema} setup as
 * {@code FuelTrackingMigrationTest}.
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
  void withNoExistingDividendsNamedCategoryTheFreshOneIsNamedPlainly() throws Exception {
    flyway("19").migrate();

    flyway("20").migrate();

    try (Connection connection = connection()) {
      assertThat(count(connection, "SELECT count(*) FROM categories WHERE dividend_category"))
          .isEqualTo(1);
      assertThat(
              count(
                  connection,
                  "SELECT count(*) FROM categories WHERE dividend_category AND name = 'Dividends'"
                      + " AND type = 'INCOME'"))
          .isEqualTo(1);
    }
  }

  @Test
  void
      withAnExistingDividendsNamedCategoryTheFreshOneFallsBackToADisambiguatedNameRatherThanAdoptingIt()
          throws Exception {
    flyway("19").migrate();
    UUID existingId;
    try (Connection connection = connection()) {
      // categories.name is globally UNIQUE (V10): a pre-existing "Dividends" row (of any type)
      // must NOT be adopted (spec: insert fresh, not adopt) and the fresh seed can't reuse the
      // exact same name either, so it falls back to a disambiguated one instead.
      existingId = insertCategory(connection, "Dividends", "EXPENSE");
    }

    flyway("20").migrate();

    try (Connection connection = connection()) {
      assertThat(count(connection, "SELECT count(*) FROM categories WHERE dividend_category"))
          .isEqualTo(1);
      assertThat(isDividendCategory(connection, existingId)).isFalse();
      assertThat(
              count(
                  connection,
                  "SELECT count(*) FROM categories WHERE dividend_category"
                      + " AND name = 'Dividends (FII)' AND type = 'INCOME'"))
          .isEqualTo(1);
    }
  }

  @Test
  void existingCategoriesProductsAndTransactionsAreUnaffected() throws Exception {
    flyway("19").migrate();
    UUID categoryId;
    UUID investmentCategoryId;
    UUID productId;
    UUID accountId;
    UUID paymentMethodId;
    UUID transactionId;
    try (Connection connection = connection()) {
      categoryId = insertCategory(connection, "Groceries FII Migration Test", "EXPENSE");
      investmentCategoryId =
          insertInvestmentCategory(connection, "Variable Income FII Migration Test");
      productId =
          insertInvestmentProduct(connection, investmentCategoryId, "KNRI11 FII Migration Test");
      UUID institutionId = insertInstitution(connection, "Test Institution FII Migration");
      accountId = insertAccount(connection, "Test Account FII Migration", institutionId);
      paymentMethodId = insertPaymentMethod(connection, "Test Payment Method FII Migration");
      transactionId = insertTransaction(connection, categoryId, accountId, paymentMethodId);
    }

    flyway("20").migrate();

    try (Connection connection = connection()) {
      assertThat(
              count(connection, "SELECT count(*) FROM categories WHERE id = '" + categoryId + "'"))
          .isEqualTo(1);
      assertThat(
              count(
                  connection,
                  "SELECT count(*) FROM investment_products WHERE id = '" + productId + "'"))
          .isEqualTo(1);
      assertThat(
              count(
                  connection,
                  "SELECT count(*) FROM transactions WHERE id = '" + transactionId + "'"))
          .isEqualTo(1);
      assertThat(isNullable(connection, "transactions", "investment_holding_id")).isTrue();
      assertThat(isNullable(connection, "investment_products", "ticker")).isTrue();
      assertThat(isNullable(connection, "investment_products", "segment_id")).isTrue();
      try (PreparedStatement statement =
          connection.prepareStatement(
              "SELECT ticker, segment_id FROM investment_products WHERE id = ?")) {
        statement.setObject(1, productId);
        try (ResultSet rows = statement.executeQuery()) {
          assertThat(rows.next()).isTrue();
          assertThat(rows.getObject(1)).isNull();
          assertThat(rows.getObject(2)).isNull();
        }
      }
    }
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

  private static UUID insertInstitution(Connection connection, String name) throws SQLException {
    UUID id = UUID.randomUUID();
    try (PreparedStatement statement =
        connection.prepareStatement(
            "INSERT INTO institutions (id, name, built_in, created_at, last_modified_at)"
                + " VALUES (?, ?, false, now(), now())")) {
      statement.setObject(1, id);
      statement.setString(2, name);
      statement.executeUpdate();
    }
    return id;
  }

  private static UUID insertAccount(Connection connection, String name, UUID institutionId)
      throws SQLException {
    UUID id = UUID.randomUUID();
    try (PreparedStatement statement =
        connection.prepareStatement(
            "INSERT INTO accounts (id, name, type, institution_id, opening_balance,"
                + " opening_balance_date, created_at, last_modified_at)"
                + " VALUES (?, ?, 'CHECKING', ?, 0, CURRENT_DATE, now(), now())")) {
      statement.setObject(1, id);
      statement.setString(2, name);
      statement.setObject(3, institutionId);
      statement.executeUpdate();
    }
    return id;
  }

  private static UUID insertPaymentMethod(Connection connection, String name) throws SQLException {
    UUID id = UUID.randomUUID();
    try (PreparedStatement statement =
        connection.prepareStatement(
            "INSERT INTO payment_methods (id, name, created_at, last_modified_at)"
                + " VALUES (?, ?, now(), now())")) {
      statement.setObject(1, id);
      statement.setString(2, name);
      statement.executeUpdate();
    }
    return id;
  }

  private static UUID insertTransaction(
      Connection connection, UUID categoryId, UUID accountId, UUID paymentMethodId)
      throws SQLException {
    UUID id = UUID.randomUUID();
    try (PreparedStatement statement =
        connection.prepareStatement(
            "INSERT INTO transactions (id, date, amount, category_id, type, account_id,"
                + " payment_method_id, description, created_at, last_modified_at)"
                + " VALUES (?, CURRENT_DATE, 10.00, ?, 'EXPENSE', ?, ?, 'Migration test', now(),"
                + " now())")) {
      statement.setObject(1, id);
      statement.setObject(2, categoryId);
      statement.setObject(3, accountId);
      statement.setObject(4, paymentMethodId);
      statement.executeUpdate();
    }
    return id;
  }

  private static boolean isDividendCategory(Connection connection, UUID id) throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement("SELECT dividend_category FROM categories WHERE id = ?")) {
      statement.setObject(1, id);
      try (ResultSet rows = statement.executeQuery()) {
        assertThat(rows.next()).isTrue();
        return rows.getBoolean(1);
      }
    }
  }
}
