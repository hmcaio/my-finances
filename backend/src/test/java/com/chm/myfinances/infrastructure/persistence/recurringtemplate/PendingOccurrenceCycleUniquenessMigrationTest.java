package com.chm.myfinances.infrastructure.persistence.recurringtemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chm.myfinances.TestcontainersConfiguration;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * Proves {@code V11__unique_pending_recurring_occurrence_per_cycle.sql} collapses duplicate pending
 * occurrences that already exist (issue #20) before it adds its {@code UNIQUE (template_id,
 * due_date)}, and that the constraint then holds - the case a fresh-database migration run can't
 * show, since a freshly migrated schema never contains duplicates.
 *
 * <p>Runs Flyway by hand in its own schema of the shared Testcontainers Postgres: migrate to V10,
 * insert the kind of rows the race produced, migrate to V11. The schema is dropped afterwards, and
 * it is separate from the schema the rest of the suite uses, so nothing here can leak into it.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class PendingOccurrenceCycleUniquenessMigrationTest {

  private static final String SCHEMA = "migration_test_pending_cycle";
  private static final String UNIQUE_VIOLATION = "23505";

  @Autowired private DataSource pooledDataSource;

  /**
   * Non-pooled, on purpose: {@code Connection.setSchema} changes the physical connection's {@code
   * search_path}, and on a pooled connection that outlives this test - the next test to borrow it
   * then fails with "relation does not exist" once the schema is dropped.
   */
  private DataSource dataSource;

  @BeforeEach
  void useAnIsolatedDataSource() throws SQLException {
    HikariDataSource pool = pooledDataSource.unwrap(HikariDataSource.class);
    dataSource =
        new DriverManagerDataSource(pool.getJdbcUrl(), pool.getUsername(), pool.getPassword());
  }

  @AfterEach
  void dropSchema() throws SQLException {
    try (Connection connection = dataSource.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
    }
  }

  @Test
  void collapsesExistingDuplicatesKeepingTheEarliestRowThenEnforcesUniqueness() throws Exception {
    flyway("10").migrate();

    UUID templateA = UUID.randomUUID();
    UUID templateB = UUID.randomUUID();
    UUID versionA = UUID.randomUUID();
    UUID versionB = UUID.randomUUID();
    UUID earliest = UUID.randomUUID();
    UUID middle = UUID.randomUUID();
    UUID latest = UUID.randomUUID();
    UUID otherCycleOfA = UUID.randomUUID();
    UUID sameDateOnOtherTemplate = UUID.randomUUID();
    try (Connection connection = connection()) {
      UUID categoryId = UUID.randomUUID();
      UUID accountId = UUID.randomUUID();
      execute(
          connection,
          "INSERT INTO categories (id, name, type, created_at, last_modified_at)"
              + " VALUES ('%s', 'Rent Migration Test', 'EXPENSE', now(), now())",
          categoryId);
      execute(
          connection,
          "INSERT INTO accounts (id, name, type, opening_balance, opening_balance_date,"
              + " created_at, last_modified_at)"
              + " VALUES ('%s', 'Checking Migration Test', 'CHECKING', 0, current_date, now(),"
              + " now())",
          accountId);
      for (UUID template : List.of(templateA, templateB)) {
        execute(
            connection,
            "INSERT INTO recurring_templates (id, category_id, account_id, description, active,"
                + " created_at, last_modified_at)"
                + " VALUES ('%s', '"
                + categoryId
                + "', '"
                + accountId
                + "', 'Rent', true, now(), now())",
            template);
      }
      insertVersion(connection, versionA, templateA);
      insertVersion(connection, versionB, templateB);

      // The same cycle (template A, 2026-03-05) three times, created a minute apart.
      insertPending(connection, earliest, templateA, versionA, "2026-03-05", "3 minutes");
      insertPending(connection, middle, templateA, versionA, "2026-03-05", "2 minutes");
      insertPending(connection, latest, templateA, versionA, "2026-03-05", "1 minute");
      // Must survive: another cycle of A, and the same date on another template.
      insertPending(connection, otherCycleOfA, templateA, versionA, "2026-04-05", "1 minute");
      insertPending(
          connection, sameDateOnOtherTemplate, templateB, versionB, "2026-03-05", "1 minute");
    }

    flyway("11").migrate();

    try (Connection connection = connection()) {
      assertThat(pendingIds(connection))
          .containsExactlyInAnyOrder(earliest, otherCycleOfA, sameDateOnOtherTemplate);

      assertThatThrownBy(
              () ->
                  insertPending(
                      connection,
                      UUID.randomUUID(),
                      templateA,
                      versionA,
                      "2026-04-05",
                      "0 seconds"))
          .isInstanceOfSatisfying(
              SQLException.class, e -> assertThat(e.getSQLState()).isEqualTo(UNIQUE_VIOLATION));
    }
  }

  private Flyway flyway(String targetVersion) {
    return Flyway.configure()
        .dataSource(dataSource)
        .schemas(SCHEMA)
        .createSchemas(true)
        .locations("classpath:db/migration")
        .target(targetVersion)
        .load();
  }

  /** A connection whose default schema is the throwaway one Flyway just migrated. */
  private Connection connection() throws SQLException {
    Connection connection = dataSource.getConnection();
    connection.setSchema(SCHEMA);
    return connection;
  }

  private static void insertVersion(Connection connection, UUID versionId, UUID templateId)
      throws SQLException {
    execute(
        connection,
        "INSERT INTO recurring_template_versions (id, template_id, amount, day_of_month,"
            + " effective_from, created_at, last_modified_at)"
            + " VALUES ('%s', '"
            + templateId
            + "', 10.00, 5, date '2026-01-01', now(), now())",
        versionId);
  }

  private static void insertPending(
      Connection connection,
      UUID id,
      UUID templateId,
      UUID versionId,
      String dueDate,
      String createdAgo)
      throws SQLException {
    execute(
        connection,
        "INSERT INTO pending_recurring_occurrences (id, template_id, template_version_id,"
            + " due_date, created_at, last_modified_at)"
            + " VALUES ('%s', '"
            + templateId
            + "', '"
            + versionId
            + "', date '"
            + dueDate
            + "', now() - interval '"
            + createdAgo
            + "', now())",
        id);
  }

  private static void execute(Connection connection, String sql, UUID id) throws SQLException {
    try (Statement statement = connection.createStatement()) {
      statement.execute(sql.formatted(id));
    }
  }

  private static List<UUID> pendingIds(Connection connection) throws SQLException {
    List<UUID> ids = new ArrayList<>();
    try (PreparedStatement statement =
            connection.prepareStatement("SELECT id FROM pending_recurring_occurrences");
        ResultSet rows = statement.executeQuery()) {
      while (rows.next()) {
        ids.add(rows.getObject("id", UUID.class));
      }
    }
    return ids;
  }
}
