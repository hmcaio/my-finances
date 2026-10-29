package com.chm.myfinances.infrastructure.persistence.investmentopeningposition;

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
 * Proves the constraints {@code V27__investment_opening_positions.sql} (F038, ADR 0033) adds: at
 * most one position per holding, and a strictly positive quantity and unit price. Runs Flyway by
 * hand in its own schema of the shared Testcontainers Postgres (see {@link AbstractMigrationTest}).
 */
@Tag("integration")
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class InvestmentOpeningPositionsMigrationTest extends AbstractMigrationTest {

  private static final String SCHEMA = "migration_test_opening_positions";

  InvestmentOpeningPositionsMigrationTest() {
    super(SCHEMA);
  }

  @Test
  void aSecondPositionForTheSameHoldingIsRejected() throws Exception {
    flyway("27").migrate();
    try (Connection connection = connection()) {
      UUID holdingId = insertHolding(connection);
      insertPosition(connection, holdingId, "10", "5.00");

      assertRejected(
          () -> insertPosition(connection, holdingId, "20", "6.00"),
          "uq_investment_opening_positions_holding");
    }
  }

  @Test
  void aZeroOrNegativeQuantityIsRejected() throws Exception {
    flyway("27").migrate();
    try (Connection connection = connection()) {
      UUID holdingId = insertHolding(connection);

      assertRejected(
          () -> insertPosition(connection, holdingId, "0", "5.00"),
          "chk_investment_opening_positions_quantity_positive");
      assertRejected(
          () -> insertPosition(connection, holdingId, "-1", "5.00"),
          "chk_investment_opening_positions_quantity_positive");
    }
  }

  @Test
  void aZeroOrNegativeUnitPriceIsRejected() throws Exception {
    flyway("27").migrate();
    try (Connection connection = connection()) {
      UUID holdingId = insertHolding(connection);

      assertRejected(
          () -> insertPosition(connection, holdingId, "10", "0"),
          "chk_investment_opening_positions_unit_price_positive");
      assertRejected(
          () -> insertPosition(connection, holdingId, "10", "-0.01"),
          "chk_investment_opening_positions_unit_price_positive");
    }
  }

  @Test
  void eightDecimalQuantityAndPriceAreAccepted() throws Exception {
    flyway("27").migrate();
    try (Connection connection = connection()) {
      insertPosition(connection, insertHolding(connection), "0.12345678", "250000.87654321");
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

  private static UUID insertHolding(Connection connection) throws SQLException {
    UUID accountId = UUID.randomUUID();
    try (PreparedStatement statement =
        connection.prepareStatement(
            "INSERT INTO accounts (id, name, institution_id, type, opening_balance,"
                + " opening_balance_date, created_at, last_modified_at)"
                + " VALUES (?, ?, (SELECT id FROM institutions WHERE built_in), 'INVESTMENT', NULL,"
                + " NULL, now(), now())")) {
      statement.setObject(1, accountId);
      statement.setString(2, "Broker Opening Position Migration Test " + accountId);
      statement.executeUpdate();
    }
    UUID productId = UUID.randomUUID();
    try (PreparedStatement statement =
        connection.prepareStatement(
            "INSERT INTO investment_products (id, investment_category_id, name, created_at,"
                + " last_modified_at) VALUES (?, (SELECT id FROM investment_categories WHERE name ="
                + " 'Crypto'), ?, now(), now())")) {
      statement.setObject(1, productId);
      statement.setString(2, "Product Opening Position Migration Test " + productId);
      statement.executeUpdate();
    }
    UUID holdingId = UUID.randomUUID();
    try (PreparedStatement statement =
        connection.prepareStatement(
            "INSERT INTO investment_holdings (id, product_id, account_id, created_at,"
                + " last_modified_at) VALUES (?, ?, ?, now(), now())")) {
      statement.setObject(1, holdingId);
      statement.setObject(2, productId);
      statement.setObject(3, accountId);
      statement.executeUpdate();
    }
    return holdingId;
  }

  private static void insertPosition(
      Connection connection, UUID holdingId, String quantity, String unitPrice)
      throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement(
            "INSERT INTO investment_opening_positions (id, holding_id, as_of_date, quantity,"
                + " unit_price, created_at, last_modified_at)"
                + " VALUES (?, ?, DATE '2026-10-01', ?, ?, now(), now())")) {
      statement.setObject(1, UUID.randomUUID());
      statement.setObject(2, holdingId);
      statement.setBigDecimal(3, new BigDecimal(quantity));
      statement.setBigDecimal(4, new BigDecimal(unitPrice));
      statement.executeUpdate();
    }
  }
}
