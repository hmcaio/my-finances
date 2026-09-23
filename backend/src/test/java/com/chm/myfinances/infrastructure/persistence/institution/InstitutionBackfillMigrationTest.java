package com.chm.myfinances.infrastructure.persistence.institution;

import static org.assertj.core.api.Assertions.assertThat;

import com.chm.myfinances.TestcontainersConfiguration;
import com.chm.myfinances.testsupport.migration.AbstractMigrationTest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Proves {@code V12__institutions.sql} converts the free-text {@code accounts.institution} that
 * already exists in a database into {@code institutions} rows and a required {@code
 * accounts.institution_id} (F017) - the case a fresh-database migration run can't show, since a
 * freshly migrated schema has no accounts.
 *
 * <p>Runs Flyway by hand in its own schema of the shared Testcontainers Postgres: migrate to V11,
 * insert accounts carrying the kinds of values users typed, migrate to V12. The schema is dropped
 * afterwards and is separate from the one the rest of the suite uses (same setup as {@code
 * PendingOccurrenceCycleUniquenessMigrationTest}).
 */
@Tag("integration")
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class InstitutionBackfillMigrationTest extends AbstractMigrationTest {

  private static final String SCHEMA = "migration_test_institutions";
  private static final String BUILT_IN_NAME = "No institution";
  private static final String ITAU_WITH_ACCENT = "Itaú";

  InstitutionBackfillMigrationTest() {
    super(SCHEMA);
  }

  @Test
  void convertsFreeTextInstitutionsAndAssignsTheRestToTheBuiltInRow() throws Exception {
    flyway("11").migrate();
    try (Connection connection = connection()) {
      insertAccount(connection, "Null Institution", null);
      insertAccount(connection, "Empty Institution", "");
      insertAccount(connection, "Blank Institution", "  ");
      insertAccount(connection, "Literal Fallback", "No institution");
      insertAccount(connection, "Shouty Fallback", "no INSTITUTION");
      insertAccount(connection, "Nubank Exact", "Nubank");
      insertAccount(connection, "Nubank Padded", " Nubank ");
      insertAccount(connection, "Nubank Lower", "nubank");
      insertAccount(connection, "Itau Accented", ITAU_WITH_ACCENT);
    }

    flyway("12").migrate();

    try (Connection connection = connection()) {
      // Built-in row + one row per distinct institution, and nothing else: the four "no
      // institution" spellings did not create rows, and the three Nubank spellings became one.
      assertThat(institutionNames(connection))
          .containsExactlyInAnyOrder(BUILT_IN_NAME, "Nubank", ITAU_WITH_ACCENT);
      assertThat(builtInNames(connection)).containsExactly(BUILT_IN_NAME);

      Map<String, String> institutionByAccount = institutionNameByAccount(connection);
      assertThat(institutionByAccount)
          .containsEntry("Null Institution", BUILT_IN_NAME)
          .containsEntry("Empty Institution", BUILT_IN_NAME)
          .containsEntry("Blank Institution", BUILT_IN_NAME)
          .containsEntry("Literal Fallback", BUILT_IN_NAME)
          .containsEntry("Shouty Fallback", BUILT_IN_NAME)
          .containsEntry("Nubank Exact", "Nubank")
          .containsEntry("Nubank Padded", "Nubank")
          .containsEntry("Nubank Lower", "Nubank")
          .containsEntry("Itau Accented", ITAU_WITH_ACCENT);
      assertThat(institutionByAccount).hasSize(9);

      assertThat(isNullable(connection, "accounts", "institution_id")).isFalse();
      assertThat(columnExists(connection, "accounts", "institution")).isFalse();
    }
  }

  @Test
  void doesNotMergeAccentVariantsAndTrimsAllKindsOfWhitespace() throws Exception {
    flyway("11").migrate();
    try (Connection connection = connection()) {
      insertAccount(connection, "Accented", ITAU_WITH_ACCENT);
      insertAccount(connection, "Unaccented", "Itau");
      insertAccount(connection, "Tab Padded", "\tInter\n");
      insertAccount(connection, "Plain", "Inter");
    }

    flyway("12").migrate();

    try (Connection connection = connection()) {
      assertThat(institutionNames(connection))
          .containsExactlyInAnyOrder(BUILT_IN_NAME, ITAU_WITH_ACCENT, "Itau", "Inter");
      Map<String, String> institutionByAccount = institutionNameByAccount(connection);
      assertThat(institutionByAccount)
          .containsEntry("Accented", ITAU_WITH_ACCENT)
          .containsEntry("Unaccented", "Itau")
          .containsEntry("Tab Padded", "Inter")
          .containsEntry("Plain", "Inter");
    }
  }

  @Test
  void aDatabaseWithNoAccountsGetsJustTheBuiltInRow() throws Exception {
    flyway("12").migrate();

    try (Connection connection = connection()) {
      assertThat(institutionNames(connection)).containsExactly(BUILT_IN_NAME);
      assertThat(builtInNames(connection)).containsExactly(BUILT_IN_NAME);
    }
  }

  private static void insertAccount(Connection connection, String name, String institution)
      throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement(
            "INSERT INTO accounts (id, name, institution, type, opening_balance,"
                + " opening_balance_date, created_at, last_modified_at)"
                + " VALUES (?, ?, ?, 'CHECKING', 0, current_date, now(), now())")) {
      statement.setObject(1, UUID.randomUUID());
      statement.setString(2, name);
      statement.setString(3, institution);
      statement.executeUpdate();
    }
  }

  private static Set<String> institutionNames(Connection connection) throws SQLException {
    return namesFrom(connection, "SELECT name FROM institutions");
  }

  private static Set<String> builtInNames(Connection connection) throws SQLException {
    return namesFrom(connection, "SELECT name FROM institutions WHERE built_in");
  }

  private static Map<String, String> institutionNameByAccount(Connection connection)
      throws SQLException {
    Map<String, String> result = new HashMap<>();
    try (PreparedStatement statement =
            connection.prepareStatement(
                "SELECT a.name, i.name FROM accounts a"
                    + " JOIN institutions i ON i.id = a.institution_id");
        ResultSet rows = statement.executeQuery()) {
      while (rows.next()) {
        result.put(rows.getString(1), rows.getString(2));
      }
    }
    return result;
  }

  private static boolean columnExists(Connection connection, String table, String column)
      throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement(
            "SELECT 1 FROM information_schema.columns"
                + " WHERE table_schema = ? AND table_name = ? AND column_name = ?")) {
      statement.setString(1, SCHEMA);
      statement.setString(2, table);
      statement.setString(3, column);
      try (ResultSet rows = statement.executeQuery()) {
        return rows.next();
      }
    }
  }
}
