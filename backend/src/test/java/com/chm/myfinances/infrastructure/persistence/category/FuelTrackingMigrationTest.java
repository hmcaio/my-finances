package com.chm.myfinances.infrastructure.persistence.category;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chm.myfinances.TestcontainersConfiguration;
import com.chm.myfinances.testsupport.migration.AbstractMigrationTest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Proves {@code V18__fuel_tracking.sql} against pre-existing data (backend {@code CLAUDE.md}'s
 * migration-testing pattern) - the case a fresh-database migration run can't show: the
 * adopt-or-insert "Fuel" category seed, the new transaction columns/CHECKs against existing rows,
 * and the partial unique index. Runs Flyway by hand in its own schema of the shared Testcontainers
 * Postgres, same setup as {@code BuiltInCategoriesMigrationTest}.
 */
@Tag("integration")
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class FuelTrackingMigrationTest extends AbstractMigrationTest {

  private static final String SCHEMA = "migration_test_fuel_tracking";

  FuelTrackingMigrationTest() {
    super(SCHEMA);
  }

  @Test
  void withNoExistingFuelNamedCategoryAFreshOneIsInserted() throws Exception {
    flyway("17").migrate();

    flyway("18").migrate();

    try (Connection connection = connection()) {
      assertThat(count(connection, "SELECT count(*) FROM categories WHERE fuel_category"))
          .isEqualTo(1);
      assertThat(
              count(
                  connection,
                  "SELECT count(*) FROM categories WHERE fuel_category AND name = 'Fuel' AND type = 'EXPENSE'"))
          .isEqualTo(1);
    }
  }

  @Test
  void anExistingExpenseFuelCategoryIsAdoptedInPlace() throws Exception {
    flyway("17").migrate();
    UUID fuelId;
    try (Connection connection = connection()) {
      fuelId = insertCategory(connection, "Fuel", "EXPENSE");
    }

    flyway("18").migrate();

    try (Connection connection = connection()) {
      assertThat(count(connection, "SELECT count(*) FROM categories WHERE fuel_category"))
          .isEqualTo(1);
      assertThat(isFuelCategory(connection, fuelId)).isTrue();
    }
  }

  @Test
  void existingTransactionsAndCategoriesAreUnaffected() throws Exception {
    flyway("17").migrate();
    UUID categoryId;
    UUID accountId;
    UUID paymentMethodId;
    UUID transactionId;
    try (Connection connection = connection()) {
      categoryId = insertCategory(connection, "Groceries Migration Test", "EXPENSE");
      UUID institutionId = insertInstitution(connection, "Test Institution Fuel Migration");
      accountId = insertAccount(connection, "Test Account Fuel Migration", institutionId);
      paymentMethodId = insertPaymentMethod(connection, "Test Payment Method Fuel Migration");
      transactionId = insertTransaction(connection, categoryId, accountId, paymentMethodId);
    }

    flyway("18").migrate();

    try (Connection connection = connection()) {
      assertThat(
              count(
                  connection,
                  "SELECT count(*) FROM transactions WHERE id = '" + transactionId + "'"))
          .isEqualTo(1);
      assertThat(isNullable(connection, "transactions", "vehicle_id")).isTrue();
      try (PreparedStatement statement =
          connection.prepareStatement(
              "SELECT vehicle_id, fuel_type, liters, price_per_liter, km_since_last_fill,"
                  + " odometer FROM transactions WHERE id = ?")) {
        statement.setObject(1, transactionId);
        try (ResultSet rows = statement.executeQuery()) {
          assertThat(rows.next()).isTrue();
          assertThat(rows.getObject(1)).isNull();
          assertThat(rows.getObject(2)).isNull();
          assertThat(rows.getObject(3)).isNull();
          assertThat(rows.getObject(4)).isNull();
          assertThat(rows.getObject(5)).isNull();
          assertThat(rows.getObject(6)).isNull();
        }
      }
      assertThat(
              count(connection, "SELECT count(*) FROM categories WHERE id = '" + categoryId + "'"))
          .isEqualTo(1);
    }
  }

  @Test
  void theSchemaRefusesASecondFuelCategory() throws Exception {
    flyway("18").migrate();

    try (Connection connection = connection()) {
      insertCategory(connection, "Another Fuel Category", "EXPENSE");
      String anotherId;
      try (PreparedStatement statement =
          connection.prepareStatement(
              "SELECT id FROM categories WHERE name = 'Another Fuel Category'")) {
        try (ResultSet rows = statement.executeQuery()) {
          rows.next();
          anotherId = rows.getString(1);
        }
      }
      assertThatThrownBy(
              () ->
                  execute(
                      connection,
                      "UPDATE categories SET fuel_category = true WHERE id = '" + anotherId + "'"))
          .isInstanceOf(SQLException.class)
          .hasMessageContaining("uq_categories_single_fuel_category");
    }
  }

  @Test
  void vehiclesTableAcceptsARowAndTransactionsCanReferenceIt() throws Exception {
    flyway("18").migrate();

    try (Connection connection = connection()) {
      UUID vehicleId = UUID.randomUUID();
      try (PreparedStatement statement =
          connection.prepareStatement(
              "INSERT INTO vehicles (id, name, created_at, last_modified_at)"
                  + " VALUES (?, ?, now(), now())")) {
        statement.setObject(1, vehicleId);
        statement.setString(2, "Civic Migration Test");
        statement.executeUpdate();
      }

      assertThat(count(connection, "SELECT count(*) FROM vehicles WHERE id = '" + vehicleId + "'"))
          .isEqualTo(1);
    }
  }

  @Test
  void checksRejectFuelColumnsThatAreNotAllPresentTogether() throws Exception {
    flyway("18").migrate();

    try (Connection connection = connection()) {
      UUID categoryId = insertCategory(connection, "Groceries Fuel Check Test", "EXPENSE");
      UUID institutionId = insertInstitution(connection, "Test Institution Fuel Check");
      UUID accountId = insertAccount(connection, "Test Account Fuel Check", institutionId);
      UUID paymentMethodId = insertPaymentMethod(connection, "Test PM Fuel Check");
      UUID vehicleId = UUID.randomUUID();
      try (PreparedStatement statement =
          connection.prepareStatement(
              "INSERT INTO vehicles (id, name, created_at, last_modified_at)"
                  + " VALUES (?, 'Fuel Check Vehicle', now(), now())")) {
        statement.setObject(1, vehicleId);
        statement.executeUpdate();
      }

      assertThatThrownBy(
              () ->
                  insertTransactionWithFuelColumns(
                      connection,
                      categoryId,
                      accountId,
                      paymentMethodId,
                      vehicleId,
                      null /* fuelType missing while vehicleId is set */))
          .isInstanceOf(SQLException.class)
          .hasMessageContaining("chk_transactions_vehicle_with_fuel_type");
    }
  }

  private static void execute(Connection connection, String sql) throws SQLException {
    try (Statement statement = connection.createStatement()) {
      statement.executeUpdate(sql);
    }
  }

  private static UUID insertCategory(Connection connection, String name, String type)
      throws SQLException {
    UUID id = UUID.randomUUID();
    try (PreparedStatement statement =
        connection.prepareStatement(
            "INSERT INTO categories (id, name, type, created_at, last_modified_at)"
                + " VALUES (?, ?, ?, now(), now())")) {
      statement.setObject(1, id);
      statement.setString(2, name);
      statement.setString(3, type);
      statement.executeUpdate();
    }
    return id;
  }

  private static UUID insertInstitution(Connection connection, String name) throws SQLException {
    UUID id = UUID.randomUUID();
    try (PreparedStatement statement =
        connection.prepareStatement(
            "INSERT INTO institutions (id, name, built_in, created_at, last_modified_at)"
                + " VALUES (?, ?, false, now(), now())")) {
      statement.setObject(1, id);
      statement.setString(2, name);
      statement.executeUpdate();
    }
    return id;
  }

  private static UUID insertAccount(Connection connection, String name, UUID institutionId)
      throws SQLException {
    UUID id = UUID.randomUUID();
    try (PreparedStatement statement =
        connection.prepareStatement(
            "INSERT INTO accounts (id, name, type, institution_id, opening_balance,"
                + " opening_balance_date, created_at, last_modified_at)"
                + " VALUES (?, ?, 'CHECKING', ?, 0, CURRENT_DATE, now(), now())")) {
      statement.setObject(1, id);
      statement.setString(2, name);
      statement.setObject(3, institutionId);
      statement.executeUpdate();
    }
    return id;
  }

  private static UUID insertPaymentMethod(Connection connection, String name) throws SQLException {
    UUID id = UUID.randomUUID();
    try (PreparedStatement statement =
        connection.prepareStatement(
            "INSERT INTO payment_methods (id, name, created_at, last_modified_at)"
                + " VALUES (?, ?, now(), now())")) {
      statement.setObject(1, id);
      statement.setString(2, name);
      statement.executeUpdate();
    }
    return id;
  }

  private static UUID insertTransaction(
      Connection connection, UUID categoryId, UUID accountId, UUID paymentMethodId)
      throws SQLException {
    UUID id = UUID.randomUUID();
    try (PreparedStatement statement =
        connection.prepareStatement(
            "INSERT INTO transactions (id, date, amount, category_id, type, account_id,"
                + " payment_method_id, description, created_at, last_modified_at)"
                + " VALUES (?, CURRENT_DATE, 10.00, ?, 'EXPENSE', ?, ?, 'Migration test', now(),"
                + " now())")) {
      statement.setObject(1, id);
      statement.setObject(2, categoryId);
      statement.setObject(3, accountId);
      statement.setObject(4, paymentMethodId);
      statement.executeUpdate();
    }
    return id;
  }

  private static void insertTransactionWithFuelColumns(
      Connection connection,
      UUID categoryId,
      UUID accountId,
      UUID paymentMethodId,
      UUID vehicleId,
      String fuelType)
      throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement(
            "INSERT INTO transactions (id, date, amount, category_id, type, account_id,"
                + " payment_method_id, description, vehicle_id, fuel_type, created_at,"
                + " last_modified_at)"
                + " VALUES (?, CURRENT_DATE, 10.00, ?, 'EXPENSE', ?, ?, 'Migration test', ?, ?,"
                + " now(), now())")) {
      statement.setObject(1, UUID.randomUUID());
      statement.setObject(2, categoryId);
      statement.setObject(3, accountId);
      statement.setObject(4, paymentMethodId);
      statement.setObject(5, vehicleId);
      statement.setString(6, fuelType);
      statement.executeUpdate();
    }
  }

  private static boolean isFuelCategory(Connection connection, UUID id) throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement("SELECT fuel_category FROM categories WHERE id = ?")) {
      statement.setObject(1, id);
      try (ResultSet rows = statement.executeQuery()) {
        assertThat(rows.next()).isTrue();
        return rows.getBoolean(1);
      }
    }
  }
}
