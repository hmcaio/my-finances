package com.chm.myfinances.infrastructure.persistence.investmentholding;

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
 * Proves {@code V17__investment_holdings.sql} (F022, ADR 0020) against pre-existing data: a product
 * and its snapshot, inserted before the migration under the old 1:1 {@code
 * investment_products.account_id} model, end up with exactly one {@code investment_holdings} row
 * carrying the product's old {@code account_id}/{@code closed_date}, and the snapshot's {@code
 * holding_id} points at it. Also proves the new constraints at the DB level: {@code
 * investment_holdings} unique on {@code (product_id, account_id)}, {@code investment_snapshots}
 * unique on {@code (holding_id, date)}, and {@code investment_products.name} globally unique.
 *
 * <p>Runs Flyway by hand in its own schema of the shared Testcontainers Postgres (see {@link
 * AbstractMigrationTest}).
 */
@Tag("integration")
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class InvestmentHoldingsMigrationTest extends AbstractMigrationTest {

  private static final String SCHEMA = "migration_test_holdings";

  InvestmentHoldingsMigrationTest() {
    super(SCHEMA);
  }

  @Test
  void preExistingProductAndSnapshotEndUpWithExactlyOneHoldingCarryingItsAccountAndClosedDate()
      throws Exception {
    flyway("16").migrate();
    UUID accountId;
    UUID productId;
    UUID snapshotId = UUID.randomUUID();
    try (Connection connection = connection()) {
      accountId = insertAccount(connection, "Broker Holdings Migration Test");
      productId = insertProductPreMigration(connection, accountId, "2026-06-15");
      insertSnapshotByProduct(connection, snapshotId, productId, "2026-03-31", "1234.56");
    }

    flyway("17").migrate();

    try (Connection connection = connection()) {
      // Exactly one holding for this product, carrying the old account_id/closed_date.
      try (PreparedStatement statement =
          connection.prepareStatement(
              "SELECT id, account_id, closed_date FROM investment_holdings WHERE product_id = ?")) {
        statement.setObject(1, productId);
        try (ResultSet rows = statement.executeQuery()) {
          assertThat(rows.next()).isTrue();
          UUID holdingId = (UUID) rows.getObject(1);
          assertThat((UUID) rows.getObject(2)).isEqualTo(accountId);
          assertThat(rows.getDate(3).toLocalDate().toString()).isEqualTo("2026-06-15");
          assertThat(rows.next()).as("exactly one holding for this product").isFalse();

          // The snapshot's holding_id points at that same holding.
          try (PreparedStatement snapshotStatement =
              connection.prepareStatement(
                  "SELECT holding_id FROM investment_snapshots WHERE id = ?")) {
            snapshotStatement.setObject(1, snapshotId);
            try (ResultSet snapshotRows = snapshotStatement.executeQuery()) {
              assertThat(snapshotRows.next()).isTrue();
              assertThat((UUID) snapshotRows.getObject(1)).isEqualTo(holdingId);
            }
          }
        }
      }
    }
    assertThat(isNullable(connection(), "investment_snapshots", "holding_id")).isFalse();
  }

  @Test
  void investmentProductsNoLongerHasAccountIdOrClosedDate() throws Exception {
    flyway("17").migrate();

    try (Connection connection = connection();
        PreparedStatement statement =
            connection.prepareStatement(
                "SELECT column_name FROM information_schema.columns"
                    + " WHERE table_schema = ? AND table_name = 'investment_products'"
                    + " AND column_name IN ('account_id', 'closed_date')")) {
      statement.setString(1, SCHEMA);
      try (ResultSet rows = statement.executeQuery()) {
        assertThat(rows.next()).as("account_id/closed_date should be dropped").isFalse();
      }
    }
  }

  @Test
  void investmentHoldingsUniqueProductAccountPairIsEnforced() throws Exception {
    flyway("17").migrate();
    try (Connection connection = connection()) {
      UUID accountId = insertAccount(connection, "Broker Holdings Uniqueness Test");
      UUID productId = insertProduct(connection, "Product Holdings Uniqueness Test");
      insertHolding(connection, productId, accountId);

      assertRejected(
          () -> insertHolding(connection, productId, accountId),
          "uq_investment_holdings_product_account");
    }
  }

  @Test
  void investmentSnapshotsUniqueHoldingDateIsEnforced() throws Exception {
    flyway("17").migrate();
    try (Connection connection = connection()) {
      UUID accountId = insertAccount(connection, "Broker Snapshot Uniqueness Test");
      UUID productId = insertProduct(connection, "Product Snapshot Uniqueness Test");
      UUID holdingId = insertHolding(connection, productId, accountId);

      insertSnapshot(connection, UUID.randomUUID(), holdingId, "2026-05-31", "10.00");
      // A different date for the same holding, and the same date for a different holding, are fine.
      insertSnapshot(connection, UUID.randomUUID(), holdingId, "2026-06-30", "20.00");
      UUID otherProduct = insertProduct(connection, "Product Snapshot Uniqueness Test 2");
      UUID otherHolding = insertHolding(connection, otherProduct, accountId);
      insertSnapshot(connection, UUID.randomUUID(), otherHolding, "2026-05-31", "5.00");

      assertRejected(
          () -> insertSnapshot(connection, UUID.randomUUID(), holdingId, "2026-05-31", "99.00"),
          "uq_investment_snapshots_holding_date");
    }
  }

  @Test
  void investmentProductNameIsNowGloballyUnique() throws Exception {
    flyway("17").migrate();
    try (Connection connection = connection()) {
      insertProduct(connection, "Tesouro Selic Global Test 2029");

      assertRejected(
          () -> insertProduct(connection, "Tesouro Selic Global Test 2029"),
          "uq_investment_products_name");
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

  private static UUID insertAccount(Connection connection, String name) throws SQLException {
    UUID id = UUID.randomUUID();
    try (PreparedStatement statement =
        connection.prepareStatement(
            "INSERT INTO accounts (id, name, institution_id, type, opening_balance,"
                + " opening_balance_date, created_at, last_modified_at)"
                + " VALUES (?, ?, (SELECT id FROM institutions WHERE built_in), 'INVESTMENT', NULL,"
                + " NULL, now(), now())")) {
      statement.setObject(1, id);
      statement.setString(2, name);
      statement.executeUpdate();
    }
    return id;
  }

  /**
   * Pre-migration insert (V16 schema): {@code investment_products} still has {@code account_id}.
   */
  private static UUID insertProductPreMigration(
      Connection connection, UUID accountId, String closedDate) throws SQLException {
    UUID id = UUID.randomUUID();
    try (PreparedStatement statement =
        connection.prepareStatement(
            "INSERT INTO investment_products (id, account_id, investment_category_id, name,"
                + " closed_date, created_at, last_modified_at) VALUES (?, ?,"
                + " (SELECT id FROM investment_categories WHERE name = 'Crypto'), ?,"
                + " CAST(? AS date), now(), now())")) {
      statement.setObject(1, id);
      statement.setObject(2, accountId);
      statement.setString(3, "Product " + id);
      statement.setString(4, closedDate);
      statement.executeUpdate();
    }
    return id;
  }

  /** Post-migration insert (V17 schema): {@code investment_products} is pure taxonomy. */
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

  private static UUID insertHolding(Connection connection, UUID productId, UUID accountId)
      throws SQLException {
    UUID id = UUID.randomUUID();
    try (PreparedStatement statement =
        connection.prepareStatement(
            "INSERT INTO investment_holdings (id, product_id, account_id, created_at,"
                + " last_modified_at) VALUES (?, ?, ?, now(), now())")) {
      statement.setObject(1, id);
      statement.setObject(2, productId);
      statement.setObject(3, accountId);
      statement.executeUpdate();
    }
    return id;
  }

  /**
   * Pre-migration insert (V16 schema): {@code investment_snapshots} still has {@code product_id}.
   */
  private static void insertSnapshotByProduct(
      Connection connection, UUID id, UUID productId, String date, String balance)
      throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement(
            "INSERT INTO investment_snapshots (id, product_id, date, balance, created_at,"
                + " last_modified_at) VALUES (?, ?, CAST(? AS date), ?, now(), now())")) {
      statement.setObject(1, id);
      statement.setObject(2, productId);
      statement.setString(3, date);
      statement.setBigDecimal(4, new BigDecimal(balance));
      statement.executeUpdate();
    }
  }

  /**
   * Post-migration insert (V17 schema): {@code investment_snapshots} now has {@code holding_id}.
   */
  private static void insertSnapshot(
      Connection connection, UUID id, UUID holdingId, String date, String balance)
      throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement(
            "INSERT INTO investment_snapshots (id, holding_id, date, balance, created_at,"
                + " last_modified_at) VALUES (?, ?, CAST(? AS date), ?, now(), now())")) {
      statement.setObject(1, id);
      statement.setObject(2, holdingId);
      statement.setString(3, date);
      statement.setBigDecimal(4, new BigDecimal(balance));
      statement.executeUpdate();
    }
  }
}
