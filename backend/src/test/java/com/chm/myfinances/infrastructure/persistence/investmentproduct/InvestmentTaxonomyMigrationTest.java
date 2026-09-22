package com.chm.myfinances.infrastructure.persistence.investmentproduct;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chm.myfinances.TestcontainersConfiguration;
import com.chm.myfinances.testsupport.migration.AbstractMigrationTest;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Proves {@code V13__investment_accounts_products_taxonomy.sql} (F008) against pre-existing data
 * and at the constraint level: existing accounts keep their values, the {@code accounts} CHECKs
 * accept and reject as specified, the composite foreign key on {@code investment_products} rejects
 * a sub-category from another category and accepts none at all, and the seed holds exactly the
 * Brazilian defaults with no duplicates.
 *
 * <p>Runs Flyway by hand in its own schema of the shared Testcontainers Postgres (same setup as
 * {@code InstitutionBackfillMigrationTest}, including the non-pooled data source). The seed is
 * asserted here, in a fresh schema, rather than against the shared one that other tests write to.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class InvestmentTaxonomyMigrationTest extends AbstractMigrationTest {

  private static final String SCHEMA = "migration_test_investments";

  InvestmentTaxonomyMigrationTest() {
    super(SCHEMA);
  }

  @Test
  void existingAccountsKeepTheirValuesAndTheOpeningColumnsBecomeNullable() throws Exception {
    flyway("12").migrate();
    UUID checkingId = UUID.randomUUID();
    UUID cardId = UUID.randomUUID();
    try (Connection connection = connection()) {
      insertAccount(connection, checkingId, "Checking", "CHECKING", "123.45", "2026-01-15");
      insertAccount(connection, cardId, "Card", "CREDIT_CARD", "0.00", "2026-02-01");
    }

    flyway("13").migrate();

    try (Connection connection = connection()) {
      Map<String, String> byName = new HashMap<>();
      try (PreparedStatement statement =
              connection.prepareStatement(
                  "SELECT name, type, opening_balance, opening_balance_date FROM accounts");
          ResultSet rows = statement.executeQuery()) {
        while (rows.next()) {
          byName.put(
              rows.getString(1),
              rows.getString(2) + "|" + rows.getBigDecimal(3) + "|" + rows.getDate(4));
        }
      }
      assertThat(byName)
          .containsEntry("Checking", "CHECKING|123.45|2026-01-15")
          .containsEntry("Card", "CREDIT_CARD|0.00|2026-02-01")
          .hasSize(2);
      assertThat(isNullable(connection, "accounts", "opening_balance")).isTrue();
      assertThat(isNullable(connection, "accounts", "opening_balance_date")).isTrue();
    }
  }

  @Test
  void theAccountsChecksAcceptAndRejectAsSpecified() throws Exception {
    flyway("13").migrate();
    try (Connection connection = connection()) {
      // Accepted: an INVESTMENT row with neither value, every other type with both.
      insertAccount(connection, UUID.randomUUID(), "Broker", "INVESTMENT", null, null);
      insertAccount(connection, UUID.randomUUID(), "Wallet", "CASH_WALLET", "1.00", "2026-01-01");

      // Rejected: an INVESTMENT row carrying either or both values.
      assertRejected(
          () ->
              insertAccount(
                  connection, UUID.randomUUID(), "Bad 1", "INVESTMENT", "1.00", "2026-01-01"),
          "chk_accounts_opening_values_by_type");
      assertRejected(
          () -> insertAccount(connection, UUID.randomUUID(), "Bad 2", "INVESTMENT", "1.00", null),
          "chk_accounts_opening_values_by_type");
      assertRejected(
          () ->
              insertAccount(
                  connection, UUID.randomUUID(), "Bad 3", "INVESTMENT", null, "2026-01-01"),
          "chk_accounts_opening_values_by_type");

      // Rejected: a non-INVESTMENT row missing either or both values.
      assertRejected(
          () -> insertAccount(connection, UUID.randomUUID(), "Bad 4", "CHECKING", null, null),
          "chk_accounts_opening_values_by_type");
      assertRejected(
          () -> insertAccount(connection, UUID.randomUUID(), "Bad 5", "SAVINGS", "1.00", null),
          "chk_accounts_opening_values_by_type");
      assertRejected(
          () ->
              insertAccount(
                  connection, UUID.randomUUID(), "Bad 6", "CREDIT_CARD", null, "2026-01-01"),
          "chk_accounts_opening_values_by_type");

      // Rejected: a type nobody knows about.
      assertRejected(
          () ->
              insertAccount(
                  connection, UUID.randomUUID(), "Bad 7", "PENSION", "1.00", "2026-01-01"),
          "chk_accounts_type");
    }
  }

  @Test
  void theCompositeForeignKeyRejectsAMismatchedPairAndAcceptsANullSubcategory() throws Exception {
    flyway("13").migrate();
    try (Connection connection = connection()) {
      UUID accountId = UUID.randomUUID();
      insertAccount(connection, accountId, "Broker", "INVESTMENT", null, null);
      UUID fixedIncome = categoryId(connection, "Fixed Income");
      UUID crypto = categoryId(connection, "Crypto");
      UUID cdb = subcategoryId(connection, "Fixed Income", "CDB");

      // Accepted: a matching pair, and a category with no sub-category at all.
      insertProduct(connection, accountId, fixedIncome, cdb, "CDB Product");
      insertProduct(connection, accountId, crypto, null, "Bitcoin");

      // Rejected: the sub-category belongs to Fixed Income, not Crypto.
      assertRejected(
          () -> insertProduct(connection, accountId, crypto, cdb, "Mismatched"),
          "fk_investment_products_subcategory_of_category");
    }
  }

  @Test
  void productNamesAreUniquePerAccountButReusableAcrossAccounts() throws Exception {
    flyway("13").migrate();
    try (Connection connection = connection()) {
      UUID xp = UUID.randomUUID();
      UUID nubank = UUID.randomUUID();
      insertAccount(connection, xp, "XP", "INVESTMENT", null, null);
      insertAccount(connection, nubank, "Nubank", "INVESTMENT", null, null);
      UUID fixedIncome = categoryId(connection, "Fixed Income");

      insertProduct(connection, xp, fixedIncome, null, "Tesouro Selic 2029");
      insertProduct(connection, nubank, fixedIncome, null, "Tesouro Selic 2029");

      assertRejected(
          () -> insertProduct(connection, xp, fixedIncome, null, "Tesouro Selic 2029"),
          "uq_investment_products_account_name");
    }
  }

  @Test
  void subcategoryNamesAreUniquePerCategoryButReusableAcrossCategories() throws Exception {
    flyway("13").migrate();
    try (Connection connection = connection()) {
      // "ETFs" is seeded under both Variable Income and International, so it already coexists.
      assertThat(subcategoryId(connection, "Variable Income", "ETFs"))
          .isNotEqualTo(subcategoryId(connection, "International", "ETFs"));

      assertRejected(
          () -> {
            try (PreparedStatement statement =
                connection.prepareStatement(
                    "INSERT INTO investment_subcategories (id, investment_category_id, name,"
                        + " created_at, last_modified_at) VALUES (?, ?, 'CDB', now(), now())")) {
              statement.setObject(1, UUID.randomUUID());
              statement.setObject(2, categoryId(connection, "Fixed Income"));
              statement.executeUpdate();
            }
          },
          "uq_investment_subcategories_category_name");
    }
  }

  @Test
  void theSeedHoldsTheBrazilianDefaultsWithNoDuplicates() throws Exception {
    flyway("13").migrate();
    try (Connection connection = connection()) {
      Map<String, Set<String>> seeded = new HashMap<>();
      int subcategoryRows = 0;
      try (PreparedStatement statement =
              connection.prepareStatement(
                  "SELECT c.name, s.name FROM investment_categories c"
                      + " LEFT JOIN investment_subcategories s ON s.investment_category_id = c.id");
          ResultSet rows = statement.executeQuery()) {
        while (rows.next()) {
          Set<String> children = seeded.computeIfAbsent(rows.getString(1), k -> new HashSet<>());
          String subcategory = rows.getString(2);
          if (subcategory != null) {
            subcategoryRows++;
            assertThat(children.add(subcategory))
                .as("duplicate sub-category %s under %s", subcategory, rows.getString(1))
                .isTrue();
          }
        }
      }

      assertThat(categoryNames(connection))
          .doesNotHaveDuplicates()
          .containsExactlyInAnyOrder(
              "Fixed Income",
              "Variable Income",
              "Funds",
              "Pension (Previdência)",
              "International",
              "Crypto",
              "Other");
      assertThat(seeded.get("Fixed Income"))
          .containsExactlyInAnyOrder(
              "Tesouro Selic",
              "Tesouro IPCA+",
              "Tesouro Prefixado",
              "CDB",
              "LCI",
              "LCA",
              "CRI",
              "CRA",
              "Debentures",
              "Savings (Poupança)");
      assertThat(seeded.get("Variable Income"))
          .containsExactlyInAnyOrder("Stocks (Ações)", "REITs (FIIs)", "ETFs", "BDRs");
      assertThat(seeded.get("Funds"))
          .containsExactlyInAnyOrder("Fixed Income Funds", "Multimercado", "Equity Funds");
      assertThat(seeded.get("Pension (Previdência)")).containsExactlyInAnyOrder("PGBL", "VGBL");
      assertThat(seeded.get("International")).containsExactlyInAnyOrder("Stocks", "ETFs", "Bonds");
      assertThat(seeded.get("Crypto")).isEmpty();
      assertThat(seeded.get("Other")).isEmpty();
      assertThat(subcategoryRows).isEqualTo(22);
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

  private static void insertAccount(
      Connection connection,
      UUID id,
      String name,
      String type,
      String openingBalance,
      String openingBalanceDate)
      throws SQLException {
    // Built-in institution seeded by V12; V12 is already applied in every scenario above.
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
      statement.setString(5, openingBalanceDate);
      statement.executeUpdate();
    }
  }

  private static void insertProduct(
      Connection connection, UUID accountId, UUID categoryId, UUID subcategoryId, String name)
      throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement(
            "INSERT INTO investment_products (id, account_id, investment_category_id,"
                + " investment_subcategory_id, name, created_at, last_modified_at)"
                + " VALUES (?, ?, ?, ?, ?, now(), now())")) {
      statement.setObject(1, UUID.randomUUID());
      statement.setObject(2, accountId);
      statement.setObject(3, categoryId);
      statement.setObject(4, subcategoryId);
      statement.setString(5, name);
      statement.executeUpdate();
    }
  }

  private static UUID categoryId(Connection connection, String name) throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement("SELECT id FROM investment_categories WHERE name = ?")) {
      statement.setString(1, name);
      try (ResultSet rows = statement.executeQuery()) {
        assertThat(rows.next()).as("seeded category %s", name).isTrue();
        return rows.getObject(1, UUID.class);
      }
    }
  }

  private static UUID subcategoryId(Connection connection, String category, String name)
      throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement(
            "SELECT s.id FROM investment_subcategories s"
                + " JOIN investment_categories c ON c.id = s.investment_category_id"
                + " WHERE c.name = ? AND s.name = ?")) {
      statement.setString(1, category);
      statement.setString(2, name);
      try (ResultSet rows = statement.executeQuery()) {
        assertThat(rows.next()).as("seeded sub-category %s / %s", category, name).isTrue();
        return rows.getObject(1, UUID.class);
      }
    }
  }

  private static List<String> categoryNames(Connection connection) throws SQLException {
    List<String> names = new ArrayList<>();
    try (PreparedStatement statement =
            connection.prepareStatement("SELECT name FROM investment_categories");
        ResultSet rows = statement.executeQuery()) {
      while (rows.next()) {
        names.add(rows.getString(1));
      }
    }
    return names;
  }
}
