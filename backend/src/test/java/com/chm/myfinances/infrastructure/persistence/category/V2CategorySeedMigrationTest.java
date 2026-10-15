package com.chm.myfinances.infrastructure.persistence.category;

import static org.assertj.core.api.Assertions.assertThat;

import com.chm.myfinances.TestcontainersConfiguration;
import com.chm.myfinances.testsupport.migration.AbstractMigrationTest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Proves F035/ADR 0028's edit of {@code V2__categories_and_payment_methods.sql} - dropping the
 * eight regular starter category inserts (Groceries, Rent, Utilities, Subscriptions, Transport,
 * Dining, Health, Salary) while keeping the two rows {@code V14} depends on adopting ("Other"
 * EXPENSE, "Other Income" INCOME).
 *
 * <p>Two things to show: a fresh {@code V2} seeds exactly those two rows and none of the removed
 * eight, and the later adopt-or-insert migrations ({@code V14}'s built-in flag, {@code V18}'s fuel
 * category, {@code V20}'s dividend category) still land on exactly the same four protected rows as
 * before this change, starting from the smaller {@code V2} seed. Runs Flyway by hand in its own
 * schema of the shared Testcontainers Postgres, same setup as {@code
 * BuiltInCategoriesMigrationTest} / {@code FuelTrackingMigrationTest}.
 */
@Tag("integration")
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class V2CategorySeedMigrationTest extends AbstractMigrationTest {

  private static final String SCHEMA = "migration_test_v2_category_seed";

  V2CategorySeedMigrationTest() {
    super(SCHEMA);
  }

  @Test
  void freshV2SeedsExactlyTheTwoRowsV14Adopts() throws Exception {
    flyway("2").migrate();

    try (Connection connection = connection()) {
      assertThat(categoryNamesAndTypes(connection))
          .containsOnly(Map.entry("Other", "EXPENSE"), Map.entry("Other Income", "INCOME"));
    }
  }

  @Test
  void theProtectedSetAfterLaterMigrationsIsStillExactlyTheFourFlaggedRows() throws Exception {
    flyway("20").migrate();

    try (Connection connection = connection()) {
      assertThat(protectedCategoryNamesAndTypes(connection))
          .containsOnly(
              Map.entry("Other Expense", "EXPENSE"),
              Map.entry("Other Income", "INCOME"),
              Map.entry("Fuel", "EXPENSE"),
              Map.entry("Dividends", "INCOME"));
    }
  }

  /** Every row currently in {@code categories}, as name -> type. */
  private static Map<String, String> categoryNamesAndTypes(Connection connection)
      throws SQLException {
    Map<String, String> result = new HashMap<>();
    try (PreparedStatement statement =
            connection.prepareStatement("SELECT name, type FROM categories");
        ResultSet rows = statement.executeQuery()) {
      while (rows.next()) {
        result.put(rows.getString(1), rows.getString(2));
      }
    }
    return result;
  }

  /** Rows flagged {@code built_in}, {@code fuel_category} or {@code dividend_category}. */
  private static Map<String, String> protectedCategoryNamesAndTypes(Connection connection)
      throws SQLException {
    Map<String, String> result = new HashMap<>();
    try (PreparedStatement statement =
            connection.prepareStatement(
                "SELECT name, type FROM categories"
                    + " WHERE built_in OR fuel_category OR dividend_category");
        ResultSet rows = statement.executeQuery()) {
      while (rows.next()) {
        result.put(rows.getString(1), rows.getString(2));
      }
    }
    return result;
  }
}
