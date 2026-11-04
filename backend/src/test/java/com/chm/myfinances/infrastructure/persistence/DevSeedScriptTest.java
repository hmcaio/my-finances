package com.chm.myfinances.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chm.myfinances.TestcontainersConfiguration;
import com.chm.myfinances.testsupport.migration.AbstractMigrationTest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Keeps the optional dev sample data ({@code scripts/seed/dev-seed.sql}, loaded by {@code
 * scripts/seed-dev.sh}) in step with the schema: it is not a Flyway migration, so nothing else
 * would notice a later migration breaking it. Migrates a throwaway schema to the latest version
 * (see {@link AbstractMigrationTest}), runs the script and checks it seeds a coherent ledger and
 * refuses to run over existing data.
 */
@Tag("integration")
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class DevSeedScriptTest extends AbstractMigrationTest {

  private static final Path SEED_SCRIPT = Path.of("..", "scripts", "seed", "dev-seed.sql");

  DevSeedScriptTest() {
    super("dev_seed_script_test");
  }

  @Test
  void seedsAFreshlyMigratedDatabaseWithACoherentLedger() throws Exception {
    flyway("latest").migrate();
    try (Connection connection = connection()) {
      runSeed(connection);

      assertThat(count(connection, "SELECT count(*) FROM accounts")).isEqualTo(5);
      assertThat(count(connection, "SELECT count(*) FROM transactions")).isPositive();
      assertThat(count(connection, "SELECT count(*) FROM transfers")).isPositive();
      assertThat(count(connection, "SELECT count(*) FROM budgets")).isEqualTo(4);
      assertThat(count(connection, "SELECT count(*) FROM budget_versions")).isEqualTo(5);
      // Nothing is dated after today.
      assertThat(
              count(
                  connection,
                  "SELECT count(*) FROM (SELECT date FROM transactions UNION ALL"
                      + " SELECT date FROM transfers UNION ALL"
                      + " SELECT date FROM investment_snapshots) d WHERE d.date > current_date"))
          .isZero();
      // Reference rows the migrations create are reused, not duplicated.
      assertThat(count(connection, "SELECT count(*) FROM payment_methods")).isEqualTo(4);
      assertThat(count(connection, "SELECT count(*) FROM categories WHERE built_in")).isEqualTo(2);
      assertThat(count(connection, "SELECT count(*) FROM categories WHERE fuel_category"))
          .isEqualTo(1);
      assertThat(count(connection, "SELECT count(*) FROM categories WHERE dividend_category"))
          .isEqualTo(1);
    }
  }

  @Test
  void seedsInvestmentsFuelAndRecurringTemplatesThatFollowTheDomainRules() throws Exception {
    flyway("latest").migrate();
    try (Connection connection = connection()) {
      runSeed(connection);

      assertThat(count(connection, "SELECT count(*) FROM investment_holdings")).isEqualTo(4);
      assertThat(count(connection, "SELECT count(*) FROM investment_opening_positions"))
          .isEqualTo(1);
      assertThat(count(connection, "SELECT count(*) FROM transfer_trade_lines")).isEqualTo(7);
      assertThat(count(connection, "SELECT count(*) FROM vehicles")).isEqualTo(1);
      assertThat(count(connection, "SELECT count(*) FROM recurring_templates")).isEqualTo(4);
      assertThat(count(connection, "SELECT count(*) FROM recurring_template_versions"))
          .isEqualTo(5);

      // A transfer touches the INVESTMENT account iff it is a trade confirmation (has lines).
      assertThat(
              count(
                  connection,
                  "SELECT count(*) FROM transfers t WHERE"
                      + " EXISTS (SELECT 1 FROM accounts a WHERE a.type = 'INVESTMENT'"
                      + "   AND a.id IN (t.from_account_id, t.to_account_id))"
                      + " <> EXISTS (SELECT 1 FROM transfer_trade_lines l WHERE l.transfer_id = t.id)"))
          .isZero();
      // Every line's resulting balance is the holding's snapshot on the transfer's date.
      assertThat(
              count(
                  connection,
                  "SELECT count(*) FROM transfer_trade_lines l"
                      + " JOIN transfers t ON t.id = l.transfer_id"
                      + " JOIN investment_holdings h ON h.product_id = l.product_id"
                      + " WHERE NOT EXISTS (SELECT 1 FROM investment_snapshots s"
                      + "   WHERE s.holding_id = h.id AND s.date = t.date"
                      + "   AND s.balance = l.resulting_balance)"))
          .isZero();
      // No trade is dated before its holding's opening position.
      assertThat(
              count(
                  connection,
                  "SELECT count(*) FROM transfer_trade_lines l"
                      + " JOIN transfers t ON t.id = l.transfer_id"
                      + " JOIN investment_holdings h ON h.product_id = l.product_id"
                      + " JOIN investment_opening_positions p ON p.holding_id = h.id"
                      + " WHERE t.date < p.as_of_date"))
          .isZero();
      // Dividend transactions sit in the dividend category (and only they carry a holding); fuel
      // fill-ups sit in the fuel category (and only they carry a vehicle).
      assertThat(
              count(
                  connection,
                  "SELECT count(*) FROM transactions t JOIN categories c ON c.id = t.category_id"
                      + " WHERE (t.investment_holding_id IS NOT NULL) <> c.dividend_category"
                      + " OR (t.vehicle_id IS NOT NULL) <> c.fuel_category"))
          .isZero();
      // Past recurring cycles are already transactions, from the version effective that month.
      assertThat(
              count(
                  connection,
                  "SELECT count(*) FROM transactions t"
                      + " JOIN recurring_template_versions v ON v.id = t.recurring_template_version_id"
                      + " WHERE t.amount <> v.amount"))
          .isZero();
      // Card spend before this month is exactly what the statements paid, so only the current
      // month's spend is still owed.
      assertThat(
              count(
                  connection,
                  "SELECT count(*) FROM accounts a WHERE a.type = 'CREDIT_CARD' AND"
                      + " (SELECT coalesce(sum(amount), 0) FROM transactions"
                      + "   WHERE account_id = a.id AND date < date_trunc('month', current_date))"
                      + " <> (SELECT coalesce(sum(amount), 0) FROM transfers"
                      + "   WHERE to_account_id = a.id AND date < date_trunc('month', current_date))"))
          .isZero();
    }
  }

  @Test
  void refusesToRunOverExistingData() throws Exception {
    flyway("latest").migrate();
    try (Connection connection = connection()) {
      runSeed(connection);
      long transactions = count(connection, "SELECT count(*) FROM transactions");

      assertThatThrownBy(() -> runSeed(connection)).hasMessageContaining("dev seed refused");
      // The refusal aborts the script's own BEGIN block; psql ends that when it exits, but this
      // connection lives on, so close the block before querying again.
      try (Statement statement = connection.createStatement()) {
        statement.execute("ROLLBACK");
      }

      assertThat(count(connection, "SELECT count(*) FROM transactions")).isEqualTo(transactions);
      assertThat(count(connection, "SELECT count(*) FROM accounts")).isEqualTo(5);
    }
  }

  private static void runSeed(Connection connection) throws Exception {
    try (Statement statement = connection.createStatement()) {
      statement.execute(Files.readString(SEED_SCRIPT));
    }
  }
}
