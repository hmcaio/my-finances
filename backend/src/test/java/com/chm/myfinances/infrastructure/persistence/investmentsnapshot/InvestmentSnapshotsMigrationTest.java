package com.chm.myfinances.infrastructure.persistence.investmentsnapshot;

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
 * Proves {@code V15__investment_snapshots_and_trades.sql} (F009) against pre-existing data and at
 * the constraint level: transfers that predate it are untouched (every new column {@code NULL},
 * every new {@code CHECK} satisfied), and the {@code investment_snapshots} / {@code transfers}
 * constraints accept and reject as specified.
 *
 * <p>Runs Flyway by hand in its own schema of the shared Testcontainers Postgres (see {@link
 * AbstractMigrationTest}).
 */
@Tag("integration")
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class InvestmentSnapshotsMigrationTest extends AbstractMigrationTest {

  private static final String SCHEMA = "migration_test_snapshots";

  InvestmentSnapshotsMigrationTest() {
    super(SCHEMA);
  }

  @Test
  void existingTransfersAreUnaffectedAndTheNewColumnsAreNull() throws Exception {
    flyway("14").migrate();
    UUID transferId = UUID.randomUUID();
    try (Connection connection = connection()) {
      UUID checking = insertAccount(connection, "Checking", "CHECKING", "100.00");
      UUID savings = insertAccount(connection, "Savings", "SAVINGS", "0.00");
      insertTransfer(connection, transferId, checking, savings, "12.34");
    }

    flyway("15").migrate();

    try (Connection connection = connection();
        PreparedStatement statement =
            connection.prepareStatement(
                "SELECT amount, investment_product_id, quantity, unit_price, taxes"
                    + " FROM transfers WHERE id = ?")) {
      statement.setObject(1, transferId);
      try (ResultSet rows = statement.executeQuery()) {
        assertThat(rows.next()).isTrue();
        assertThat(rows.getBigDecimal(1)).isEqualByComparingTo("12.34");
        assertThat(rows.getObject(2)).isNull();
        assertThat(rows.getObject(3)).isNull();
        assertThat(rows.getObject(4)).isNull();
        assertThat(rows.getObject(5)).isNull();
      }
    }
  }

  @Test
  void theTransferTradeChecksAcceptAndRejectAsSpecified() throws Exception {
    flyway("15").migrate();
    try (Connection connection = connection()) {
      UUID checking = insertAccount(connection, "Checking", "CHECKING", "100.00");
      UUID broker = insertAccount(connection, "Broker", "INVESTMENT", null);
      UUID product = insertProduct(connection, broker);

      // Accepted: no trade data, everything, taxes alone, zero taxes, an 8-decimal quantity/price.
      insertTrade(connection, checking, broker, product, null, null, null);
      insertTrade(connection, checking, broker, product, "10", "100.5", "5.00");
      insertTrade(connection, checking, broker, product, null, null, "0.00");
      insertTrade(connection, checking, broker, product, "0.00000001", "0.00000001", null);
      insertTrade(connection, checking, broker, null, null, null, null);

      assertRejected(
          () -> insertTrade(connection, checking, broker, product, "0", "1", null),
          "chk_transfers_quantity_positive");
      assertRejected(
          () -> insertTrade(connection, checking, broker, product, "-1", "1", null),
          "chk_transfers_quantity_positive");
      assertRejected(
          () -> insertTrade(connection, checking, broker, product, "1", "0", null),
          "chk_transfers_unit_price_positive");
      assertRejected(
          () -> insertTrade(connection, checking, broker, product, null, null, "-0.01"),
          "chk_transfers_taxes_non_negative");
      assertRejected(
          () -> insertTrade(connection, checking, broker, product, "1", null, null),
          "chk_transfers_quantity_with_unit_price");
      assertRejected(
          () -> insertTrade(connection, checking, broker, product, null, "1", null),
          "chk_transfers_quantity_with_unit_price");
      assertRejected(
          () -> insertTrade(connection, checking, broker, null, "1", "1", null),
          "chk_transfers_trade_details_need_product");
      assertRejected(
          () -> insertTrade(connection, checking, broker, null, null, null, "1.00"),
          "chk_transfers_trade_details_need_product");
    }
  }

  @Test
  void aTransferCannotReferenceAnUnknownProduct() throws Exception {
    flyway("15").migrate();
    try (Connection connection = connection()) {
      UUID checking = insertAccount(connection, "Checking", "CHECKING", "100.00");
      UUID broker = insertAccount(connection, "Broker", "INVESTMENT", null);

      assertRejected(
          () -> insertTrade(connection, checking, broker, UUID.randomUUID(), null, null, null),
          "transfers_investment_product_id_fkey");
    }
  }

  @Test
  void snapshotsAreUniquePerProductPerDateAndNeverNegative() throws Exception {
    flyway("15").migrate();
    try (Connection connection = connection()) {
      UUID broker = insertAccount(connection, "Broker", "INVESTMENT", null);
      UUID product = insertProduct(connection, broker);
      UUID otherProduct = insertProduct(connection, broker);

      insertSnapshot(connection, product, "2026-03-31", "100.00");
      // Accepted: another day, another product on the same day, and a zero balance.
      insertSnapshot(connection, product, "2026-04-30", "0.00");
      insertSnapshot(connection, otherProduct, "2026-03-31", "5.00");

      assertRejected(
          () -> insertSnapshot(connection, product, "2026-03-31", "200.00"),
          "uq_investment_snapshots_product_date");
      assertRejected(
          () -> insertSnapshot(connection, product, "2026-05-31", "-0.01"),
          "chk_investment_snapshots_balance_non_negative");
      assertRejected(
          () -> insertSnapshot(connection, UUID.randomUUID(), "2026-05-31", "1.00"),
          "investment_snapshots_product_id_fkey");
    }
  }

  /** Runs the statement and asserts the database refused it with the named constraint. */
  private static void assertRejected(SqlAction action, String constraintName) {
    assertThatThrownBy(action::run)
        .isInstanceOf(SQLException.class)
        .hasMessageContaining(constraintName);
  }

  @FunctionalInterface
  private interface SqlAction {
    void run() throws SQLException;
  }

  private static UUID insertAccount(
      Connection connection, String name, String type, String openingBalance) throws SQLException {
    UUID id = UUID.randomUUID();
    try (PreparedStatement statement =
        connection.prepareStatement(
            "INSERT INTO accounts (id, name, institution_id, type, opening_balance,"
                + " opening_balance_date, created_at, last_modified_at)"
                + " VALUES (?, ?, (SELECT id FROM institutions WHERE built_in), ?, ?,"
                + " CAST(? AS date), now(), now())")) {
      statement.setObject(1, id);
      statement.setString(2, name);
      statement.setString(3, type);
      statement.setBigDecimal(4, openingBalance == null ? null : new BigDecimal(openingBalance));
      statement.setString(5, openingBalance == null ? null : "2026-01-01");
      statement.executeUpdate();
    }
    return id;
  }

  private static UUID insertProduct(Connection connection, UUID accountId) throws SQLException {
    UUID id = UUID.randomUUID();
    try (PreparedStatement statement =
        connection.prepareStatement(
            "INSERT INTO investment_products (id, account_id, investment_category_id, name,"
                + " created_at, last_modified_at) VALUES (?, ?,"
                + " (SELECT id FROM investment_categories WHERE name = 'Crypto'), ?, now(), now())")) {
      statement.setObject(1, id);
      statement.setObject(2, accountId);
      statement.setString(3, "Product " + id);
      statement.executeUpdate();
    }
    return id;
  }

  private static void insertTransfer(
      Connection connection, UUID id, UUID from, UUID to, String amount) throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement(
            "INSERT INTO transfers (id, date, from_account_id, to_account_id, amount,"
                + " description, created_at, last_modified_at)"
                + " VALUES (?, DATE '2026-02-01', ?, ?, ?, 'Old transfer', now(), now())")) {
      statement.setObject(1, id);
      statement.setObject(2, from);
      statement.setObject(3, to);
      statement.setBigDecimal(4, new BigDecimal(amount));
      statement.executeUpdate();
    }
  }

  private static void insertTrade(
      Connection connection,
      UUID from,
      UUID to,
      UUID productId,
      String quantity,
      String unitPrice,
      String taxes)
      throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement(
            "INSERT INTO transfers (id, date, from_account_id, to_account_id, amount,"
                + " description, investment_product_id, quantity, unit_price, taxes, created_at,"
                + " last_modified_at) VALUES (?, DATE '2026-03-01', ?, ?, 10.00, 'Trade', ?, ?, ?,"
                + " ?, now(), now())")) {
      statement.setObject(1, UUID.randomUUID());
      statement.setObject(2, from);
      statement.setObject(3, to);
      statement.setObject(4, productId);
      statement.setBigDecimal(5, quantity == null ? null : new BigDecimal(quantity));
      statement.setBigDecimal(6, unitPrice == null ? null : new BigDecimal(unitPrice));
      statement.setBigDecimal(7, taxes == null ? null : new BigDecimal(taxes));
      statement.executeUpdate();
    }
  }

  private static void insertSnapshot(
      Connection connection, UUID productId, String date, String balance) throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement(
            "INSERT INTO investment_snapshots (id, product_id, date, balance, created_at,"
                + " last_modified_at) VALUES (?, ?, CAST(? AS date), ?, now(), now())")) {
      statement.setObject(1, UUID.randomUUID());
      statement.setObject(2, productId);
      statement.setString(3, date);
      statement.setBigDecimal(4, new BigDecimal(balance));
      statement.executeUpdate();
    }
  }
}
