package com.chm.myfinances.infrastructure.persistence.transfer;

import static org.assertj.core.api.Assertions.assertThat;

import com.chm.myfinances.TestcontainersConfiguration;
import com.chm.myfinances.testsupport.migration.AbstractMigrationTest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Proves the schema {@code V21__trade_confirmations.sql} (F027, ADR 0024) leaves behind: the four
 * flat trade columns are gone from {@code transfers} and {@code taxes} stays. The tests of V21's
 * and {@code V22__backfill_trade_confirmation_taxes.sql}'s one-off backfills of pre-existing trades
 * were retired (issue #89, B6).
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
}
