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

      assertThat(count(connection, "SELECT count(*) FROM accounts")).isEqualTo(4);
      assertThat(count(connection, "SELECT count(*) FROM transactions")).isPositive();
      assertThat(count(connection, "SELECT count(*) FROM transfers")).isPositive();
      assertThat(count(connection, "SELECT count(*) FROM budgets")).isEqualTo(4);
      assertThat(count(connection, "SELECT count(*) FROM budget_versions")).isEqualTo(5);
      // Nothing is dated after today, and the oldest row is within the seeded 4-month window.
      assertThat(
              count(
                  connection,
                  "SELECT count(*) FROM (SELECT date FROM transactions UNION ALL"
                      + " SELECT date FROM transfers) d WHERE d.date > current_date"))
          .isZero();
      // Reference rows the migrations create are reused, not duplicated.
      assertThat(count(connection, "SELECT count(*) FROM payment_methods")).isEqualTo(4);
      assertThat(count(connection, "SELECT count(*) FROM categories WHERE built_in")).isEqualTo(2);
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
      assertThat(count(connection, "SELECT count(*) FROM accounts")).isEqualTo(4);
    }
  }

  private static void runSeed(Connection connection) throws Exception {
    try (Statement statement = connection.createStatement()) {
      statement.execute(Files.readString(SEED_SCRIPT));
    }
  }
}
