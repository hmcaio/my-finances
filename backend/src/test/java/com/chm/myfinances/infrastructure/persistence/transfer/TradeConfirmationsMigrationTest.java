package com.chm.myfinances.infrastructure.persistence.transfer;

import static org.assertj.core.api.Assertions.assertThat;

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
 * Proves {@code V21__trade_confirmations.sql} and {@code
 * V22__backfill_trade_confirmation_taxes.sql} (F027, ADR 0024) against pre-existing data: a
 * pre-existing single-product trade ends up with exactly one correctly-signed {@code
 * transfer_trade_lines} row; a pre-existing plain transfer gets none; the four flat trade columns
 * are gone from {@code transfers}; and a pre-existing trade that was recorded with no tax figure at
 * all (legal under the old all-optional {@code InvestmentTradeDetails}) has its now-required {@code
 * taxes} backfilled to {@code 0} by V22, rather than left {@code null} and crashing on load (V21
 * alone reproduced a real {@code NullPointerException}: a non-empty {@code TradeConfirmation} with
 * null taxes violates {@code Transfer}'s invariant the moment the row is reconstituted).
 *
 * <p>Runs Flyway by hand in its own schema of the shared Testcontainers Postgres (see {@link
 * AbstractMigrationTest}).
 */
@Tag("integration")
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class TradeConfirmationsMigrationTest extends AbstractMigrationTest {

  private static final String SCHEMA = "migration_test_trade_confirmations";

  TradeConfirmationsMigrationTest() {
    super(SCHEMA);
  }

  @Test
  void aPreExistingBuyEndsUpWithExactlyOneCorrectlySignedLine() throws Exception {
    flyway("20").migrate();
    UUID checkingId;
    UUID brokerId;
    UUID productId;
    UUID transferId = UUID.randomUUID();
    try (Connection connection = connection()) {
      checkingId = insertAccount(connection, "Checking Trade Migration Test", "CHECKING");
      brokerId = insertAccount(connection, "Broker Trade Migration Test", "INVESTMENT");
      productId = insertProduct(connection, "Product Trade Migration Test");
      insertTransferPreMigration(
          connection, transferId, checkingId, brokerId, productId, "10.5", "95.00");
    }

    flyway("21").migrate();

    try (Connection connection = connection();
        PreparedStatement statement =
            connection.prepareStatement(
                "SELECT product_id, side, quantity, unit_price, resulting_balance, close_holding"
                    + " FROM transfer_trade_lines WHERE transfer_id = ?")) {
      statement.setObject(1, transferId);
      try (ResultSet rows = statement.executeQuery()) {
        assertThat(rows.next()).isTrue();
        assertThat((UUID) rows.getObject(1)).isEqualTo(productId);
        assertThat(rows.getString(2)).isEqualTo("BUY"); // destination (broker) is INVESTMENT
        assertThat(rows.getBigDecimal(3)).isEqualByComparingTo("10.5");
        assertThat(rows.getBigDecimal(4)).isEqualByComparingTo("95.00");
        assertThat(rows.getObject(5)).isNull();
        assertThat(rows.getBoolean(6)).isFalse();
        assertThat(rows.next()).as("exactly one line for this transfer").isFalse();
      }
    }
  }

  @Test
  void aPreExistingSellIsSignedSell() throws Exception {
    flyway("20").migrate();
    UUID checkingId;
    UUID brokerId;
    UUID productId;
    UUID transferId = UUID.randomUUID();
    try (Connection connection = connection()) {
      checkingId = insertAccount(connection, "Checking Sell Migration Test", "CHECKING");
      brokerId = insertAccount(connection, "Broker Sell Migration Test", "INVESTMENT");
      productId = insertProduct(connection, "Product Sell Migration Test");
      // Sell: source (broker) is INVESTMENT, destination (checking) is not.
      insertTransferPreMigration(
          connection, transferId, brokerId, checkingId, productId, "3", "10.00");
    }

    flyway("21").migrate();

    try (Connection connection = connection();
        PreparedStatement statement =
            connection.prepareStatement(
                "SELECT side FROM transfer_trade_lines WHERE transfer_id = ?")) {
      statement.setObject(1, transferId);
      try (ResultSet rows = statement.executeQuery()) {
        assertThat(rows.next()).isTrue();
        assertThat(rows.getString(1)).isEqualTo("SELL");
      }
    }
  }

  @Test
  void aPreExistingPlainTransferGetsNoLines() throws Exception {
    flyway("20").migrate();
    UUID checkingId;
    UUID savingsId;
    UUID transferId = UUID.randomUUID();
    try (Connection connection = connection()) {
      checkingId = insertAccount(connection, "Checking Plain Migration Test", "CHECKING");
      savingsId = insertAccount(connection, "Savings Plain Migration Test", "SAVINGS");
      insertPlainTransferPreMigration(connection, transferId, checkingId, savingsId);
    }

    flyway("21").migrate();

    try (Connection connection = connection()) {
      assertThat(
              count(
                  connection,
                  "SELECT count(*) FROM transfer_trade_lines WHERE transfer_id = '"
                      + transferId
                      + "'"))
          .isZero();
    }
  }

  @Test
  void aPreExistingTradeWithNoRecordedTaxesGetsThemBackfilledToZero() throws Exception {
    flyway("20").migrate();
    UUID checkingId;
    UUID brokerId;
    UUID productId;
    UUID transferId = UUID.randomUUID();
    try (Connection connection = connection()) {
      checkingId = insertAccount(connection, "Checking No-Taxes Migration Test", "CHECKING");
      brokerId = insertAccount(connection, "Broker No-Taxes Migration Test", "INVESTMENT");
      productId = insertProduct(connection, "Product No-Taxes Migration Test");
      // No taxes column given at all - legal under the old all-optional InvestmentTradeDetails.
      insertTransferPreMigration(
          connection, transferId, checkingId, brokerId, productId, "1", "50.00");
    }

    // V21 alone reproduces the bug: a line exists for this transfer, but taxes stays null.
    flyway("21").migrate();
    try (Connection connection = connection();
        PreparedStatement statement =
            connection.prepareStatement("SELECT taxes FROM transfers WHERE id = ?")) {
      statement.setObject(1, transferId);
      try (ResultSet rows = statement.executeQuery()) {
        assertThat(rows.next()).isTrue();
        assertThat(rows.getObject(1)).as("V21 alone leaves taxes null").isNull();
      }
    }

    flyway("22").migrate();
    try (Connection connection = connection();
        PreparedStatement statement =
            connection.prepareStatement("SELECT taxes FROM transfers WHERE id = ?")) {
      statement.setObject(1, transferId);
      try (ResultSet rows = statement.executeQuery()) {
        assertThat(rows.next()).isTrue();
        assertThat(rows.getBigDecimal(1)).isEqualByComparingTo("0");
      }
    }
  }

  @Test
  void transfersNoLongerHasTheFourFlatTradeColumns() throws Exception {
    flyway("21").migrate();

    try (Connection connection = connection();
        PreparedStatement statement =
            connection.prepareStatement(
                "SELECT column_name FROM information_schema.columns"
                    + " WHERE table_schema = ? AND table_name = 'transfers'"
                    + " AND column_name IN ('investment_product_id', 'quantity', 'unit_price')")) {
      statement.setString(1, SCHEMA);
      try (ResultSet rows = statement.executeQuery()) {
        assertThat(rows.next()).as("the four flat trade columns should be gone").isFalse();
      }
    }
    // taxes stays.
    try (PreparedStatement statement =
        connection()
            .prepareStatement(
                "SELECT column_name FROM information_schema.columns"
                    + " WHERE table_schema = ? AND table_name = 'transfers' AND column_name"
                    + " = 'taxes'")) {
      statement.setString(1, SCHEMA);
      try (ResultSet rows = statement.executeQuery()) {
        assertThat(rows.next()).as("taxes should stay").isTrue();
      }
    }
  }

  private static UUID insertAccount(Connection connection, String name, String type)
      throws SQLException {
    UUID id = UUID.randomUUID();
    boolean investment = "INVESTMENT".equals(type);
    try (PreparedStatement statement =
        connection.prepareStatement(
            "INSERT INTO accounts (id, name, institution_id, type, opening_balance,"
                + " opening_balance_date, created_at, last_modified_at)"
                + " VALUES (?, ?, (SELECT id FROM institutions WHERE built_in), ?, ?, ?, now(),"
                + " now())")) {
      statement.setObject(1, id);
      statement.setString(2, name);
      statement.setString(3, type);
      if (investment) {
        statement.setNull(4, java.sql.Types.NUMERIC);
        statement.setNull(5, java.sql.Types.DATE);
      } else {
        statement.setBigDecimal(4, BigDecimal.ZERO);
        statement.setDate(5, java.sql.Date.valueOf("2026-01-01"));
      }
      statement.executeUpdate();
    }
    return id;
  }

  private static UUID insertProduct(Connection connection, String name) throws SQLException {
    UUID id = UUID.randomUUID();
    try (PreparedStatement statement =
        connection.prepareStatement(
            "INSERT INTO investment_products (id, investment_category_id, name, created_at,"
                + " last_modified_at) VALUES (?, (SELECT id FROM investment_categories WHERE name ="
                + " 'Crypto'), ?, now(), now())")) {
      statement.setObject(1, id);
      statement.setString(2, name);
      statement.executeUpdate();
    }
    return id;
  }

  private static void insertTransferPreMigration(
      Connection connection,
      UUID id,
      UUID fromAccountId,
      UUID toAccountId,
      UUID productId,
      String quantity,
      String unitPrice)
      throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement(
            "INSERT INTO transfers (id, date, from_account_id, to_account_id, amount, description,"
                + " investment_product_id, quantity, unit_price, created_at, last_modified_at)"
                + " VALUES (?, CURRENT_DATE, ?, ?, ?, ?, ?, ?, ?, now(), now())")) {
      statement.setObject(1, id);
      statement.setObject(2, fromAccountId);
      statement.setObject(3, toAccountId);
      statement.setBigDecimal(4, new BigDecimal(quantity).multiply(new BigDecimal(unitPrice)));
      statement.setString(5, "Trade Migration Test");
      statement.setObject(6, productId);
      statement.setBigDecimal(7, new BigDecimal(quantity));
      statement.setBigDecimal(8, new BigDecimal(unitPrice));
      statement.executeUpdate();
    }
  }

  private static void insertPlainTransferPreMigration(
      Connection connection, UUID id, UUID fromAccountId, UUID toAccountId) throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement(
            "INSERT INTO transfers (id, date, from_account_id, to_account_id, amount, description,"
                + " created_at, last_modified_at) VALUES (?, CURRENT_DATE, ?, ?, ?, ?, now(),"
                + " now())")) {
      statement.setObject(1, id);
      statement.setObject(2, fromAccountId);
      statement.setObject(3, toAccountId);
      statement.setBigDecimal(4, new BigDecimal("10.00"));
      statement.setString(5, "Plain Migration Test");
      statement.executeUpdate();
    }
  }
}
