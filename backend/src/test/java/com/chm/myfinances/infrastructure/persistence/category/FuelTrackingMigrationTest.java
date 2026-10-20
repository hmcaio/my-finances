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
 * Proves the constraints {@code V18__fuel_tracking.sql} adds: the partial unique index on the Fuel
 * category, the {@code vehicles} table and the transaction fuel columns with their all-or-none
 * CHECKs. The tests of V18's one-off adopt-or-insert backfill were retired (issue #89, B6). Runs
 * Flyway by hand in its own schema of the shared Testcontainers Postgres (see {@link
 * AbstractMigrationTest}).
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
}
